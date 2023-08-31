// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.usageView.UsageInfo
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.UserDataProperty

class AutomaticOverloadsRenamer(function: KtNamedFunction, newName: String) : AutomaticRenamer() {
    companion object {
        @get:TestOnly
        @set:TestOnly
        var PsiElement.elementFilter: ((PsiElement) -> Boolean)? by UserDataProperty(Key.create("ELEMENT_FILTER"))
    }

    init {
        val filter = function.elementFilter
        function.getOverloads().mapNotNullTo(myElements) { candidate ->
            if (filter != null && !filter(candidate)) return@mapNotNullTo null
            if (candidate != function) candidate else null
        }
        suggestAllNames(function.name, newName)
    }

    override fun getDialogTitle() = KotlinBundle.message("text.rename.overloads.title")
    override fun getDialogDescription() = KotlinBundle.message("title.rename.overloads.to")
    override fun entityName() = KotlinBundle.message("text.overload")
    override fun isSelectedByDefault(): Boolean = true
}

private fun KtNamedFunction.getOverloads(): Collection<KtNamedFunction> {
    val name = nameAsName ?: return emptyList()
    @OptIn(KtAllowAnalysisOnEdt::class)
    allowAnalysisOnEdt {
        analyze(this) {
            val symbol = getSymbol() as? KtFunctionSymbol  ?: return emptyList()
            if (symbol.isActual && symbol.getExpectsForActual().isNotEmpty()) return emptyList()
            val result = LinkedHashSet<KtNamedFunction>()
            listOfNotNull(
                getPackageSymbolIfPackageExists(containingKtFile.packageFqName)?.getPackageScope(),
                (symbol.getContainingSymbol() as? KtClassOrObjectSymbol)?.getDeclaredMemberScope(),
                symbol.receiverParameter?.type?.expandedClassSymbol?.getDeclaredMemberScope()
            ).flatMapTo(result) { scope ->
                scope.getCallableSymbols(name).mapNotNull {
                    it.psi as? KtNamedFunction
                }
            }
            return result
        }
    }
}

class AutomaticOverloadsRenamerFactory : AutomaticRenamerFactory {
    override fun isApplicable(element: PsiElement): Boolean {
        if (element !is KtNamedFunction) return false
        if (element.isLocal) return false
        return element.getOverloads().size > 1
    }

    override fun getOptionName() = JavaRefactoringBundle.message("rename.overloads")

    override fun isEnabled() = KotlinCommonRefactoringSettings.getInstance().renameOverloads

    override fun setEnabled(enabled: Boolean) {
        KotlinCommonRefactoringSettings.getInstance().renameOverloads = enabled
    }

    override fun createRenamer(element: PsiElement, newName: String, usages: Collection<UsageInfo>) =
        AutomaticOverloadsRenamer(element as KtNamedFunction, newName)
}