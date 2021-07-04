package org.ohdsi.webapi.algorithm;

import org.springframework.stereotype.Controller;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;


import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.ohdsi.webapi.util.ExceptionUtils;
import org.apache.commons.io.IOUtils;
import org.ohdsi.webapi.common.analyses.CommonAnalysisDTO;
import org.ohdsi.webapi.executionengine.service.ScriptExecutionService;
import org.ohdsi.webapi.prediction.dto.CustomAlgorithmDTO;
import org.ohdsi.webapi.source.SourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.support.GenericConversionService;

import javax.ws.rs.core.MediaType;
import com.odysseusinc.arachne.commons.utils.ConverterUtils;

@Controller
@Path("/custom_algorithm/")
public class CustomAlgorithmController {

    private static final String NO_PREDICTION_ANALYSIS_MESSAGE = "There is no prediction analysis with id = %d.";
  
    private final AlgortihmService service;
  
    private final GenericConversionService conversionService;

  
    @Autowired
    public CustomAlgorithmController(AlgortihmService service,
                                GenericConversionService conversionService,
                                ConverterUtils converterUtils,
                                SourceService sourceService,
                                ScriptExecutionService executionService) {
        this.service = service;
        this.conversionService = conversionService;
    }

    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    public List<CommonAnalysisDTO> getAlgorithmList() {
        return StreamSupport
              .stream(service.getCustomAlgorithmList().spliterator(), false)
              .map(analysis -> conversionService.convert(analysis, CommonAnalysisDTO.class))
              .collect(Collectors.toList());
    }


    @POST
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public CustomAlgorithmDTO createAnalysis(CustomAlgorithm customAlgorithm) throws IOException {
      boolean validCertificate = service.verifyCertificate(customAlgorithm);
      if (!validCertificate) {
        return null;
      }
      customAlgorithm = service.prepareAlgorithm(customAlgorithm);
      CustomAlgorithm analysis = service.addNewAlgorithm(customAlgorithm);
      return reloadAndConvert(analysis.getId());
    }

    @GET
    @Path("download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadPackage() throws IOException {

      File tempFile = File.createTempFile("validation", "zip");
      String resource = "/resources/custom_algorithm/validation.zip";
      try(InputStream in = AlgorithmServiceImpl.class.getResourceAsStream(resource)) {
          try(OutputStream out = new FileOutputStream(tempFile)) {
            IOUtils.copy(in, out);
        }
      }

      Response response = Response
            .ok(tempFile)
            .type(MediaType.APPLICATION_OCTET_STREAM)
            .header("Content-Disposition", "attachment; filename=\"validation-script.zip\"")
            .build();

      return response;
    }

    @PUT
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public CustomAlgorithmDTO addAlgorithm(@PathParam("id") int id, CustomAlgorithm algorithm) throws IOException {
      boolean validCertificate = service.verifyCertificate(algorithm);
      if (!validCertificate) {
        return null;
      }
      algorithm = service.prepareAlgorithm(algorithm);
      service.addAlgorithm(id, algorithm);
      return reloadAndConvert(id);
    }

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public CustomAlgorithmDTO getAlgorithm(@PathParam("id") int id) {
  
      CustomAlgorithm analysis = service.getAlgorithm(id);
      ExceptionUtils.throwNotFoundExceptionIfNull(analysis, String.format(NO_PREDICTION_ANALYSIS_MESSAGE, id));
      return conversionService.convert(analysis, CustomAlgorithmDTO.class);
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    public void delete(@PathParam("id") int id) {
      service.delete(id);
    }

    @GET
    @Path("cert/{model_name}")
    public int putinto(@PathParam("model_name") String modelName) {
      Random rand = new Random();
      int upperbound = 10000;
      int int_random = rand.nextInt(upperbound);
      service.createCertificate(int_random, modelName);
      return int_random;
    }

    @GET
    @Path("/{id}/exists")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public int getCountAlgorithmsWithSameName(@PathParam("id") @DefaultValue("0") final int id, @QueryParam("name") String name) {
      return service.getCountAlgorithmsWithSameName(id, name);
    }

    private CustomAlgorithmDTO reloadAndConvert(Integer id) {
      CustomAlgorithm analysis = service.getAlgorithmById(id);
      return conversionService.convert(analysis, CustomAlgorithmDTO.class);
  }
}