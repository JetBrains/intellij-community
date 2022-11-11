// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolService
import com.intellij.openapi.project.Project
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.symbol.SymbolRenameTargetFactory
import org.jetbrains.kotlin.psi.*

internal class KotlinRenameTargetProvider : SymbolRenameTargetFactory {
    override fun renameTarget(project: Project, symbol: Symbol): RenameTarget? {
        val psiElement = PsiSymbolService.getInstance().extractElementFromSymbol(symbol)

        val declaration = when (psiElement) {
            is KtConstructor<*> -> psiElement.getContainingClassOrObject()
            is KtNamedDeclaration -> psiElement
            else -> null
        }

        if (declaration == null || !isRenameable(declaration)) return null

        return KotlinNamedDeclarationRenameUsage.create(declaration)
    }

    /**
     * Right now we want to restrict renaming to local declarations because we cannot properly
     * rename declarations in Java (yet). Those restrictions will be lifted eventually.
     */
    private fun isRenameable(psiElement: KtNamedDeclaration): Boolean = when {
        !psiElement.isWritable -> false

        psiElement is KtProperty && psiElement.isLocal -> true
        psiElement is KtFunction && psiElement.isLocal -> true
        psiElement is KtClass && psiElement.isLocal -> true

        psiElement is KtParameter -> parameterIsRenameable(psiElement)

        else -> false
    }

    private fun parameterIsRenameable(parameter: KtParameter): Boolean {
        val parentFunction = (parameter.parent as? KtParameterList)?.parent as? KtFunction ?: return false

        return parentFunction.isLocal || parentFunction is KtNamedFunction && parentFunction.isTopLevel
    }
}
