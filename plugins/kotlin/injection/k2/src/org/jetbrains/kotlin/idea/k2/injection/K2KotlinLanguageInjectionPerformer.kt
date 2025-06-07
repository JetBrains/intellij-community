// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.injection

import com.intellij.openapi.util.text.StringUtilRt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.injection.KotlinLanguageInjectionPerformerBase
import org.jetbrains.kotlin.psi.KtExpression

internal class K2KotlinLanguageInjectionPerformer : KotlinLanguageInjectionPerformerBase() {
    @OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class)
    override fun tryEvaluateConstant(expression: KtExpression?): String? = expression?.let { exp ->
        allowAnalysisOnEdt { // in refactorings this code might run in a write action on EDT
            allowAnalysisFromWriteAction {
                analyze(exp) {
                    val value = exp.evaluate()?.takeUnless { it is KaConstantValue.ErrorValue }
                        ?.render()
                    if (value != null) StringUtilRt.unquoteString(value) else null
                }
            }
        }
    }
}