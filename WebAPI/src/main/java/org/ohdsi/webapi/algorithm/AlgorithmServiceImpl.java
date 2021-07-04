package org.ohdsi.webapi.algorithm;

import com.cosium.spring.data.jpa.entity.graph.domain.EntityGraph;
import org.json.JSONObject;
import org.ohdsi.circe.helper.ResourceHelper;
import org.ohdsi.webapi.algorithm.repository.CertificateRepository;
import org.ohdsi.webapi.algorithm.repository.CustomAlgorithmRepository;
import org.ohdsi.webapi.common.generation.AnalysisExecutionSupport;
import org.ohdsi.webapi.job.GeneratesNotification;
import org.ohdsi.webapi.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.Base64.Encoder;

import static org.ohdsi.webapi.Constants.GENERATE_PREDICTION_ANALYSIS;
import static org.ohdsi.webapi.Constants.Params.PREDICTION_ANALYSIS_ID;

@Service
@Transactional
public class AlgorithmServiceImpl extends AnalysisExecutionSupport implements AlgortihmService, GeneratesNotification {

    @Autowired
    private CustomAlgorithmRepository algorithmRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    private final EntityGraph COMMONS_ENTITY_GRAPH = EntityUtils.fromAttributePaths(
            "createdBy",
            "modifiedBy"
    );

    private final String EXEC_SCRIPT = ResourceHelper.GetResourceAsString("/resources/prediction/r/runAnalysis.R");

    @PersistenceContext
    protected EntityManager entityManager;

    @Override
    public int getCountAlgorithmsWithSameName(Integer id, String name) {

        return algorithmRepository.getCountAlgorithmsWithSameName(id, name);
    }

    @Override
    protected String getExecutionScript() {

        return EXEC_SCRIPT;
    }

    public CustomAlgorithm addNewAlgorithm(CustomAlgorithm algorithm) {
        Date currentTime = Calendar.getInstance().getTime();
        algorithm.setModifiedBy(getCurrentUser());
        algorithm.setModifiedDate(currentTime);
        algorithm.setCreatedDate(currentTime);
        algorithm.setCreatedBy(getCurrentUser());
        return saveCustomAlgorithm(algorithm);
    }

    @Override
    public CustomAlgorithm getAlgorithm(int id) {
        return algorithmRepository.findById(id, COMMONS_ENTITY_GRAPH);
    }

    @Override
    public CustomAlgorithm getAlgorithmById(Integer id) {
        return algorithmRepository.findById(id, COMMONS_ENTITY_GRAPH);
    }

    @Override
    public void delete(int id) {
      algorithmRepository.delete(id);
    }

    @Override
    public void createCertificate(Integer secret, String model_name) {
        Certificate certificate = new Certificate();
        certificate.setSecret(secret);
        certificate.setName(model_name);
        certificateRepository.saveAndFlush(certificate);

    }

    private CustomAlgorithm saveCustomAlgorithm(CustomAlgorithm algorithm) {
        algorithm = algorithmRepository.saveAndFlush(algorithm);
        entityManager.refresh(algorithm);
        algorithm = getAlgorithmById(algorithm.getId());
        return algorithm;
    }

    public CustomAlgorithm addAlgorithm(int id, CustomAlgorithm algorithm) {
        CustomAlgorithm algFromDB = getAlgorithmById(id);
        Date currentTime = Calendar.getInstance().getTime();
        algorithm.setModifiedBy(getCurrentUser());
        algorithm.setModifiedDate(currentTime);
        algorithm.setCreatedDate(algFromDB.getCreatedDate());
        algorithm.setCreatedBy(algFromDB.getCreatedBy());
        //algorithm.setId(id);
        return saveCustomAlgorithm(algorithm);
    }

    public CustomAlgorithm prepareAlgorithm(CustomAlgorithm algorithm) throws IOException {
      //validate certificate
      byte[] data = Base64.getDecoder().decode(algorithm.getFile());
      File tempFolder = Files.createTempDirectory("temp_new_algorithm").toFile();
      tempFolder.mkdirs();
      ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data));
      ZipEntry zipEntry = zis.getNextEntry();
      byte[] buffer = new byte[1024];
      while (zipEntry != null) {
        String filePath = tempFolder.getAbsolutePath() + File.separator + zipEntry.getName();
        if (zipEntry.isDirectory()) {
          File directory = new File(filePath);
          directory.mkdirs();
        } else {
          File parent = new File(filePath).getParentFile();
          if (!parent.exists()) {
            parent.mkdirs();
          }
          // write file content
          FileOutputStream fos = new FileOutputStream(filePath);
          int len;
          while ((len = zis.read(buffer)) > 0) {
              fos.write(buffer, 0, len);
          }
          fos.close();
        }
        zipEntry = zis.getNextEntry();
      }
      zis.close();

      String settings_path = tempFolder.getAbsolutePath() + File.separator + "settings.json";
      String settingsString = Files.lines(Paths.get(settings_path)).reduce((x, y) -> x + y).get();
      JSONObject settings = new JSONObject(settingsString);
      String hyperParameters = settings.getJSONArray("hyper_parameters").toString();
      String modelName = settings.getString("model_name").toString();
      algorithm.setHyperParameters(hyperParameters);
      algorithm.setName(modelName);
      List<String> fileNames = new ArrayList<String>(Arrays.asList(new File(tempFolder.getAbsolutePath() + File.separator + "custom").list()));
      fileNames = fileNames.stream().map(x -> tempFolder.getAbsolutePath() + File.separator + "custom" + File.separator + x).collect(Collectors.toList());
      fileNames.add(tempFolder.getAbsolutePath() + File.separator + "model.R");
      File tempZip = File.createTempFile("temp", ".zip");
      FileOutputStream fos = new FileOutputStream(tempZip);
      ZipOutputStream zipOut = new ZipOutputStream(fos);
      for (String srcFile : fileNames) {
        File fileToZip = new File(srcFile);
        FileInputStream fis = new FileInputStream(fileToZip);
        zipEntry = null;
        if (srcFile.equals(tempFolder.getAbsolutePath() + File.separator + "model.R")) {
            zipEntry = new ZipEntry(fileToZip.getName());
        } else {
            zipEntry = new ZipEntry("custom" + File.separator + fileToZip.getName());
        }
        
        zipOut.putNextEntry(zipEntry);

        byte[] bytes = new byte[1024];
        int length;
        while((length = fis.read(bytes)) >= 0) {
            zipOut.write(bytes, 0, length);
        }
        fis.close();
      }
      zipOut.close();
      fos.close();

      Encoder encoder = Base64.getEncoder();
      String zip64 = encoder.encodeToString(Files.readAllBytes(tempZip.toPath()));
      algorithm.setFile(zip64);
        return algorithm;
    }

    @Override
    public Iterable<CustomAlgorithm> getCustomAlgorithmList() {
        return algorithmRepository.findAll(COMMONS_ENTITY_GRAPH);
    }

    @Override
    public String getJobName() {
        return GENERATE_PREDICTION_ANALYSIS;
    }

    @Override
    public String getExecutionFoldingKey() {
        return PREDICTION_ANALYSIS_ID;
    }

    @Override
    public boolean verifyCertificate(CustomAlgorithm algorithm) throws IOException {
        Certificate toProof = getCertificate(algorithm);
        int secret = toProof.getSecret();
        String name = toProof.getName();

        List<Certificate> certificates = certificateRepository.findAll();
        for (Certificate certificate : certificates) {
            if (certificate.getSecret() == secret && certificate.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private Certificate getCertificate(CustomAlgorithm algorithm) throws IOException {
        byte[] data = Base64.getDecoder().decode(algorithm.getFile());
      File tempFolder = Files.createTempDirectory("temp_new_algorithm").toFile();
      tempFolder.mkdirs();
      ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data));
      ZipEntry zipEntry = zis.getNextEntry();
      byte[] buffer = new byte[1024];
      while (zipEntry != null) {
        String filePath = tempFolder.getAbsolutePath() + File.separator + zipEntry.getName();
        if (zipEntry.isDirectory()) {
          File directory = new File(filePath);
          directory.mkdirs();
        } else {
          File parent = new File(filePath).getParentFile();
          if (!parent.exists()) {
            parent.mkdirs();
          }
          // write file content
          FileOutputStream fos = new FileOutputStream(filePath);
          int len;
          while ((len = zis.read(buffer)) > 0) {
              fos.write(buffer, 0, len);
          }
          fos.close();
        }
        zipEntry = zis.getNextEntry();
      }
      zis.close();

      String cert_path = tempFolder.getAbsolutePath() + File.separator + "cert";
      int cert = Integer.parseUnsignedInt(Files.lines(Paths.get(cert_path)).reduce((x, y) -> x + y).get().replace("\n", ""));

      String settings_path = tempFolder.getAbsolutePath() + File.separator + "settings.json";
      String settingsString = Files.lines(Paths.get(settings_path)).reduce((x, y) -> x + y).get();
      JSONObject settings = new JSONObject(settingsString);
      String modelName = settings.getString("model_name").toString();
      Certificate certificate = new Certificate(cert, modelName);
        return certificate;
    }
}