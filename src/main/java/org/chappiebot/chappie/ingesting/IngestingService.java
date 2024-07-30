package org.chappiebot.chappie.ingesting;

import io.quarkus.logging.Log;
import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.Blocking;
import io.vertx.core.json.JsonArray;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import org.chappiebot.chappie.document.DocumentFetcher;
import org.chappiebot.chappie.document.DocumentFetcherException;
import org.chappiebot.chappie.document.DocumentLocation;
import org.chappiebot.chappie.product.DocumentSet;
import org.chappiebot.chappie.product.Product;
import org.chappiebot.chappie.product.ProductService;
import org.chappiebot.chappie.summary.Summary;
import org.chappiebot.chappie.summary.SummaryException;

/**
 * Queue things to be ingested
 * TODO: Move to store in a DB
 *
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@ApplicationScoped
public class IngestingService {

    @Inject
    Map<String,Ingestor> ingestorsMap;
    
    @Inject
    Map<String,DocumentFetcher> documentFetchersMap;
    
    @Inject
    ProductService productService;
    
    private final Map<ProductQueueKey,Status> statuses = new HashMap<>(); 
    private final Queue<ProductQueueKey> productQueue = new LinkedList<>();
    private final Queue<DocumentLocation> documentsQueue = new LinkedList<>();
    private final Queue<DocumentLocation> errorQueue = new LinkedList<>();
    private final Queue<DocumentLocation> failedQueue = new LinkedList<>();
    private Product currentProduct = null;
    private String currentVersion = null;
    private DocumentLocation currentDocumentLocation = null;
    private int currentDocumentsTotalSize = -1;
    private Instant currentStartTime = null;
    private JsonArray summaryArray = null;
    
    public Status getStatus(String product, String version) {
        ProductQueueKey key = new ProductQueueKey(product, version);
        return getStatus(key);
    }
    
    public Status getStatus(ProductQueueKey key) {
        return statuses.get(key);
    }
    
    public boolean isQueued(String product, String version) {
        ProductQueueKey key = new ProductQueueKey(product, version);
        return isQueued(key);
    }

    public boolean isQueued(ProductQueueKey key){
        return productQueue.contains(key);
    }
    
    public boolean queue(String product, String version){
        ProductQueueKey key = new ProductQueueKey(product, version);
        if(isQueued(key))return false;
        productQueue.add(key);
        return true;
    }
    
    public Status getCurrentStatus(){
        Product p = getCurrentProduct();
        if(p!=null){
            return new Status(RunStage.IN_PROGRESS, p,getCurrentVersion(), getCurrentDocumentLocation(),getPersentageIngested(),noOfDocumentsIngested(), getCurrentTimeSinceStarted(),new ArrayList<>(failedQueue));
        }
        return null;
    }
    
    public Product getCurrentProduct(){
        return this.currentProduct;
    }
    
    public String getCurrentVersion(){
        return this.currentVersion;
    }
    
    public DocumentLocation getCurrentDocumentLocation(){
        return this.currentDocumentLocation;
    }
    
    public int getCurrentDocumentsTotalSize(){
        return this.currentDocumentsTotalSize;
    }
    
    public double getPersentageIngested(){
        if(this.currentDocumentsTotalSize==-1){
            return 0; // Not started yet
        }else{
            return calculatePercentage(noOfDocumentsIngested(),this.currentDocumentsTotalSize);
        }
    }
    
    public int noOfDocumentsIngested(){
        return this.currentDocumentsTotalSize-this.documentsQueue.size();
    }
    
    public Duration getCurrentTimeSinceStarted(){
        if(currentStartTime!=null){
            Instant end = Instant.now();
            return Duration.between(currentStartTime, end);
        }
        return null;
    }
    
    @Scheduled(every = "30s", concurrentExecution = SKIP)
    @Blocking
    void processNextProduct() {
        if(!productQueue.isEmpty()){
            // Check if the documents info is already loaded
            if(documentsQueue.isEmpty()){
                
                this.summaryArray = JsonArray.of(); // We need to collect all summaries
                // Get the product in the front of the queue
                ProductQueueKey key = productQueue.peek();
                Log.infof("Loading all documents for %s %s",key.product,key.version);
                this.currentProduct = productService.getProduct(key.product);
                this.currentVersion = key.version;
                // Load documents to process
                DocumentSet documentSet = currentProduct.documentSet; // TODO: Support multiple
                String documentLoaderName = documentSet.documentLoaderName;
                DocumentFetcher documentFetcher = documentFetchersMap.get(documentLoaderName);

                String path = "quarkusio/quarkus/docs/src/main/asciidoc"; // TODO: Move to DocumentSet
                try {
                    List<DocumentLocation> documentLocations = documentFetcher.findDocumentLocations(path, key.version,
                                                                                            List.of("adoc") ,// TODO: Move to DocumentSet
                                                                                            Optional.of(currentProduct.repoAuthToken)); // TODO: Move to DocumentSet
                    // TODO: Handle if no documents found
                    this.currentDocumentsTotalSize = documentLocations.size();
                    Log.info("\t found " + documentLocations.size() + " documents");
                    this.documentsQueue.addAll(documentLocations);
                    this.currentStartTime = Instant.now();
                } catch (DocumentFetcherException ex) {
                    // TODO: Handle
                    ex.printStackTrace();
                }
            }
        }
    }

    @Scheduled(every = "15s", concurrentExecution = SKIP)
    @Blocking
    void processNextDocument() {
        if(!documentsQueue.isEmpty()){
            // Get the product in the front of the queue
            ProductQueueKey key = productQueue.peek();
            currentProduct = productService.getProduct(key.product);
            currentVersion = key.version;
            DocumentSet documentSet = currentProduct.documentSet;
            
            // Get the product in the front of the queue
            this.currentDocumentLocation = documentsQueue.peek();
            
            String ingestorName = documentSet.ingestorName;
            Ingestor ingestor = ingestorsMap.get(ingestorName);
            try {
                Summary summary = ingestor.ingestDocument(key.product, key.version, this.currentDocumentLocation);
                this.summaryArray.add(summary);
                documentsQueue.poll(); // Remove that document that has now been process
            }catch(IngestingException | SummaryException ie){
                if (ie.getCause() != null && ie.getCause().getMessage().contains("insufficient_quota")) {
                    // Stop processing the queue as hitting the limits or no $$$ available
                    productQueue.clear();
                    documentsQueue.clear();
                    errorQueue.clear();
                    Log.warn("Stopping queue processing as insufficient_quota error was returned from the AI service");
                } else {
                    // Something went wrong. Move it to the back of the queue
                    ie.printStackTrace();
                    Log.warnf("Problem ingesting product [%s] version [%s] document [%s], moving it to the error queue to retry later", key.product, key.version, this.currentDocumentLocation.name());
                    DocumentLocation b = documentsQueue.poll();
                    errorQueue.add(b);
                }
            }
            if(documentsQueue.isEmpty()){
                // All document for a certain product/version has been processed
                this.productIngestionDone(ingestor, key);
            }
        }
    }
    
    private void productIngestionDone(Ingestor ingestor, ProductQueueKey key){
        if(this.errorQueue.isEmpty()){
            ingestor.ingestSummaries(key.product, key.version, summaryArray);
            statuses.put(key, getCurrentStatus().markAsDone());
            this.documentsQueue.clear();
            this.errorQueue.clear();
            this.failedQueue.clear();
            this.summaryArray = null;
            this.currentProduct = null;
            this.currentVersion = null;
            this.currentDocumentLocation = null;
            this.currentDocumentsTotalSize = -1;
            this.currentStartTime = null;
            productQueue.poll();
        }else{
            try {
                this.currentDocumentLocation = errorQueue.peek();
                Summary summary = ingestor.ingestDocument(key.product, key.version, this.currentDocumentLocation);
                this.summaryArray.add(summary);
                errorQueue.poll(); // Remove that document that has now been process
                if(errorQueue.isEmpty()){
                    // All document for a certain product/version has been processed
                    this.productIngestionDone(ingestor, key);
                }
            }catch(IngestingException | SummaryException ie){
                // Something went wrong again
                Log.warnf("Problem ingesting product [%s] version [%s] document [%s]", key.product, key.version, this.currentDocumentLocation.name());
                Log.warn(ie.getMessage());
                DocumentLocation b = errorQueue.poll();
                failedQueue.add(b);
            }
        }
    }
    
    private double calculatePercentage(int done, int total) {
        if (total == 0) {
            return 0;
        }
        return ((double) done / total) * 100;
    }

    class ProductQueueKey {

        String product;
        String version;

        public ProductQueueKey(String product, String version) {
            this.product = product;
            this.version = version;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 61 * hash + Objects.hashCode(this.product);
            hash = 61 * hash + Objects.hashCode(this.version);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ProductQueueKey other = (ProductQueueKey) obj;
            if (!Objects.equals(this.product, other.product)) {
                return false;
            }
            return Objects.equals(this.version, other.version);
        }
    }
}
