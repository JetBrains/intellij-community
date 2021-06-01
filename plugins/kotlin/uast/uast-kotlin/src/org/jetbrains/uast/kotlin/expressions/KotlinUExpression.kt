// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiType
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.constants.UnsignedErrorValueTypeConstant
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.uast.UExpression

interface KotlinUElementWithType : UExpression {
    override fun getExpressionType(): PsiType? {
        val ktElement = sourcePsi as? KtExpression ?: return null
        val ktType = ktElement.analyze()[BindingContext.EXPRESSION_TYPE_INFO, ktElement]?.type ?: return null
        return ktType.toPsiType(this, ktElement, boxed = false)
    }
}

interface KotlinEvaluatableUElement : UExpression {
    override fun evaluate(): Any? {
        val ktElement = sourcePsi as? KtExpression ?: return null
        
        val compileTimeConst = ktElement.analyze()[BindingContext.COMPILE_TIME_VALUE, ktElement]
        if (compileTimeConst is UnsignedErrorValueTypeConstant) return null

        return compileTimeConst?.getValue(TypeUtils.NO_EXPECTED_TYPE)
    }
}