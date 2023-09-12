package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.searchEverywhereMl.semantics.utils.ScoredText
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

abstract class AbstractEmbeddingsStorage {
  @RequiresBackgroundThread
  abstract fun searchNeighbours(text: String, topK: Int, similarityThreshold: Double? = null): List<ScoredText>


  @RequiresBackgroundThread
  abstract fun searchNeighboursForce(text: String, topK: Int, similarityThreshold: Double? = null): List<ScoredText>
}