package com.intellij.ide.actions.searcheverywhere.ml.model.local

import com.intellij.internal.ml.DecisionFunction

internal fun interface LocalModelProvider {
  fun loadModel(path: String): DecisionFunction?
}
