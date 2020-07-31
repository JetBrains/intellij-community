// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.storage

import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.performance.CompletionPerformanceTracker
import com.intellij.completion.sorting.RankingModelWrapper
import com.intellij.lang.Language
import com.intellij.completion.personalization.session.LookupSessionFactorsStorage

interface LookupStorage {
  companion object {
    fun get(lookup: LookupImpl): LookupStorage? = MutableLookupStorage.get(lookup)
  }

  val model: RankingModelWrapper?
  val language: Language
  val startedTimestamp: Long
  val sessionFactors: LookupSessionFactorsStorage
  val userFactors: Map<String, String>
  val contextFactors: Map<String, String>
  val performanceTracker: CompletionPerformanceTracker
  fun mlUsed(): Boolean
  fun contextProvidersResult(): ContextFeatures
  fun shouldComputeFeatures(): Boolean
  fun getItemStorage(id: String): LookupElementStorage
}