// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.usageView.UsageInfo
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
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

@OptIn(KaExperimentalApi::class)
private fun KtNamedFunction.getOverloads(): Collection<KtNamedFunction> {
    val name = nameAsName ?: return emptyList()
    @OptIn(KaAllowAnalysisOnEdt::class)
    allowAnalysisOnEdt {
        analyze(this) {
            val symbol = symbol as? KaNamedFunctionSymbol ?: return emptyList()
            if (symbol.isActual && symbol.getExpectsForActual().isNotEmpty()) return emptyList()
            val result = LinkedHashSet<KtNamedFunction>()
            listOfNotNull(
                findPackage(containingKtFile.packageFqName)?.packageScope,
                (symbol.containingDeclaration as? KaClassSymbol)?.declaredMemberScope,
                symbol.receiverParameter?.returnType?.expandedSymbol?.declaredMemberScope,
            ).flatMapTo(result) { scope ->
                scope.callables(name).mapNotNull {
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