package com.intellij.turboComplete.languages.kotlin.k1

import com.intellij.internal.ml.catboost.CatBoostJarCompletionModelProvider
import com.intellij.lang.Language
import com.intellij.turboComplete.SortingExecutorPreferences
import com.intellij.turboComplete.SortingExecutorProvider
import com.intellij.turboComplete.ranking.MLKindSorterProvider
import com.intellij.turboComplete.ranking.provideLocalIfAny
import org.jetbrains.kotlin.idea.completion.KotlinKindVariety

class MLKotlinSuggestionGeneratorExecutorProvider : SortingExecutorProvider(
  SortingExecutorPreferences(
    policyForMostRelevant = SortingExecutorPreferences.MostRelevantKindPolicy.PASS_TO_RESULT,
    policyForNoneKind = SortingExecutorPreferences.NoneKindPolicy.PASS_TO_RESULT,
    executeMostRelevantWhenPassed = 3
  )
) {
  override val sorterProvider = run {
    val mlSorterProvider = MLKindSorterProvider(KotlinKindVariety) { MlKotlinMultipleReferencesSorterProvider().model }
    val maybeLocalSorterProvider = mlSorterProvider
      .provideLocalIfAny("kotlin", "ml.completion.performance.localModel.kotlin")

    maybeLocalSorterProvider
  }
}

class MlKotlinMultipleReferencesSorterProvider : CatBoostJarCompletionModelProvider(
  TurboCompleteKotlinBundle.message(
    "completion.kind.sorter.ml.kotlin"),
  "performance_kotlin_features", "performance_kotlin_model") {

  override fun isLanguageSupported(language: Language): Boolean = language.id.compareTo("kotlin", ignoreCase = true) == 0

  override fun isEnabledByDefault(): Boolean {
    return true
  }
}