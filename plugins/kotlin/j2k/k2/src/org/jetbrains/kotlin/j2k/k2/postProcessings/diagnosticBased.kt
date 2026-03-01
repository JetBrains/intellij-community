// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.k2.postProcessings

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ArgumentTypeMismatch
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.AssignmentTypeMismatch
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.InitializerTypeMismatch
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.IteratorOnNullable
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ReturnTypeMismatch
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.SmartcastImpossible
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.UnnecessaryNotNullAssertion
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.UnsafeCall
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.UnsafeInfixCall
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.UnsafeOperatorCall
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.UselessCast
import org.jetbrains.kotlin.idea.base.psi.isNullExpression
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExclExclCallFixFactories
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.TypeMismatchFactories
import org.jetbrains.kotlin.idea.quickfix.RemoveExclExclCallFix
import org.jetbrains.kotlin.idea.quickfix.RemoveUselessCastFix
import org.jetbrains.kotlin.j2k.k2.K2AddExclExclDiagnosticBasedProcessing
import org.jetbrains.kotlin.j2k.k2.K2CustomDiagnosticBasedProcessing
import org.jetbrains.kotlin.j2k.k2.K2DiagnosticFix
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

internal val argumentTypeMismatchProcessing: K2AddExclExclDiagnosticBasedProcessing<ArgumentTypeMismatch> =
    K2AddExclExclDiagnosticBasedProcessing(
        ArgumentTypeMismatch::class,
        TypeMismatchFactories.argumentTypeMismatchFactory
    )

internal val returnTypeMismatchProcessing: K2AddExclExclDiagnosticBasedProcessing<ReturnTypeMismatch> =
    K2AddExclExclDiagnosticBasedProcessing(
        ReturnTypeMismatch::class,
        TypeMismatchFactories.returnTypeMismatchFactory
    )

internal val assignmentTypeMismatchProcessing: K2AddExclExclDiagnosticBasedProcessing<AssignmentTypeMismatch> =
    K2AddExclExclDiagnosticBasedProcessing(
        AssignmentTypeMismatch::class,
        TypeMismatchFactories.assignmentTypeMismatch
    )

internal val initializerTypeMismatchProcessing: K2AddExclExclDiagnosticBasedProcessing<InitializerTypeMismatch> =
    K2AddExclExclDiagnosticBasedProcessing(
        InitializerTypeMismatch::class,
        TypeMismatchFactories.initializerTypeMismatch
    )

internal val smartcastImpossibleProcessing: K2AddExclExclDiagnosticBasedProcessing<SmartcastImpossible> =
    K2AddExclExclDiagnosticBasedProcessing(
        SmartcastImpossible::class,
        TypeMismatchFactories.smartcastImpossibleFactory
    )

internal val unsafeCallProcessing: K2AddExclExclDiagnosticBasedProcessing<UnsafeCall> =
    K2AddExclExclDiagnosticBasedProcessing(
        UnsafeCall::class,
        AddExclExclCallFixFactories.unsafeCallFactory
    )

internal val unsafeInfixCallProcessing: K2AddExclExclDiagnosticBasedProcessing<UnsafeInfixCall> =
    K2AddExclExclDiagnosticBasedProcessing(
        UnsafeInfixCall::class,
        AddExclExclCallFixFactories.unsafeInfixCallFactory
    )

internal val unsafeOperatorCallProcessing: K2AddExclExclDiagnosticBasedProcessing<UnsafeOperatorCall> =
    K2AddExclExclDiagnosticBasedProcessing(
        UnsafeOperatorCall::class,
        AddExclExclCallFixFactories.unsafeOperatorCallFactory
    )

internal val iteratorOnNullableProcessing: K2AddExclExclDiagnosticBasedProcessing<IteratorOnNullable> =
    K2AddExclExclDiagnosticBasedProcessing(
        IteratorOnNullable::class,
        AddExclExclCallFixFactories.iteratorOnNullableFactory
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

internal val unnecessaryNotNullAssertionProcessing: K2CustomDiagnosticBasedProcessing<UnnecessaryNotNullAssertion> =
    K2CustomDiagnosticBasedProcessing(UnnecessaryNotNullAssertion::class) {
        object : K2DiagnosticFix {
            override fun apply(element: PsiElement) {
                val postfixExpression = element.getNonStrictParentOfType<KtPostfixExpression>() ?: return
                RemoveExclExclCallFix.invoke(postfixExpression)
            }
        }
    }