// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.openapi.components.service
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.statistics.PackageDaemonTaskExecutor
import com.jetbrains.python.statistics.PyPackageUsageStatistics
import kotlinx.coroutines.runBlocking

class PyPackageDaemonTest : PyTestCase() {

  fun doTest(text: String, vararg packageNames: String) {
    val psiFile = myFixture.configureByText("test.py", text)
    val job = service<PackageDaemonTaskExecutor>().execute(psiFile.virtualFile, psiFile.project)
    runBlocking {
      job.join()
    }
    val state = PyPackageUsageStatistics.getInstance(psiFile.project).getStatisticsAndResetState()
    assertSameElements(state.keys.mapNotNull { it.name }.toSet(), packageNames.toSet())
  }

  fun testHuggingFaceDatasets() {
    doTest("""
      from datasets import load_dataset
      
      def load_data():
        dataset = load_dataset('wikitext', 'wikitext-2-raw-v1')
                 
      if __name__ == "__main__":
        load_data()
    """.trimIndent(), "datasets")
  }

  fun testHuggingFaceHub() {
    doTest("""
      from huggingface_hub import hf_hub_url, cached_download
      from transformers import AutoTokenizer, TFAutoModelForSequenceClassification

      # Model name
      model_id = "distilbert-base-uncased-finetuned-sst-2-english"

      # Download model from Hugging Face Hub and load it
      model_url = hf_hub_url(model_id, filename="pytorch_model.bin")
      model_path = cached_download(model_url)
      model = TFAutoModelForSequenceClassification.from_pretrained(model_path, num_labels=2)

      # Download tokenization model from Hugging Face Hub and load it
      tokenizer_url = hf_hub_url(model_id, filename="tokenizer.json")
      tokenizer_path = cached_download(tokenizer_url)
      tokenizer = AutoTokenizer.from_pretrained(tokenizer_path)

      # Text to classify
      text = "I love using my new phone. The image quality is amazing!"

      # Run the model
      inputs = tokenizer(text, return_tensors='pt')
      outputs = model(**inputs)
      classification_scores = outputs.logits

      # Output the classification scores
      print(classification_scores)
    """.trimIndent(), "huggingface-hub", "transformers")
  }

  fun testPyTorchIsReported() {
    doTest("""
      import torch
      import torch.nn as nn
      from torch.autograd import Variable

      # Our data was simple 2 dimensional data
      X = torch.tensor([[1.0], [2.0], [3.0], [4.0]])
      Y = torch.tensor([[0.], [0.], [1.], [1.]])

      # Here we define our model as a class
      class LogisticRegressionModel(nn.Module):
          def __init__(self, input_dim, output_dim):
              super(LogisticRegressionModel, self).__init__()
              self.linear = nn.Linear(input_dim, output_dim)

          def forward(self, x):
              out = torch.sigmoid(self.linear(x))
              return out

      input_dim = 1
      output_dim = 1

      model = LogisticRegressionModel(input_dim, output_dim)

      criterion = torch.nn.BCELoss(reduction='mean')
      optimizer = torch.optim.SGD(model.parameters(), lr=0.01)

      epochs = 1000
      for epoch in range(epochs):
          model.train()
          optimizer.zero_grad()
          # Forward pass
          y_pred = model(X)
          # Compute Loss
          loss = criterion(y_pred, Y)
         
          # Backward pass and optimization
          loss.backward()
          optimizer.step()
          
          if (epoch+1) % 100 == 0:
              print('Epoch: {}, loss: {}'.format(epoch+1, loss.item()))

      # Test the model
      model.eval()
      test_var = Variable(torch.Tensor([[1.0], [7.0]]))
      result = model(test_var)
      print("Predicted probabilities:")
      print(result.data)
    """.trimIndent(), "torch")
  }

  fun testTensorflowTextIsReported() {
    doTest("""
      import tensorflow as tf
      import tensorflow_text as text
      import tensorflow_graphics as tfg
      
      # You might have to download this if you're running it for the first time
      dataset_url = "https://ai.stanford.edu/~amaas/data/sentiment/aclImdb_v1.tar.gz"
      dataset = tf.keras.utils.get_file("aclImdb_v1.tar.gz", dataset_url,
                                          untar=True, cache_dir='.',
                                          cache_subdir='')

    """.trimIndent(), "tensorflow-text", "tensorflow-graphics", "tensorflow")
  }

}