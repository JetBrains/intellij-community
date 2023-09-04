package com.intellij.searchEverywhereMl.semantics.models

import io.kinference.core.CoreBackend
import io.kinference.core.KIEngine
import io.kinference.core.data.tensor.KITensor
import io.kinference.core.data.tensor.asTensor
import io.kinference.data.ONNXData
import io.kinference.model.Model
import io.kinference.ndarray.arrays.*

/**
 * Wrapper around a transformer neural network that applies it to encoded texts
 * and transforms the last hidden state from the model into embeddings via mean pooling.
 * Can work with batched data.
 *
 * @property network KInference model
 * @property maxLen upper restriction on a number of input tokens required by model
 */
class LocalEmbeddingNetwork(
  private val network: Model<ONNXData<*, CoreBackend>>,
  val maxLen: Int = DEFAULT_MAX_LEN
) {
  companion object {
    suspend operator fun invoke(data: ByteArray, maxLen: Int? = null): LocalEmbeddingNetwork {
      return LocalEmbeddingNetwork(KIEngine.loadModel(data), maxLen ?: DEFAULT_MAX_LEN)
    }

    const val DEFAULT_MAX_LEN = 512
  }

  @Suppress("Unused") // useful for older versions of kinference where operators for mean pooling are not supported
  private suspend fun meanPooling(lastHiddenState: FloatNDArray, attentionMask: NumberNDArray): FloatNDArray {
    val attentionMaskPointer = (attentionMask as IntNDArray).array.pointer()
    val floatAttentionMask = FloatNDArray(attentionMask.shape) {
      attentionMaskPointer.getAndIncrement().toFloat()
    }

    val attentionMaskExpanded = floatAttentionMask
      .unsqueeze(-1)
      .expand(lastHiddenState.shape) as NumberNDArray
    val sum = lastHiddenState
      .times(attentionMaskExpanded)
      .reduceSum(axes = intArrayOf(1), keepDims = false)
    val count = attentionMaskExpanded
      .reduceSum(axes = intArrayOf(1), keepDims = false) as NumberNDArray

    return sum.div(count)
  }

  suspend fun predict(inputIds: NumberNDArray, attentionMask: NumberNDArray): FloatNDArray {
    val prediction = network.predict(listOf(
      (inputIds as NDArrayCore).asTensor("input_ids"),
      (attentionMask as NDArrayCore).asTensor("attention_mask")
    ))
    return (prediction["embeddings"] as KITensor).data as FloatNDArray
  }
}