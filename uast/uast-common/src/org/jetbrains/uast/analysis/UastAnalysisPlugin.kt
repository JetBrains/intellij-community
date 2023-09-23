// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.analysis

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UExpression

/**
 * Extension which allows to provide additional information (facts) about UAST expressions which could be used in analysis for all UAST languages.
 */
@ApiStatus.Experimental
interface UastAnalysisPlugin {
  companion object {
    private val extensionPointName = ExtensionPointName<UastAnalysisPlugin>("org.jetbrains.uast.analysis.uastAnalysisPlugin")

    @JvmStatic
    fun byLanguage(language: Language): UastAnalysisPlugin? = extensionPointName.extensionList.firstOrNull { it.language == language }
  }

  /**
   * Language for which these expression facts can be gathered.
   */
  val language: Language

  /**
   * @return fact about given expression which is defined by the language semantics
   */
  fun <T : Any> UExpression.getExpressionFact(fact: UExpressionFact<T>): T?
}

sealed class UExpressionFact<T : Any> {
  object UNullabilityFact : UExpressionFact<UNullability>()
}
