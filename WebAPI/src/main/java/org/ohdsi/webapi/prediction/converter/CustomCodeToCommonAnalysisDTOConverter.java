package org.ohdsi.webapi.prediction.converter;

import org.ohdsi.webapi.algorithm.CustomAlgorithm;
import org.ohdsi.webapi.common.analyses.CommonAnalysisDTO;
import org.ohdsi.webapi.converter.BaseConversionServiceAwareConverter;
import org.ohdsi.webapi.util.ConversionUtils;
import org.springframework.stereotype.Component;

@Component
public class CustomCodeToCommonAnalysisDTOConverter <T extends CommonAnalysisDTO> extends BaseConversionServiceAwareConverter<CustomAlgorithm, T> {
    
    @Override
    protected T createResultObject() {

        return (T) new CommonAnalysisDTO();
    }

    @Override
    public T convert(CustomAlgorithm source) {

        T result = createResultObject(source);
        ConversionUtils.convertMetadata(conversionService, source, result);
        result.setId(source.getId());
        result.setName(source.getName());
        result.setDescription(source.getDescription());
        return result;
    }

}
