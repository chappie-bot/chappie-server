package org.chappiebot.doc;

import java.util.Optional;
import org.chappiebot.CommonInput;

public record DocInput(CommonInput commonInput,
                        Optional<String> extraContext,
                        String doc,
                        String source){
}
