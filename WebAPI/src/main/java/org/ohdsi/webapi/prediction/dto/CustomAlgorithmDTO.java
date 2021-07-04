package org.ohdsi.webapi.prediction.dto;

import org.ohdsi.webapi.common.analyses.CommonAnalysisDTO;

public class CustomAlgorithmDTO extends CommonAnalysisDTO {

    private String code; 
    private String hyperParameters;
    private String file;

    public String getCode() {
        return this.code;
    }

    public String getHyperParameters() {
        return this.hyperParameters;
    }

    public String getFile() {
        return this.file;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setHyperParameters(String hyperParameters) {
        this.hyperParameters = hyperParameters;
    }

    public void setFile(String fileBase64) {
        this.file = fileBase64;
    }
}
