// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.postProcessings

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic.SmartcastImpossible
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic.UselessCast
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.idea.base.psi.isNullExpression
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
import org.jetbrains.kotlin.idea.quickfix.RemoveUselessCastFix
import org.jetbrains.kotlin.j2k.K2CustomDiagnosticBasedProcessing
import org.jetbrains.kotlin.j2k.K2DiagnosticFix
import org.jetbrains.kotlin.j2k.K2QuickFixDiagnosticBasedProcessing
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

// Adapted from org.jetbrains.kotlin.idea.k2.codeinsight.fixes.TypeMismatchFactories.getSmartcastImpossibleFactory
// to avoid dependency on v2 module
private val smartcastImpossibleFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: SmartcastImpossible ->
    val psi = diagnostic.psi
    val actualType = psi.getKtType() ?: return@IntentionBased emptyList()
    val expectedType = diagnostic.desiredType

    if (expectedType.canBeNull || !actualType.canBeNull) return@IntentionBased emptyList()
    if (psi.safeAs<KtExpression>()?.isDefinitelyNull() == true) {
        return@IntentionBased emptyList()
    }
    val nullableExpectedType = expectedType.withNullability(KtTypeNullability.NULLABLE)
    if (actualType isSubTypeOf nullableExpectedType) {
        return@IntentionBased listOf(AddExclExclCallFix(psi))
    }
    return@IntentionBased emptyList()
}

internal val smartcastImpossibleProcessing: K2QuickFixDiagnosticBasedProcessing<SmartcastImpossible> =
    K2QuickFixDiagnosticBasedProcessing(
        SmartcastImpossible::class,
        smartcastImpossibleFactory
    )

internal val uselessCastProcessing: K2CustomDiagnosticBasedProcessing<UselessCast> =
    K2CustomDiagnosticBasedProcessing(UselessCast::class) { diagnostic: UselessCast ->
        if (diagnostic.psi.left.isNullExpression()) return@K2CustomDiagnosticBasedProcessing null

        object : K2DiagnosticFix {
            override fun apply(element: PsiElement) {
                if (element !is KtBinaryExpressionWithTypeRHS) return
                RemoveUselessCastFix.invoke(element)
            }
        }
    }