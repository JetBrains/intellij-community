// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.analysis

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UExpression
import kotlin.streams.asSequence

@ApiStatus.Experimental
interface UastAnalysisPlugin {
  companion object {
    private val extensionPointName = ExtensionPointName<UastAnalysisPlugin>("org.jetbrains.uast.analysis.uastAnalysisPlugin")

    @JvmStatic
    fun byLanguage(language: Language) = extensionPointName.extensions().asSequence().firstOrNull { it.language == language }
  }

  val language: Language

  fun <T : Any> UExpression.getExpressionFact(fact: UExpressionFact<T>): T?

  /**
   * Returns list of known indirect usages of a given value, that is other expressions that could evaluate under some input parameters
   * (but not definitely evaluate) to the supplied expression. Returns an empty list if no such usages found or the analysis 
   * is not implemented for given language/context.
   * 
   * Types of indirect usages include:
   * - If the expression is then/else branch of ?: expression (Java) or if-else expression (Kotlin)
   *   then the whole conditional expression is considered to be an indirect usage
   * - If the expression is return value of switch expression (Java) or when expression (Kotlin)
   *   then the whole switch expression or when expression is considered to be an indirect usage
   * - If the expression is assigned to the local variable
   *   then further references to that local variable are considered to be indirect usages 
   * 
   * Note that the analysis doesn't take into account if the value of given expression changes before usage.
   * Normally, this method should be used only for expressions that are known to be evaluated to the constant value.
   * Also, it's not guaranteed that this value exactly will arrive to the use site. There could be another code branch
   * that produces another value. Finally, the transitive closure of the result might be larger than the result itself,
   * e.g. if the value is reassigned through the several intermediate variables. Finding the transitive closure could be
   * more computational expensive, so the clients must decide whether they need it.
   * 
   * Default implementation returns an empty list.
   */
  fun UExpression.findIndirectUsages(): List<UExpression> = emptyList()
}

sealed class UExpressionFact<T : Any> {
  object UNullabilityFact : UExpressionFact<UNullability>()
}
