package org.ohdsi.webapi.prediction;

import com.cosium.spring.data.jpa.entity.graph.domain.EntityGraph;
import com.cosium.spring.data.jpa.entity.graph.domain.EntityGraphUtils;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.ohdsi.analysis.Utils;
import org.ohdsi.analysis.prediction.design.AdaBoostSettings;
import org.ohdsi.circe.helper.ResourceHelper;
import org.ohdsi.hydra.Hydra;
import org.ohdsi.utilities.InMemoryFile;
import org.ohdsi.webapi.algorithm.AlgortihmService;
import org.ohdsi.webapi.algorithm.Certificate;
import org.ohdsi.webapi.algorithm.CustomAlgorithm;
import org.ohdsi.webapi.algorithm.repository.CertificateRepository;
import org.ohdsi.webapi.algorithm.repository.CustomAlgorithmRepository;
import org.ohdsi.webapi.analysis.AnalysisCohortDefinition;
import org.ohdsi.webapi.analysis.AnalysisConceptSet;
import org.ohdsi.webapi.cohortdefinition.CohortDefinition;
import org.ohdsi.webapi.cohortdefinition.CohortDefinitionRepository;
import org.ohdsi.webapi.common.DesignImportService;
import org.ohdsi.webapi.common.generation.AnalysisExecutionSupport;
import org.ohdsi.webapi.common.generation.GenerationUtils;
import org.ohdsi.webapi.conceptset.ConceptSetCrossReferenceImpl;
import org.ohdsi.webapi.executionengine.entity.AnalysisFile;
import org.ohdsi.webapi.featureextraction.specification.CovariateSettingsImpl;
import org.ohdsi.webapi.job.GeneratesNotification;
import org.ohdsi.webapi.job.JobExecutionResource;
import org.ohdsi.webapi.prediction.domain.PredictionGenerationEntity;
import org.ohdsi.webapi.prediction.repository.PredictionAnalysisGenerationRepository;
import org.ohdsi.webapi.prediction.repository.PredictionAnalysisRepository;
import org.ohdsi.webapi.prediction.specification.CustomAlgorithmSettingsImpl;
import org.ohdsi.webapi.prediction.specification.ModelSettingsImpl;
import org.ohdsi.webapi.prediction.specification.PatientLevelPredictionAnalysisImpl;
import org.ohdsi.webapi.service.ConceptSetService;
import org.ohdsi.webapi.service.JobService;
import org.ohdsi.webapi.service.VocabularyService;
import org.ohdsi.webapi.service.dto.ConceptSetDTO;
import org.ohdsi.webapi.shiro.annotations.DataSourceAccess;
import org.ohdsi.webapi.shiro.annotations.SourceKey;
import org.ohdsi.webapi.shiro.management.datasource.SourceAccessor;
import org.ohdsi.webapi.source.Source;
import org.ohdsi.webapi.source.SourceService;
import org.ohdsi.webapi.util.EntityUtils;
import org.ohdsi.webapi.util.NameUtils;
import org.ohdsi.webapi.util.SessionUtils;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import javax.ws.rs.InternalServerErrorException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.Base64;
import java.util.Base64.Encoder;

import static org.ohdsi.webapi.Constants.GENERATE_PREDICTION_ANALYSIS;
import static org.ohdsi.webapi.Constants.Params.JOB_NAME;
import static org.ohdsi.webapi.Constants.Params.PREDICTION_ANALYSIS_ID;

@Service
@Transactional
public class PredictionServiceImpl extends AnalysisExecutionSupport implements PredictionService, GeneratesNotification {

    private static final EntityGraph DEFAULT_ENTITY_GRAPH = EntityGraphUtils.fromAttributePaths("source", "analysisExecution.resultFiles");

    private final EntityGraph COMMONS_ENTITY_GRAPH = EntityUtils.fromAttributePaths(
            "createdBy",
            "modifiedBy"
    );

    @Value("${hydra.externalPackage.prediction}")
    private String extenalPackagePath;

    @Autowired
    private PredictionAnalysisRepository predictionAnalysisRepository;

    @PersistenceContext
    protected EntityManager entityManager;

    @Autowired
    private ConceptSetService conceptSetService;
    
    @Autowired
    private VocabularyService vocabularyService;

    @Autowired
    private CohortDefinitionRepository cohortDefinitionRepository;

    @Autowired
    private SourceService sourceService;

    @Autowired
    private GenerationUtils generationUtils;

    @Autowired
    private JobService jobService;

    @Autowired
    private AlgortihmService algorithmService;

    @Autowired
    private PredictionAnalysisGenerationRepository generationRepository;
    
    @Autowired
    private Environment env;

    @Autowired
    private SourceAccessor sourceAccessor;
    
    @Autowired
    private DesignImportService designImportService;
    
    @Autowired
    private ConversionService conversionService;
    
    private final String EXEC_SCRIPT = ResourceHelper.GetResourceAsString("/resources/prediction/r/runAnalysis.R");

    @Override
    public Iterable<PredictionAnalysis> getAnalysisList() {

        return predictionAnalysisRepository.findAll(COMMONS_ENTITY_GRAPH);
    }
    
    @Override
    public int getCountPredictionWithSameName(Integer id, String name) {

        return predictionAnalysisRepository.getCountPredictionWithSameName(id, name);
    }

    @Override
    public PredictionAnalysis getById(Integer id) {
        return predictionAnalysisRepository.findOne(id, COMMONS_ENTITY_GRAPH);
    }
    
    @Override
    public void delete(final int id) {
        this.predictionAnalysisRepository.delete(id);
    }
    
    @Override
    public PredictionAnalysis createAnalysis(PredictionAnalysis pred) {
        Date currentTime = Calendar.getInstance().getTime();
        pred.setCreatedBy(getCurrentUser());
        pred.setCreatedDate(currentTime);
        // Fields with information about modifications have to be reseted
        pred.setModifiedBy(null);
        pred.setModifiedDate(null);

        return save(pred);
    }

    @Override
    public PredictionAnalysis updateAnalysis(final int id, PredictionAnalysis pred) {
        PredictionAnalysis predFromDB = getById(id);
        Date currentTime = Calendar.getInstance().getTime();

        pred.setModifiedBy(getCurrentUser());
        pred.setModifiedDate(currentTime);
        // Prevent any updates to protected fields like created/createdBy
        pred.setCreatedDate(predFromDB.getCreatedDate());
        pred.setCreatedBy(predFromDB.getCreatedBy());

        return save(pred);
    }

    private List<String> getNamesLike(String name) {
        return predictionAnalysisRepository.findAllByNameStartsWith(name).stream().map(PredictionAnalysis::getName).collect(Collectors.toList());
    }
    
    @Override
    public PredictionAnalysis copy(final int id) {
        PredictionAnalysis analysis = this.predictionAnalysisRepository.findOne(id);
        entityManager.detach(analysis); // Detach from the persistence context in order to save a copy
        analysis.setId(null);
        analysis.setName(getNameForCopy(analysis.getName()));
        return this.createAnalysis(analysis);
    }
    
    @Override
    public PredictionAnalysis getAnalysis(int id) {

        return this.predictionAnalysisRepository.findOne(id, COMMONS_ENTITY_GRAPH);
    }
    
    @Override
    public PatientLevelPredictionAnalysisImpl exportAnalysis(int id) {
        PredictionAnalysis pred = predictionAnalysisRepository.findOne(id);
        PatientLevelPredictionAnalysisImpl expression;
        try {
            expression = Utils.deserialize(pred.getSpecification(), PatientLevelPredictionAnalysisImpl.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (ModelSettingsImpl modelSettingsImpl : expression.getModelSettings()) {
            int modelId;
            if (modelSettingsImpl instanceof CustomAlgorithmSettingsImpl)
            modelId = ((CustomAlgorithmSettingsImpl) modelSettingsImpl).getId();  
            else 
                continue;
            CustomAlgorithm algorithm = algorithmService.getAlgorithm(modelId);
            String name = algorithm.getName();
            name = name + "Settings";
            ((CustomAlgorithmSettingsImpl)modelSettingsImpl).setSettingsName(name);
        }
        
        // Set the root properties
        expression.setId(pred.getId());
        expression.setName(pred.getName());
        expression.setDescription(pred.getDescription());
        expression.setOrganizationName(env.getRequiredProperty("organization.name"));
        
        // Retrieve the cohort definition details
        ArrayList<AnalysisCohortDefinition> detailedList = new ArrayList<>();
        for (AnalysisCohortDefinition c : expression.getCohortDefinitions()) {
            CohortDefinition cd = cohortDefinitionRepository.findOneWithDetail(c.getId());
            detailedList.add(new AnalysisCohortDefinition(cd));
        }
        expression.setCohortDefinitions(detailedList);
        
        // Retrieve the concept set expressions
        ArrayList<AnalysisConceptSet> pcsList = new ArrayList<>();
        HashMap<Integer, ArrayList<Long>> conceptIdentifiers = new HashMap<>();
        for (AnalysisConceptSet pcs : expression.getConceptSets()) {
            pcs.expression = conceptSetService.getConceptSetExpression(pcs.id);
            pcsList.add(pcs);
            conceptIdentifiers.put(pcs.id, new ArrayList<>(vocabularyService.resolveConceptSetExpression(pcs.expression)));
        }
        expression.setConceptSets(pcsList);
        
        // Resolve all ConceptSetCrossReferences
        for (ConceptSetCrossReferenceImpl xref : expression.getConceptSetCrossReference()) {
            if (xref.getTargetName().equalsIgnoreCase("covariateSettings")) {
                if (xref.getPropertyName().equalsIgnoreCase("includedCovariateConceptIds")) {
                    expression.getCovariateSettings().get(xref.getTargetIndex()).setIncludedCovariateConceptIds(conceptIdentifiers.get(xref.getConceptSetId()));
                } else if (xref.getPropertyName().equalsIgnoreCase("excludedCovariateConceptIds")) {
                    expression.getCovariateSettings().get(xref.getTargetIndex()).setExcludedCovariateConceptIds(conceptIdentifiers.get(xref.getConceptSetId()));
                }
            }
        }
        
        return expression;
    }
    
    @Override
    public PredictionAnalysis importAnalysis(PatientLevelPredictionAnalysisImpl analysis) throws Exception {
        try {
            if (Objects.isNull(analysis.getCohortDefinitions()) || Objects.isNull(analysis.getCovariateSettings())) {
                log.error("Failed to import Prediction. Invalid source JSON.");
                throw new InternalServerErrorException();
            }
            // Create all of the cohort definitions
            // and map the IDs from old -> new
            List<BigDecimal> newTargetIds = new ArrayList<>();
            List<BigDecimal> newOutcomeIds = new ArrayList<>();
            analysis.getCohortDefinitions().forEach((analysisCohortDefinition) -> {
                BigDecimal oldId = new BigDecimal(analysisCohortDefinition.getId());
                analysisCohortDefinition.setId(null);
                CohortDefinition cd = designImportService.persistCohortOrGetExisting(conversionService.convert(analysisCohortDefinition, CohortDefinition.class), true);
                if (analysis.getTargetIds().contains(oldId)) {
                    newTargetIds.add(new BigDecimal(cd.getId()));
                }
                if (analysis.getOutcomeIds().contains(oldId)) {
                    newOutcomeIds.add(new BigDecimal(cd.getId()));
                }
                analysisCohortDefinition.setId(cd.getId());
                analysisCohortDefinition.setName(cd.getName());
            });
            
            // Create all of the concept sets and map
            // the IDs from old -> new
            Map<Integer, Integer> conceptSetIdMap = new HashMap<>();
            analysis.getConceptSets().forEach((pcs) -> { 
               int oldId = pcs.id;
               ConceptSetDTO cs = designImportService.persistConceptSet(pcs);
               pcs.id = cs.getId();
               pcs.name = cs.getName();
               conceptSetIdMap.put(oldId, cs.getId());
            });
            
            // Replace all of the cohort definitions
            analysis.setTargetIds(newTargetIds);
            analysis.setOutcomeIds(newOutcomeIds);
            
            // Replace all of the concept sets
            analysis.getConceptSetCrossReference().forEach((ConceptSetCrossReferenceImpl xref) -> {
                Integer newConceptSetId = conceptSetIdMap.get(xref.getConceptSetId());
                xref.setConceptSetId(newConceptSetId);
            });
            
            // Clear all of the concept IDs from the covariate settings
            analysis.getCovariateSettings().forEach((CovariateSettingsImpl cs) -> {
                cs.setIncludedCovariateConceptIds(new ArrayList<>());
                cs.setExcludedCovariateConceptIds(new ArrayList<>());
            });
            
            // Remove the ID
            analysis.setId(null);
            
            // Create the prediction analysis
            PredictionAnalysis pa = new PredictionAnalysis();
            pa.setDescription(analysis.getDescription());
            pa.setSpecification(Utils.serialize(analysis));
            pa.setName(NameUtils.getNameWithSuffix(analysis.getName(), this::getNamesLike));
            
            PredictionAnalysis savedAnalysis = this.createAnalysis(pa);
            return predictionAnalysisRepository.findOne(savedAnalysis.getId(), COMMONS_ENTITY_GRAPH);
        } catch (Exception e) {
            log.debug("Error while importing prediction analysis: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public String getNameForCopy(String dtoName) {
        return NameUtils.getNameForCopy(dtoName, this::getNamesLike, predictionAnalysisRepository.findByName(dtoName));
    }

    @Override
    public void hydrateAnalysis(PatientLevelPredictionAnalysisImpl analysis, String packageName, OutputStream out) throws IOException {
        if (packageName == null || !Utils.isAlphaNumeric(packageName)) {
            throw new IllegalArgumentException("The package name must be alphanumeric only.");
        }
        analysis.setPackageName(packageName);
        String studySpecs = Utils.serialize(analysis, true);
        Hydra h = new Hydra(studySpecs);
        List<ModelSettingsImpl> modelSettings = analysis.getModelSettings();
        for (ModelSettingsImpl settings : modelSettings) {
            int id;
            if (settings instanceof CustomAlgorithmSettingsImpl)
                id = ((CustomAlgorithmSettingsImpl) settings).getId();  
            else 
                continue;
            CustomAlgorithm algorithm = algorithmService.getAlgorithm(id);
            String file64 = algorithm.getFile();
            byte[] data = Base64.getDecoder().decode(file64);
            List<InMemoryFile> customFiles = new ArrayList<InMemoryFile>();
            byte[] buffer = new byte[1024];
            ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data));
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                if (!zipEntry.isDirectory()) {

                    // write file content
                    StringBuilder s = new StringBuilder();
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        s.append(new String(buffer, 0, len));
                    }
                    String content = s.toString();

                    if (zipEntry.getName().endsWith("model.R")) {
                        String modelName = algorithm.getName() + ".R";
                        InMemoryFile file = new InMemoryFile("model/" + modelName, content);
                        customFiles.add(file);
                    } else {
                        InMemoryFile file = new InMemoryFile(zipEntry.getName(), content);
                        customFiles.add(file);
                    }
                   
                }
                zipEntry = zis.getNextEntry();
            }
            zis.close();

            //InMemoryFile file = new InMemoryFile("CustomAlgorithm" + String.valueOf(id) + ".R", algorithm.getCode());
            
            //customFiles.add(file);
            h.addCustomFiles(customFiles);
            
        }
        
        if (StringUtils.isNotEmpty(extenalPackagePath)) {
            h.setExternalSkeletonFileName(extenalPackagePath);
        }
        File plpFile = copyResourceToTempFile("/resources/prediction/SkeletonPredictionStudy_0.0.1.zip", "plp", "zip");
        System.out.println(plpFile.getAbsolutePath());
        h.setExternalSkeletonFileName(plpFile.getAbsolutePath());


        h.hydrate(out);
    }

    public File copyResourceToTempFile(String resource, String prefix, String suffix) throws IOException {

        File tempFile = File.createTempFile(prefix, suffix);
        try(InputStream in = PredictionServiceImpl.class.getResourceAsStream(resource)) {
        try(OutputStream out = new FileOutputStream(tempFile)) {
        IOUtils.copy(in, out);
        }
        }
        return tempFile;
        }
    
    @Override
    @DataSourceAccess
    public JobExecutionResource runGeneration(final PredictionAnalysis predictionAnalysis,
                                              @SourceKey final String sourceKey) throws IOException {

        final Source source = sourceService.findBySourceKey(sourceKey);
        final Integer predictionAnalysisId = predictionAnalysis.getId();

        String packageName = String.format("PredictionAnalysis.%s", SessionUtils.sessionId());
        String packageFilename = String.format("prediction_study_%d.zip", predictionAnalysisId);
        List<AnalysisFile> analysisFiles = new ArrayList<>();
        AnalysisFile analysisFile = new AnalysisFile();
        analysisFile.setFileName(packageFilename);
        try(ByteArrayOutputStream out = new ByteArrayOutputStream()) {
          PatientLevelPredictionAnalysisImpl analysis = exportAnalysis(predictionAnalysisId);
          hydrateAnalysis(analysis, packageName, out);
          analysisFile.setContents(out.toByteArray());
        }
        analysisFiles.add(analysisFile);
        analysisFiles.add(prepareAnalysisExecution(packageName, packageFilename, predictionAnalysisId));

        JobParametersBuilder builder = prepareJobParametersBuilder(source, predictionAnalysisId, packageName, packageFilename)
                .addString(PREDICTION_ANALYSIS_ID, predictionAnalysisId.toString())
                .addString(JOB_NAME, String.format("Generating Prediction Analysis %d using %s (%s)", predictionAnalysisId, source.getSourceName(), source.getSourceKey()));


        Job generateAnalysisJob = generationUtils.buildJobForExecutionEngineBasedAnalysisTasklet(
                GENERATE_PREDICTION_ANALYSIS,
                source,
                builder,
                analysisFiles
        ).build();

        return jobService.runJob(generateAnalysisJob, builder.toJobParameters());
    }

    @Override
    protected String getExecutionScript() {

        return EXEC_SCRIPT;
    }

    @Override
    public List<PredictionGenerationEntity> getPredictionGenerations(Integer predictionAnalysisId) {

        return generationRepository
            .findByPredictionAnalysisId(predictionAnalysisId, DEFAULT_ENTITY_GRAPH)
            .stream()
            .filter(gen -> sourceAccessor.hasAccess(gen.getSource()))
            .collect(Collectors.toList());
    }

    @Override
    public PredictionGenerationEntity getGeneration(Long generationId) {

        return generationRepository.findOne(generationId, DEFAULT_ENTITY_GRAPH);
    }
    
    private PredictionAnalysis save(PredictionAnalysis analysis) {
        analysis = predictionAnalysisRepository.saveAndFlush(analysis);
        entityManager.refresh(analysis);
        analysis = getById(analysis.getId());
        return analysis;
    }

    

    @Override
    public String getJobName() {
        return GENERATE_PREDICTION_ANALYSIS;
    }

    @Override
    public String getExecutionFoldingKey() {
        return PREDICTION_ANALYSIS_ID;
    }
}
