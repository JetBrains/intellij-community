package com.intellij.searchEverywhereMl.semantics.models

import ai.grazie.nlp.encoder.PreTrainedTextEncoder
import ai.grazie.nlp.encoder.bert.uncased.BertBaseUncasedTextEncoder
import ai.grazie.utils.mpp.*

class LocalEmbeddingServiceLoader {
  suspend fun load(loader: RootDataLoader): LocalEmbeddingService {
    val network = loadNetwork(loader)
    val encoder = loadTextEncoder(loader)
    return LocalEmbeddingService(network, encoder)
  }

  private suspend fun loadNetwork(loader: RootDataLoader): LocalEmbeddingNetwork {
    val modelLoader = PrefixedDataLoader(DataLoader.Path(MODEL_NAME, "optimized"), loader)
    val tokenizerConfigLoader = PrefixedDataLoader(DataLoader.Path(MODEL_NAME, "encoder"), loader)
    val textEncoderConfig = TextEncoderConfig.loadConfig(tokenizerConfigLoader)
    return LocalEmbeddingNetwork(modelLoader.bytes(MODEL_FILENAME), textEncoderConfig.maxLen)
  }

  private suspend fun loadTextEncoder(loader: RootDataLoader): PreTrainedTextEncoder {
    return BertBaseUncasedTextEncoder.load(loader)
  }

  companion object {
    const val MODEL_NAME = "dan-bert-tiny"
    const val MODEL_FILENAME = "dan_optimized_fp16.onnx"
  }
}