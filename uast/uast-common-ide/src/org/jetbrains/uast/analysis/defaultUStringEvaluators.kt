// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.analysis

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.patterns.uast.callExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PartiallyKnownString
import com.intellij.psi.util.StringEntry
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.getQualifiedChain

object UStringBuilderEvaluator : BuilderLikeExpressionEvaluator<PartiallyKnownString> {
  override val buildMethod: ElementPattern<PsiMethod>
    get() = PsiJavaPatterns.psiMethod().withName("toString").definedInClass("java.lang.StringBuilder")

  override val dslBuildMethodDescriptor: DslLikeMethodDescriptor<PartiallyKnownString>
    get() = DslLikeMethodDescriptor(
      PsiJavaPatterns.psiMethod().withName("buildString").definedInClass("kotlin.text.StringsKt__StringBuilderKt"),
      DslLambdaDescriptor(DslLambdaPlace.Last, 0) { PartiallyKnownString(listOf()) }
    )

  override val allowSideEffects: Boolean
    get() = true

  override fun isExpressionReturnSelf(expression: UReferenceExpression): Boolean {
    val qualifiedChain = expression.getQualifiedChain()
    val callPattern = callExpression().withAnyResolvedMethod(PsiJavaPatterns.psiMethod().definedInClass("java.lang.StringBuilder"))
    return qualifiedChain.firstOrNull().let { it is USimpleNameReferenceExpression || it is UThisExpression } &&
           qualifiedChain.drop(1).all { callPattern.accepts(it) }
  }

  override val methodDescriptions: Map<ElementPattern<PsiMethod>, BuilderMethodEvaluator<PartiallyKnownString>>
    get() = mapOf(
      PsiJavaPatterns.psiMethod().withName("append").definedInClass("java.lang.StringBuilder") to
        BuilderMethodEvaluator { call, currentResult, stringEvaluator, config, isStrict ->
          val entries = currentResult?.segments?.toMutableList()
                        ?: mutableListOf<StringEntry>(StringEntry.Unknown(call.sourcePsi!!, TextRange(0, 1)))
          val argument = call.getArgumentForParameter(0)?.let { argument -> stringEvaluator.calculateValue(argument, config) }
          if (argument != null) {
            if (isStrict) {
              entries.addAll(argument.segments)
            }
            else {
              entries.add(StringEntry.Unknown(
                call.sourcePsi!!,
                TextRange(0, call.sourcePsi!!.textLength),
                possibleValues = listOf(PartiallyKnownString(argument.segments), PartiallyKnownString(listOf()))
              ))
            }
          }

          PartiallyKnownString(entries)
        },
      PsiJavaPatterns.psiMethod().definedInClass("java.lang.StringBuilder").constructor(true) to
        BuilderMethodEvaluator { call, _, stringEvaluator, config, _ ->
          call.getArgumentForParameter(0)?.let { argument ->
            stringEvaluator.calculateValue(argument, config)
          } ?: PartiallyKnownString(listOf())
        }
    )
}