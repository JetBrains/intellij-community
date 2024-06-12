// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.k2.postProcessings

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.SmartcastImpossible
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.UselessCast
import org.jetbrains.kotlin.idea.base.psi.isNullExpression
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.TypeMismatchFactories
import org.jetbrains.kotlin.idea.quickfix.RemoveUselessCastFix
import org.jetbrains.kotlin.j2k.k2.K2AddExclExclDiagnosticBasedProcessing
import org.jetbrains.kotlin.j2k.k2.K2CustomDiagnosticBasedProcessing
import org.jetbrains.kotlin.j2k.k2.K2DiagnosticFix
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS

internal val smartcastImpossibleProcessing: K2AddExclExclDiagnosticBasedProcessing<SmartcastImpossible> =
    K2AddExclExclDiagnosticBasedProcessing(
        SmartcastImpossible::class,
        TypeMismatchFactories.smartcastImpossibleFactory
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