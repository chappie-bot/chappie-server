package org.chappiebot.exception;

import org.chappiebot.CommonInput;
import java.util.Optional;

public record ExceptionInput(CommonInput commonInput,
                        Optional<String> extraContext,
                        String stacktrace,
                        String source){
}
