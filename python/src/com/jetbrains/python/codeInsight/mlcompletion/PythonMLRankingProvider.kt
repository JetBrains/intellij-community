// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion

import com.intellij.internal.ml.catboost.CatBoostJarCompletionModelProvider
import com.intellij.internal.ml.completion.DecoratingItemsPolicy
import com.intellij.lang.Language
import com.jetbrains.python.PyBundle

class PythonMLRankingProvider :
  CatBoostJarCompletionModelProvider(PyBundle.message("settings.completion.ml.python.display.name"), "python_features", "python_model") {

  override fun isLanguageSupported(language: Language): Boolean = language.id.compareTo("python", ignoreCase = true) == 0

  override fun isEnabledByDefault() = true

  override fun getDecoratingPolicy(): DecoratingItemsPolicy = DecoratingItemsPolicy.Composite(
    DecoratingItemsPolicy.ByAbsoluteThreshold(3.0),
    DecoratingItemsPolicy.ByRelativeThreshold(2.0)
  )
}