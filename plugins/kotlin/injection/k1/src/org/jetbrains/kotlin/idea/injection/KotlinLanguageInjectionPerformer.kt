// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.injection

import org.jetbrains.kotlin.idea.base.injection.KotlinLanguageInjectionPerformerBase
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.types.TypeUtils

internal class KotlinLanguageInjectionPerformer : KotlinLanguageInjectionPerformerBase() {
    override fun tryEvaluateConstant(expression: KtExpression?): String? = expression?.let { exp ->
        ConstantExpressionEvaluator.getConstant(exp, exp.analyze())
            ?.takeUnless { it.isError }
            ?.getValue(TypeUtils.NO_EXPECTED_TYPE) as? String
    }
}