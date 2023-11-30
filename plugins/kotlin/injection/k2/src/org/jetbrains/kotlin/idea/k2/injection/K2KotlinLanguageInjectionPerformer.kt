// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.k2.injection

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.base.KtConstantValue
import org.jetbrains.kotlin.analysis.api.components.KtConstantEvaluationMode
import org.jetbrains.kotlin.idea.base.injection.KotlinLanguageInjectionPerformerBase
import org.jetbrains.kotlin.psi.KtExpression

internal class K2KotlinLanguageInjectionPerformer : KotlinLanguageInjectionPerformerBase() {
    override fun tryEvaluateConstant(expression: KtExpression?): String? = expression?.let { exp ->
        analyze(exp) {
            exp.evaluate(KtConstantEvaluationMode.CONSTANT_EXPRESSION_EVALUATION)?.takeUnless { it is KtConstantValue.KtErrorConstantValue }
                ?.renderAsKotlinConstant()
        }
    }
}