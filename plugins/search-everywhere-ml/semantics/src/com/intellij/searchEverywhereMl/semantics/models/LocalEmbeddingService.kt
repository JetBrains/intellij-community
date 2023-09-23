package com.intellij.searchEverywhereMl.semantics.models

import ai.grazie.emb.FloatTextEmbedding
import ai.grazie.emb.SuspendableTextEmbeddingsService
import ai.grazie.nlp.encoder.PreTrainedTextEncoder
import ai.grazie.utils.ki.TensorUtils
import io.kinference.ndarray.arrays.FloatNDArray

class LocalEmbeddingService(
  private val network: LocalEmbeddingNetwork,
  private val encoder: PreTrainedTextEncoder
): SuspendableTextEmbeddingsService<FloatTextEmbedding> {
  suspend fun embed(texts: List<String>): List<FloatTextEmbedding> {
    val tokenIds = encoder.encodeAsIds(texts, withSpecialTokens = true, maxLen = network.maxLen)
    val attentionMask = tokenIds.map { List(it.size) { 1 } }

    val tokensPadded = TensorUtils.create2DInt32Array(tokenIds, encoder.padId)
    val attentionMaskPadded = TensorUtils.create2DInt32Array(attentionMask, paddingIdx = 0)

    val embeddings: FloatNDArray = network.predict(tokensPadded, attentionMaskPadded)
    return embeddings.split(parts = embeddings.shape[0], axis = 0)
      .map { FloatTextEmbedding((it as FloatNDArray).array.toArray()) }
  }

  override suspend fun embed(texts: List<String>, model: String?): List<FloatTextEmbedding> {
    return embed(texts)
  }
}
