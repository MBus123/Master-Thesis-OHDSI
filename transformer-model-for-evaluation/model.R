# HELPER

createTempModelLoc <- function(){
  repeat{
    loc <- file.path(tempdir(), paste0('python_models_',sample(10002323,1)))
    if(!dir.exists(loc)){
      return(loc)
    }
  }
}

##


setTransformer <- function(){
  
  seed <- as.integer(sample(100000000,1))
  
  result <- list(model='fitTransformer', param=split(expand.grid(size=c(500, 1000), w_decay=c(0.0005, 0.005),
                                                              epochs=c(20, 50), seed=0, 
                                                              class_weight = 0, mlp_type = "mlp_type", autoencoder = FALSE, vae = FALSE),
                                                  1:(length(c(500, 1000))*length(c(0.0005, 0.005))*length(c(20, 50))) ),
                 name='Transformer')
  
  class(result) <- 'modelSettings' 
  
  return(result)
}

fitTransformer <- function(population, plpData, param, search='grid', quiet=F,
                           outcomeId, cohortId, ...){
  
  # check plpData is libsvm format or convert if needed
  if (!FeatureExtraction::isCovariateData(plpData$covariateData)){
    stop("Needs correct covariateData")
  }
  
  # check population has indexes column, which is used to split training with different folds and testing set
  if(colnames(population)[ncol(population)]!='indexes'){
    warning('indexes column not present as last column - setting all index to 1')
    population$indexes <- rep(1, nrow(population))
  }
  
  start <- Sys.time()
  
  population$rowIdPython <- population$rowId-1 # -1 to account for python/r index difference
  pPopulation <-  as.matrix(population[,c('rowIdPython','outcomeCount','indexes')])
  
  # convert plpData in coo to python:
  x <- toSparseM(plpData,population, map=NULL)
  #x <- toSparseTorchPython2(plpData,population, map=NULL, temporal=F)
  
  outLoc <- createTempModelLoc()
  # clear the existing model pickles
  for(file in dir(outLoc))
    file.remove(file.path(outLoc,file))
  
  pydata <- reticulate::r_to_py(x$data)
  
  #do cross validation to find hyperParameter
  hyperParamSel <- lapply(param, function(x) do.call(trainTransformer, 
                                                     listAppend(x, list(plpData=pydata,
                                                                        population = pPopulation,
                                                                        modelOutput=outLoc,
                                                                        train=TRUE))))
  
  
  hyperSummary <- cbind(do.call(rbind, param), unlist(hyperParamSel))
  
  #now train the final model
  bestInd <- which.max(abs(unlist(hyperParamSel)-0.5))[1]
  finalModel <- do.call(trainTransformer, listAppend(param[[bestInd]], 
                                                  list(plpData=pydata,
                                                       population = pPopulation,
                                                       train=FALSE,
                                                       modelOutput=outLoc,
                                                       quiet = quiet)))
  
  covariateRef <- as.data.frame(plpData$covariateData$covariateRef)
  incs <- rep(1, nrow(covariateRef)) 
  covariateRef$included <- incs
  covariateRef$covariateValue <- rep(0, nrow(covariateRef))
  
  modelTrained <- file.path(outLoc) 
  param.best <- param[[bestInd]]
  
  comp <- start-Sys.time()
  
  # train prediction
  pred <- finalModel
  pred[,1] <- pred[,1] + 1 # converting from python to r index
  colnames(pred) <- c('rowId','outcomeCount','indexes', 'value')
  pred <- as.data.frame(pred)
  attr(pred, "metaData") <- list(predictionType="binary")
  prediction <- merge(population, pred[,c('rowId', 'value')], by='rowId')
  
  
  # return model location 
  result <- list(model = modelTrained,
                 trainCVAuc = -1, # ToDo decide on how to deal with this
                 hyperParamSearch = hyperSummary,
                 modelSettings = list(model='fitMLPTorch',modelParameters=param.best),
                 metaData = plpData$metaData,
                 populationSettings = attr(population, 'metaData'),
                 outcomeId=outcomeId,
                 cohortId=cohortId,
                 varImp = covariateRef, 
                 trainingTime =comp,
                 dense=1,
                 covariateMap=x$map, # I think this is need for new data to map the same?
                 predictionTrain = prediction
  )
  class(result) <- 'plpModel'
  attr(result, 'type') <- 'custom'
  attr(result, 'predictionType') <- 'binary'
  
  return(result)
}

predict.custom <- function(plpModel, population, plpData){
  e <- environment()
  reticulate::source_python('custom/CustomModel.py', envir = e)
  
  
  ParallelLogger::logInfo('Mapping covariates...')
  if(!is.null(plpData$timeRef)){
    pdata <- toSparseTorchPython(plpData,population, map=plpModel$covariateMap, temporal=T)
    pdata <- pdata$data
    fun_predict <- python_predict_temporal
  } else {  
    newData <- toSparseM(plpData, population, map=plpModel$covariateMap)
    included <- plpModel$varImp$covariateId[plpModel$varImp$included>0] # does this include map?
    included <- newData$map$newCovariateId[newData$map$oldCovariateId%in%included] 
    pdata <- reticulate::r_to_py(newData$data[,included])
    fun_predict <- python_predict_custom
  }
  
  # save population
  if('indexes'%in%colnames(population)){
    population$rowIdPython <- population$rowId-1 # -1 to account for python/r index difference
    pPopulation <- as.matrix(population[,c('rowIdPython','outcomeCount','indexes')])
    
  } else {
    population$rowIdPython <- population$rowId-1 # -1 to account for python/r index difference
    pPopulation <- as.matrix(population[,c('rowIdPython','outcomeCount')])
  }
  
  # run the python predict code:
  ParallelLogger::logInfo('Executing prediction...')
  result <- fun_predict(population = pPopulation, 
                        plpData = pdata, 
                        model_loc = plpModel$model,
                        dense = ifelse(is.null(plpModel$dense),0,plpModel$dense))
  #get the prediction from python and reformat:
  ParallelLogger::logInfo('Returning results...')
  prediction <- result
  prediction <- as.data.frame(prediction)
  attr(prediction, "metaData") <- list(predictionType="binary")
  if(ncol(prediction)==4){
    colnames(prediction) <- c('rowId','outcomeCount','indexes', 'value')
  } else {
    colnames(prediction) <- c('rowId','outcomeCount', 'value')
  }
  
  # add 1 to rowId from python:
  prediction$rowId <- prediction$rowId+1
  
  # add subjectId and date:
  prediction <- merge(prediction,
                      population[,c('rowId','subjectId','cohortStartDate')], 
                      by='rowId')
  return(prediction)
}


trainTransformer <- function(population, plpData, modelOutput, train, ...){
  
  train_transformer <- function(){return(NULL)}
  
  e <- environment()
  reticulate::source_python("custom/CustomModel.py", envir = e)
  
  result <- train_transformer(population = population, 
                            plpData = plpData, 
                            modelOutput = modelOutput,
                            train = train)
  
  if(train){
    # then get the prediction 
    pred <- result
    colnames(pred) <- c('rowId','outcomeCount','indexes', 'value')
    pred <- as.data.frame(pred)
    attr(pred, "metaData") <- list(predictionType="binary")
    
    pred$value <- 1-pred$value
    auc <- computeAuc(pred)
    writeLines(paste0('Model obtained CV AUC of ', auc))
    return(auc)
  }
  
  return(result)
  
}
