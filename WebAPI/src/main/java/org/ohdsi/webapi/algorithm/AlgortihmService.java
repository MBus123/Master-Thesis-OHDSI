package org.ohdsi.webapi.algorithm;

import java.io.IOException;

public interface AlgortihmService {

    Iterable<CustomAlgorithm> getCustomAlgorithmList(); 

    CustomAlgorithm addAlgorithm(int id, CustomAlgorithm algorithm);

    CustomAlgorithm addNewAlgorithm(CustomAlgorithm algorithm);

    CustomAlgorithm prepareAlgorithm(CustomAlgorithm algorithm) throws IOException;

    CustomAlgorithm getAlgorithm(int id);

    CustomAlgorithm getAlgorithmById(Integer id);

    void createCertificate(Integer secret, String model_name);

    int getCountAlgorithmsWithSameName(Integer id, String name);

    boolean verifyCertificate(CustomAlgorithm algorithm) throws IOException;

    void delete(int id);
    
}
