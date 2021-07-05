import pickle
import timeit
import numpy as np
import os
import joblib

import torch.nn as nn
import torch
from torch import nn, einsum
import torch.nn.functional as F

from einops import rearrange, repeat
from einops.layers.torch import Rearrange

import torch.optim as optim
from torch.utils.data import DataLoader, Dataset

###

# helpers

def pair(t):
    return t if isinstance(t, tuple) else (t, t)

# classes

class PreNorm(nn.Module):
    def __init__(self, dim, fn):
        super().__init__()
        self.norm = nn.LayerNorm(dim)
        self.fn = fn
    def forward(self, x, **kwargs):
        return self.fn(self.norm(x), **kwargs)

class FeedForward(nn.Module):
    def __init__(self, dim, hidden_dim, dropout = 0.):
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(dim, hidden_dim),
            nn.GELU(),
            nn.Dropout(dropout),
            nn.Linear(hidden_dim, dim),
            nn.Dropout(dropout)
        )
    def forward(self, x):
        return self.net(x)

class Attention(nn.Module):
    def __init__(self, dim, heads = 8, dim_head = 64, dropout = 0.):
        super().__init__()
        inner_dim = dim_head *  heads
        project_out = not (heads == 1 and dim_head == dim)

        self.heads = heads
        self.scale = dim_head ** -0.5

        self.attend = nn.Softmax(dim = -1)
        self.to_qkv = nn.Linear(dim, inner_dim * 3, bias = False)

        self.to_out = nn.Sequential(
            nn.Linear(inner_dim, dim),
            nn.Dropout(dropout)
        ) if project_out else nn.Identity()

    def forward(self, x):
        b, n, _, h = *x.shape, self.heads
        qkv = self.to_qkv(x).chunk(3, dim = -1)
        q, k, v = map(lambda t: rearrange(t, 'b n (h d) -> b h n d', h = h), qkv)

        dots = einsum('b h i d, b h j d -> b h i j', q, k) * self.scale

        attn = self.attend(dots)

        out = einsum('b h i j, b h j d -> b h i d', attn, v)
        out = rearrange(out, 'b h n d -> b n (h d)')
        return self.to_out(out)



class Transformer(nn.Module):
    def __init__(self, dim, depth, heads, dim_head, mlp_dim, dropout = 0.):
        super().__init__()
        self.layers = nn.ModuleList([])
        for _ in range(depth):
            self.layers.append(nn.ModuleList([
                PreNorm(dim, Attention(dim, heads = heads, dim_head = dim_head, dropout = dropout)),
                PreNorm(dim, FeedForward(dim, mlp_dim, dropout = dropout))
            ]))

        self.head = nn.Linear(dim, 1)
    def forward(self, x):
        for attn, ff in self.layers:
            x = attn(x) + x
            x = ff(x) + x
        x = self.head(x)
        return x
        
    def predict_proba(self, x):
      if type(x) is np.ndarray:
            x = torch.from_numpy(x.astype(np.float32))
      return F.sigmoid(self.forward(x)).numpy()
        
class Data(Dataset):
  
  def __init__(self, x, y):
    self.data = []
    for x_i, y_i in zip(x, y):
      self.data.append([x_i, y_i])
      
  def __len__(self):
    return len(self.data)
    
  def __getitem__(self, item):
    x, y = self.data[item]
    x = np.expand_dims(x, axis=0)
    return x.astype(np.float32), y.astype(np.float32)
   
def batch1(tensor, batch_size = 50):
    """ It is used to create batch samples, each batch has batch_size samples"""
    tensor_list = []
    length = tensor.shape[0]
    i = 0
    while True:
        if (i+1) * batch_size >= length:
            tensor_list.append(tensor[i * batch_size: length])
            return tensor_list
        tensor_list.append(tensor[i * batch_size: (i+1) * batch_size])
        i += 1 

def train_transformer(population, plpData, modelOutput, train=True): 
  # return modelOutput
  y = population[:, 1]
  X = plpData.todense().A
  X = X[np.int64(population[:, 0]), :]
  trainInds = population[:, population.shape[1] - 1] > 0
    
  loss_function = nn.BCELoss()
  if train:
    test_pred = np.zeros(population[population[:, population.shape[1] - 1] > 0, :].shape[0]) 
    
    for i in range(1, int(np.max(population[:, population.shape[1] - 1]) + 1), 1):
          testInd = population[population[:, population.shape[1] - 1] > 0, population.shape[1] - 1] == i
          trainInd = (population[population[:, population.shape[1] - 1] > 0, population.shape[1] - 1] != i)
          train_x = X[trainInds, :][trainInd, :]
          train_y = y[trainInds][trainInd]
          test_x = X[trainInds, :][testInd, :]
          print("Fold %s split %s in train set and %s in test set" % (i, train_x.shape[0], test_x.shape[0]))
          print("Train set contains %s outcomes " % (np.sum(train_y)))
          # if True: 
          #   return test_x.shape, train_x.shape
          # train on fold
          learning_rate = 0.001
          print("Training fold %s" % (i))
          start_time = timeit.default_timer()
          model = Transformer(train_x.shape[1], 1, 1, 4, 4, dropout = 0.)
          optimizer = optim.Adam(model.parameters(), lr=0.0001)
          criterion = nn.BCEWithLogitsLoss()
          data_set = Data(train_x, train_y)
          loader = DataLoader(data_set, batch_size=32, drop_last=True, shuffle=True)
          
          for _ in range(1):
            for batch, label in loader:
              pred_y = model(batch)[:, 0, 0]
              loss = criterion(pred_y, label)
              # print(loss.item(), pred_y[0].item(), label[0].item())
              optimizer.zero_grad()
              loss.backward()
              optimizer.step()
              
        
          
          model.eval()
          ind = (population[:, population.shape[1] - 1] > 0)
          ind = population[ind, population.shape[1] - 1] == i
          
          loader = DataLoader(Data(test_x, torch.ones(test_x.shape[0]).numpy()), batch_size=32)
          # test_batch = batch(test_x, batch_size = 32)
          # t = batch1(test_x, batch_size=32)
          # return len(t), t[0].shape
          temp = []
          with torch.no_grad():
            for test, _ in loader:
              pred_test1 = model.predict_proba(test)[:, 0, 0]
              temp = np.concatenate((temp, pred_test1), axis = 0)
          # return temp.shape, ind.shape, test_x.shape
          test_pred[ind] = temp
          
          del model
          print("Prediction complete: %s rows " % (np.shape(test_pred[ind])[0]))
          print("Mean: %s prediction value" % (np.mean(test_pred[ind])))
        # RETURN CV PREDICTION
    
    test_pred.shape = (population[population[:, population.shape[1] - 1] > 0, :].shape[0], 1)
    prediction = np.append(population[population[:, population.shape[1] - 1] > 0, :], test_pred, axis=1)
    return prediction
  else:
    print("Training final neural network model on all train data...")
    print("X- %s rows and Y %s length" % (X[trainInds, :].shape[0], y[trainInds].shape[0]))
    start_time = timeit.default_timer()
    train_x = X[trainInds, :]
    train_y = y[trainInds]
    if not os.path.exists(modelOutput):
      os.makedirs(modelOutput)
    
    
    model = Transformer(train_x.shape[1], 1, 1, 4, 4, dropout = 0.)
    
    optimizer = optim.Adam(model.parameters(), lr=0.0001)
    criterion = nn.BCEWithLogitsLoss()
    data_set = Data(train_x, train_y)
    loader = DataLoader(data_set, batch_size=32, drop_last=True, shuffle=True)
    
    for _ in range(1):
      for batch, label in loader:
        pred_y = model(batch)[:, 0, 0]
        loss = criterion(pred_y, label)
        optimizer.zero_grad()
        loss.backward()
        optimizer.step()
    
    end_time = timeit.default_timer()
    print("Training final took: %.2f s" % (end_time - start_time))
    print("Model saved to: %s" % (modelOutput))
    joblib.dump(model, os.path.join(modelOutput,'model.pkl'))
    # DO PREDICTION ON TRAIN:
    loader = DataLoader(Data(train_x, torch.ones(train_x.shape[0]).numpy()), batch_size=32)
    train_pred = []
    with torch.no_grad():
      for test, _ in loader:
        preds = model.predict_proba(test)[:, 0, 0]
        train_pred = np.concatenate((train_pred, preds), axis = 0)
    train_pred.shape = (population[population[:, population.shape[1] - 1] > 0, :].shape[0], 1)
    prediction = np.append(population[population[:, population.shape[1] - 1] > 0, :], train_pred, axis=1)
    print("FINISHED TRAINING!!!!!")
    return prediction;
    
def python_predict(population, plpData, model_loc, dense, autoencoder):
  print("Applying Python Model") 
  print("Loading Data...")
  # load data + train,test indexes + validation index
  #y=population[:,1]
  X = plpData[population[:,0].astype(int),:]
  # load index file
  print("population loaded- %s rows and %s columns" %(np.shape(population)[0], np.shape(population)[1]))
  print("Dataset has %s rows and %s columns" %(X.shape[0], X.shape[1]))
  print("Data ready for model has %s features" %(np.shape(X)[1]))
  ###########################################################################	
  # uf dense convert 
  if dense==1:
    print("converting to dense data...")
    X=X.toarray()
  ###########################################################################	
  # load model
  print("Loading model...")
  if autoencoder:
    autoencoder_model = joblib.load(os.path.join(model_loc, 'autoencoder_model.pkl'))
    X = autoencoder_model.get_encode_features(X)
  modelTrained = joblib.load(os.path.join(model_loc,"model.pkl")) 
  print("Calculating predictions on population...")
  test_pred = modelTrained.predict_proba(X)[:, 1]
  print("Prediction complete: %s rows" %(np.shape(test_pred)[0]))
  print("Mean: %s prediction value" %(np.mean(test_pred)))
  # merge pred with population
  test_pred.shape = (population.shape[0], 1)
  prediction = np.append(population,test_pred, axis=1)
  return prediction
    
    
def python_predict_custom(population, plpData, model_loc, dense):
  with torch.no_grad():
    print("Applying Python Model") 
    print("Loading Data...")
    # load data + train,test indexes + validation index
    X = plpData[population[:,0].astype(int),:]
    
    print("population loaded- %s rows and %s columns" %(np.shape(population)[0], np.shape(population)[1]))
    print("Dataset has %s rows and %s columns" %(X.shape[0], X.shape[1]))
    print("Data ready for model has %s features" %(np.shape(X)[1]))
    
    if dense==1:
      print("converting to dense data...")
      X=X.toarray()
      
    modelTrained = joblib.load(os.path.join(model_loc,"model.pkl")) 
    modelTrained.eval()
    print("Calculating predictions on population...")
    X = torch.tensor(X).unsqueeze(1).float()
    test_pred = modelTrained.predict_proba(X)[:, 0, 0]
    print("Prediction complete: %s rows" %(np.shape(test_pred)[0]))
    print("Mean: %s prediction value" %(np.mean(test_pred)))
    test_pred.shape = (population.shape[0], 1)
    prediction = np.append(population,test_pred, axis=1)
    return prediction
