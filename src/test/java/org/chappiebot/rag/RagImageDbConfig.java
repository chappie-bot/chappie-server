package org.chappiebot.rag;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target(TYPE)
public @interface RagImageDbConfig {
    String image() default "ghcr.io/quarkusio/chappie-ingestion-quarkus:3.30.6";
    int dim() default 384;
    String datasourceName() default ""; // "" = default datasource
}

