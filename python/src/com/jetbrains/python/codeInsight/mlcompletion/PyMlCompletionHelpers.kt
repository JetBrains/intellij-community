// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion

import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyQualifiedExpression
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object PyMlCompletionHelpers {
  private val LOG = Logger.getInstance("#com.jetbrains.python.codeInsight.completion.mlcompletion")

  // imports and builtins popularity was calculated on github by number of search results for each present builtins or import
  val importPopularity = initMapFromJsonResource("/mlcompletion/importPopularityWeights.json")
  val builtinsPopularity = initMapFromJsonResource("/mlcompletion/builtinsPopularityWeights.json")

  private val keyword2id = initMapFromJsonResource("/mlcompletion/keywordsNumeration.json")

  fun getKeywordId(kw: String): Int? = keyword2id[kw]

  private fun initMapFromJsonResource(resourcePath: String): Map<String, Int> {
    try {
      val resource = PyMlCompletionHelpers::class.java.getResource(resourcePath)
      InputStreamReader(resource.openStream(), StandardCharsets.UTF_8).use {
        return Gson().fromJson(it, object: TypeToken<HashMap<String, Int>>() {}.type)
      }
    }
    catch (ex: Throwable) {
      LOG.error(ex.message)
      return emptyMap()
    }
  }

  fun getQualifiedComponents(element: PyExpression): List<String> =
    generateSequence<PsiElement>(element) { it.firstChild }
      .filterIsInstance<PyQualifiedExpression>()
      .mapNotNull { it.name }
      .toList()
      .asReversed()
}