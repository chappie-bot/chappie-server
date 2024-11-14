package org.chappiebot.explain;

import java.util.Optional;
import org.chappiebot.CommonInput;

public record ExplainInput(CommonInput commonInput,
                        Optional<String> extraContext,
                        String source){
}
