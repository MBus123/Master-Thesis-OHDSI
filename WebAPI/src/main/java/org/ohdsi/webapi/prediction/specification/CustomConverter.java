package org.ohdsi.webapi.prediction.specification;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;

public class CustomConverter extends JsonSerializer<CustomAlgorithmSettingsImpl> {

    @Override
    public void serialize(CustomAlgorithmSettingsImpl value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException, JsonProcessingException {
                //gen.writeObject(value.getCustomParameters());
                //gen.writeObjectField("TransformerSettings", value.getCustomParameters());
                gen.writeStartObject();
                gen.writeFieldName(value.getSettingsName());
                Map<String, Object> parameters = new HashMap<>(value.getCustomParameters());
                parameters.remove("selectedItem");
                gen.writeObject(parameters);
                gen.writeEndObject();


        
    }

    @Override
    public void serializeWithType(CustomAlgorithmSettingsImpl value, JsonGenerator gen, SerializerProvider serializers, TypeSerializer typeSer)
    throws IOException, JsonProcessingException {

        //typeSer.writeCustomTypePrefixForObject(value, gen, "TransformerSettings");
        serialize(value, gen, serializers); // call your customized serialize method
        //typeSer.writeCustomTypeSuffixForObject(value, gen, "TransformerSettings");
    }
    
}
