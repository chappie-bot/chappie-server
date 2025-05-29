package org.chappiebot;

import java.util.Map;

public record GenericInput(String programmingLanguage,
                            String programmingLanguageVersion,
                            String quarkusVersion,
                            String systemmessageTemplate, 
                            String usermessageTemplate,
                            Map<String,String> variables){

    public String getUserMessage() {
        return getMessage(usermessageTemplate, variables);
    }
    
    public String getSystemMessage() {
        return getMessage(systemmessageTemplate, variables);
    }

    // TODO: There must be a Langchain4J way to do this ?    
    private String getMessage(String result, Map<String, String> variables) {
        if(variables!=null && !variables.isEmpty()){
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
        }
        return result;
    }
    
}
