// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion

import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyQualifiedExpression
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntMaps
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object PyMlCompletionHelpers {
  // imports and builtins popularity was calculated on github by number of search results for each present builtins or import
  val importPopularity: Object2IntMap<String> = initMapFromJsonResource("/mlcompletion/importPopularityWeights.json")
  val builtinsPopularity: Object2IntMap<String> = initMapFromJsonResource("/mlcompletion/builtinsPopularityWeights.json")

  private val keyword2id: Object2IntMap<String> = initMapFromJsonResource("/mlcompletion/keywordsNumeration.json")

  fun getKeywordId(kw: String): Int? = keyword2id[kw]

  private fun initMapFromJsonResource(resourcePath: String): Object2IntMap<String> {
    try {
      val resource = PyMlCompletionHelpers::class.java.getResource(resourcePath)
      val result: Map<String, Int> = InputStreamReader(resource.openStream(), StandardCharsets.UTF_8).use {
        Gson().fromJson(it, object : TypeToken<HashMap<String, Int>>() {}.type)
      }
      return Object2IntOpenHashMap(result)
    }
    catch (ex: Throwable) {
      thisLogger().error(ex.message)
      return Object2IntMaps.emptyMap()
    }
  }

  fun getQualifiedComponents(element: PyExpression): List<String> =
    generateSequence<PsiElement>(element) { it.firstChild }
      .filterIsInstance<PyQualifiedExpression>()
      .mapNotNull { it.name }
      .toList()
      .asReversed()
}