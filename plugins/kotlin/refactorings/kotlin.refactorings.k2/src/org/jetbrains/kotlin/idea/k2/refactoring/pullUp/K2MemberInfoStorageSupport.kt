// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pullUp

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfoStorageSupport
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration

internal class K2MemberInfoStorageSupport : KotlinMemberInfoStorageSupport {
    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun memberConflict(
        member1: KtNamedDeclaration,
        member: KtNamedDeclaration,
    ): Boolean = allowAnalysisOnEdt {
        analyze(member1) {
            val symbol1 = member1.symbol
            val symbol = member.symbol
            if (symbol1.name != symbol.name) return false

            when (symbol1) {
                is KaFunctionSymbol if symbol is KaFunctionSymbol -> {
                    // TODO: KT-74009
                    true
                }
                is KaPropertySymbol if symbol is KaPropertySymbol -> true
                is KaClassSymbol if symbol is KaClassSymbol -> true
                else -> false
            }
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun isInheritor(baseClass: PsiNamedElement, aClass: PsiNamedElement): Boolean = allowAnalysisOnEdt {
        val analyzableElement = aClass as? KtElement ?: baseClass as? KtElement
        if (analyzableElement == null) return@allowAnalysisOnEdt false

        analyze(analyzableElement) {
            val baseSymbol = getClassSymbol(baseClass) as? KaClassSymbol ?: return@analyze false
            val currentSymbol = getClassSymbol(aClass) as? KaClassSymbol ?: return@analyze false

            currentSymbol.isSubClassOf(baseSymbol)
        }
    }
}

internal fun KaSession.getClassSymbol(element: PsiNamedElement): KaDeclarationSymbol? {
    return when (element) {
        is PsiClass -> element.namedClassSymbol
        is KtNamedDeclaration -> element.symbol
        else -> null
    }
}
