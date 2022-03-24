// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.analysis

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UExpression
import kotlin.streams.asSequence

/**
 * Extension which allows to provide additional information (facts) about UAST expressions which could be used in analysis for all UAST languages.
 */
@ApiStatus.Experimental
interface UastAnalysisPlugin {
  companion object {
    private val extensionPointName = ExtensionPointName<UastAnalysisPlugin>("org.jetbrains.uast.analysis.uastAnalysisPlugin")

    @JvmStatic
    fun byLanguage(language: Language) = extensionPointName.extensions().asSequence().firstOrNull { it.language == language }
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
