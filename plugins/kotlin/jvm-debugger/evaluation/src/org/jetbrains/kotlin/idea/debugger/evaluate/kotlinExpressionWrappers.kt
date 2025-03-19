// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaUsualClassType
import org.jetbrains.kotlin.psi.KtExpression

internal interface KotlinExpressionWrapper {
    fun createWrappedExpressionText(expressionText: String): String

    @RequiresReadLock
    fun isApplicable(expression: KtExpression): Boolean
}

internal class KotlinValueClassToStringWrapper : KotlinExpressionWrapper {
    override fun createWrappedExpressionText(expressionText: String) = "($expressionText) as Any?"

    @RequiresReadLock
    override fun isApplicable(expression: KtExpression) = analyze(expression) {
        val ktUsualClassType = expression.expressionType as? KaUsualClassType
        val ktNamedClassOrObjectSymbol = ktUsualClassType?.symbol as? KaNamedClassSymbol
        ktNamedClassOrObjectSymbol?.isInline ?: false
    }
}
