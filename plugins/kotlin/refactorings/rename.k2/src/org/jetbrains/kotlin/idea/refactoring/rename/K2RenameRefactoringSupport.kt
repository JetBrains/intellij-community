// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

internal class K2RenameRefactoringSupport : KotlinRenameRefactoringSupport {
    override fun processForeignUsages(
        element: PsiElement,
        newName: String,
        usages: Array<UsageInfo>,
        fallbackHandler: (UsageInfo) -> Unit
    ) {
        usages.forEach(fallbackHandler)
    }

    override fun prepareForeignUsagesRenaming(
        element: PsiElement,
        newName: String,
        allRenames: MutableMap<PsiElement, String>,
        scope: SearchScope
    ) {}

    override fun checkRedeclarations(declaration: KtNamedDeclaration, newName: String, result: MutableList<UsageInfo>) {
        // TODO
    }

    override fun checkOriginalUsagesRetargeting(
        declaration: KtNamedDeclaration,
        newName: String,
        originalUsages: MutableList<UsageInfo>,
        newUsages: MutableList<UsageInfo>
    ) {
        // TODO
    }

    override fun checkNewNameUsagesRetargeting(declaration: KtNamedDeclaration, newName: String, newUsages: MutableList<UsageInfo>) {
        // TODO
    }

    override fun checkAccidentalPropertyOverrides(declaration: KtNamedDeclaration, newName: String, result: MutableList<UsageInfo>) {
        // TODO
    }

    override fun getAllOverridenFunctions(function: KtNamedFunction): List<PsiElement> {
        return analyze(function) {
            val overridenFunctions = (function.getSymbol() as? KtCallableSymbol)?.getAllOverriddenSymbols().orEmpty()
            overridenFunctions.mapNotNull { it.psi as? KtNamedFunction }
        }
    }
}