package org.chappiebot.rag;

import jakarta.enterprise.context.RequestScoped;
import java.util.Map;

@RequestScoped
public class RagRequestContext {
    private Map<String,String> variables;

    public Map<String, String> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, String> variables) {
        this.variables = variables;
    }
}
