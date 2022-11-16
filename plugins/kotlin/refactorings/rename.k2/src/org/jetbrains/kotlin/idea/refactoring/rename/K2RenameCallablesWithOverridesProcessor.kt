// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport
import org.jetbrains.kotlin.idea.refactoring.KotlinK2RefactoringsBundle
import org.jetbrains.kotlin.idea.searching.inheritors.findAllOverridings
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

/**
 * That processor has two main purposes:
 * - Jump from the starting declaration to its deepest super declaration
 * - Collect all overrides of the declaration to also rename them
 *
 * IMPORTANT: This processor searches for all overrides in the project, and
 * [com.intellij.refactoring.rename.RenameJavaMethodProcessor.prepareRenaming] does that as well.
 * However, we have to do the search ourselves in any case, because
 * [com.intellij.refactoring.rename.RenameJavaMethodProcessor] will not kick in at
 * all if the rename process starts at Kotlin element.
 */
internal class K2RenameCallablesWithOverridesProcessor : RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement): Boolean {
        val unwrapped = element.unwrapped as? KtCallableDeclaration
        return unwrapped is KtNamedFunction || unwrapped is KtParameter || unwrapped is KtProperty
    }

    override fun substituteElementToRename(element: PsiElement, editor: Editor?): PsiElement? {
        val kotlinElement = element.unwrapped as? KtCallableDeclaration ?: return null

        val elementToRename = selectElementToRename(element, kotlinElement)

        // TODO: handle situation with intersection overrides (more than one deepest super declaration)
        return elementToRename.lastOrNull()
    }

    /**
     * User has to choose whether he wants to rename only current method
     * or the base method and the whole hierarchy.
     */
    private fun selectElementToRename(
        element: PsiElement,
        kotlinElement: KtCallableDeclaration
    ): List<PsiElement> {
        return KotlinFindUsagesSupport.getInstance(element.project).checkSuperMethods(
            kotlinElement,
            ignore = emptyList(),
            actionString = KotlinBundle.message("text.rename.as.part.of.phrase")
        )
    }

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>, scope: SearchScope) {
        val kotlinElement = element.unwrapped as? KtCallableDeclaration ?: return

        val allOverrides = collectAllOverrides(kotlinElement)
        allRenames += allOverrides.associateWith { newName }
    }

    private fun collectAllOverrides(element: KtCallableDeclaration): Set<PsiElement> =
        runProcessWithProgressSynchronously(
            KotlinK2RefactoringsBundle.message("rename.searching.for.all.overrides"),
            canBeCancelled = true,
            element.project
        ) {
            runReadAction {
                element.findAllOverridings().toSet()
            }
        }

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun renameElement(element: PsiElement, newName: String, usages: Array<out UsageInfo>, listener: RefactoringElementListener?) {
        val kotlinElement = element.unwrapped as? KtCallableDeclaration ?: return

        super.renameElement(kotlinElement, newName, usages, listener)

        // analysis is needed to check the overrides
        allowAnalysisOnEdt {
            dropOverrideKeywordIfNecessary(kotlinElement)
        }
    }

    private fun dropOverrideKeywordIfNecessary(declaration: KtCallableDeclaration) {
        if (declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD) && declaration.overridesNothing()) {
            declaration.removeModifier(KtTokens.OVERRIDE_KEYWORD)
        }
    }

    private fun KtCallableDeclaration.overridesNothing(): Boolean {
        val declaration = this

        analyze(this) {
            val declarationSymbol = declaration.getSymbol() as? KtCallableSymbol ?: return false

            return declarationSymbol.getDirectlyOverriddenSymbols().isEmpty()
        }
    }
}


/**
 * A utility function to call [ProgressManager.runProcessWithProgressSynchronously] more conveniently.
 */
private inline fun <T> runProcessWithProgressSynchronously(
    @NlsSafe @NlsContexts.DialogTitle progressTitle: String,
    canBeCancelled: Boolean,
    project: Project,
    crossinline action: () -> T
): T = ProgressManager.getInstance().runProcessWithProgressSynchronously(
    ThrowableComputable { action() },
    progressTitle,
    canBeCancelled,
    project
)
