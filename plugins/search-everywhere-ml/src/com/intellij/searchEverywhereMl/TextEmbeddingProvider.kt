package com.intellij.searchEverywhereMl

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.openapi.extensions.ExtensionPointName

interface TextEmbeddingProvider {
  companion object {
    private val EP_NAME: ExtensionPointName<TextEmbeddingProvider> = ExtensionPointName.create(
      "com.intellij.searchEverywhereMl.textEmbeddingProvider")

    fun getProvider(): TextEmbeddingProvider? {
      return EP_NAME.extensionList.firstOrNull()
    }
  }

  fun embed(text: String): FloatTextEmbedding = embed(listOf(text)).single()

  fun embed(texts: List<String>): List<FloatTextEmbedding>
}