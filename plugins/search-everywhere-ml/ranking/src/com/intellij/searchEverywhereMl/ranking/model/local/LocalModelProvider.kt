package com.intellij.searchEverywhereMl.ranking.model.local

import com.intellij.internal.ml.DecisionFunction

internal fun interface LocalModelProvider {
  fun loadModel(path: String): DecisionFunction?
}
