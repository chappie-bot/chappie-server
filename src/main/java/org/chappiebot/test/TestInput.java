package org.chappiebot.test;

import java.util.Optional;
import org.chappiebot.CommonInput;

public record TestInput(CommonInput commonInput,
                        Optional<String> extraContext,
                        String source){
}
