package org.ohdsi.webapi.prediction.specification;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.apache.commons.lang.NotImplementedException;
import org.ohdsi.analysis.prediction.design.ModelSettings;

@JsonSerialize(using = CustomConverter.class)
public class CustomAlgorithmSettingsImpl extends SeedSettingsImpl implements ModelSettings {
    
    private Map<String, Object> customParameters = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getCustomParameters() {
	return customParameters;
    }

    @JsonAnySetter
    public void setCustomParameters(String name, Object value) {
	this.customParameters.put(name, value);
    }

    public int getId() {
        return (int) customParameters.get("selectedItem");
    }

    @JsonIgnore
    private String settingsName = "CustomAlgorithmSettings";

    public String getSettingsName() {
        return this.settingsName;
    }

    public void setSettingsName(String name) {
        this.settingsName = name;
    }

}
