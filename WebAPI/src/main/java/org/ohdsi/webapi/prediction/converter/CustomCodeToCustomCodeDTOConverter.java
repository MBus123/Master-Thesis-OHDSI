package org.ohdsi.webapi.prediction.converter;

import org.ohdsi.webapi.algorithm.CustomAlgorithm;
import org.ohdsi.webapi.prediction.dto.CustomAlgorithmDTO;
import org.springframework.stereotype.Component;

@Component
public class CustomCodeToCustomCodeDTOConverter extends CustomCodeToCommonAnalysisDTOConverter<CustomAlgorithmDTO> {
    
    @Override
    protected CustomAlgorithmDTO createResultObject() {

        return new CustomAlgorithmDTO();
    }

    @Override
    public CustomAlgorithmDTO convert(CustomAlgorithm source) {

        CustomAlgorithmDTO result = super.convert(source);
        result.setCode(source.getCode());
        result.setHyperParameters(source.getHyperParameters());
        result.setFile(source.getFile());
        return result;
    }
}
