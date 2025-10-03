package org.chappiebot;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import jakarta.xml.bind.ValidationException;
import java.util.Map;
import org.jboss.logging.Logger;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {
    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);
    public static final String ERROR_HEADER = "X-Error-Message";
    private static final int MAX_HEADER_LENGTH = 200;
    
    @Override
    public Response toResponse(Throwable exception) {
        
        int status = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
        if(exception instanceof WebApplicationException){
            status = ((WebApplicationException)exception).getResponse().getStatus();
        }else if(exception instanceof ValidationException) {
            status = Response.Status.BAD_REQUEST.getStatusCode();
        }
        
        LOG.errorf(exception, "Unhandled exception (status=%s)", status);

        String raw = exception.getMessage();
        String headerValue = sanitizeHeaderValue(raw);

        if (headerValue.isBlank()) {
            headerValue = switch (status) {
                case 400 -> "Bad request";
                case 404 -> "Not found";
                default -> "Internal server error";
            };
        }

        return Response.status(status)
                .header(ERROR_HEADER, headerValue)
                .entity(Map.of("status", status, "message",headerValue))
                .build();
    }

    private static String sanitizeHeaderValue(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\r\\n]+", " ").trim();
        if (cleaned.length() > MAX_HEADER_LENGTH) {
            return cleaned.substring(0, MAX_HEADER_LENGTH - 3) + "...";
        }
        return cleaned;
    }
}