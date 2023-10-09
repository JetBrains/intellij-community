package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.searchEverywhereMl.semantics.utils.ScoredText
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

abstract class AbstractEmbeddingsStorage {
  @RequiresBackgroundThread
  abstract suspend fun searchNeighboursIfEnabled(text: String, topK: Int, similarityThreshold: Double? = null): List<ScoredText>


  @RequiresBackgroundThread
  abstract suspend fun searchNeighbours(text: String, topK: Int, similarityThreshold: Double? = null): List<ScoredText>
}