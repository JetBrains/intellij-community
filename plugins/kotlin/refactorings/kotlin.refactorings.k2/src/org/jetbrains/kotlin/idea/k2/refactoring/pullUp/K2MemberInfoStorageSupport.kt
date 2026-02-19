// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pullUp

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfoStorageSupport
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration

internal class K2MemberInfoStorageSupport : KotlinMemberInfoStorageSupport {
    @OptIn(KaAllowAnalysisOnEdt::class, KaPlatformInterface::class)
    override fun memberConflict(
        member1: KtNamedDeclaration,
        member2: KtNamedDeclaration,
    ): Boolean = allowAnalysisOnEdt {
        // If they have different names, then there is no conflict
        if (member1.name != member2.name) {
            return false
        }

        // This logic is copied from `org.jetbrains.kotlin.resolve.OverloadChecker.isOverloadable`
        val member1Category = member1.getDeclarationCategory()
        val member2Category = member2.getDeclarationCategory()
        when {
            // Members have non-conflicting categories
            member1Category != member2Category ||
                    member1Category == DeclarationCategory.OTHER -> return false
            // Members have the same category, but they are not functions
            member1Category != DeclarationCategory.FUNCTION -> return true
        }

        val symbol1Pointer = analyze(member1) {
            val member1Symbol = member1.symbol as? KaFunctionSymbol ?: return true
            member1Symbol.createPointer()
        }

        val symbol2Pointer = analyze(member2) {
            val member2Symbol = member2.symbol as? KaFunctionSymbol ?: return true
            member2Symbol.createPointer()
        }

        // Checking if `member2` is analyzable from the module of `member1`
        analyze(member1) {
            val member1Symbol = symbol1Pointer.restoreSymbol() ?: return@analyze
            val member2Symbol = symbol2Pointer.restoreSymbol() ?: return@analyze

            // Checking conflicts using platform-specific checks for the `member1` module
            return member1Symbol.hasConflictingSignatureWith(member2Symbol, member1Symbol.containingModule.targetPlatform)
        }

        // Checking if `member1` is analyzable from the module of `member2`
        analyze(member2) {
            val member1Symbol = symbol1Pointer.restoreSymbol() ?: return@analyze
            val member2Symbol = symbol2Pointer.restoreSymbol() ?: return@analyze

            // Checking conflicts using platform-specific checks for the `member2` module
            return member1Symbol.hasConflictingSignatureWith(member2Symbol, member2Symbol.containingModule.targetPlatform)
        }

        // Symbols are from unrelated modules, so it's impossible to check for conflicts
        return false
    }

    private enum class DeclarationCategory {
        TYPE_OR_VALUE,
        FUNCTION,
        EXTENSION_PROPERTY,
        OTHER
    }

    private fun KtNamedDeclaration.getDeclarationCategory(): DeclarationCategory {
        return when (this) {
            is KtProperty ->
                if (this.isExtensionDeclaration())
                    DeclarationCategory.EXTENSION_PROPERTY
                else
                    DeclarationCategory.TYPE_OR_VALUE

            is KtNamedFunction ->
                DeclarationCategory.FUNCTION

            is KtClassLikeDeclaration ->
                DeclarationCategory.TYPE_OR_VALUE

            else ->
                DeclarationCategory.OTHER
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
