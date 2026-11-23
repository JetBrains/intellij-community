// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete

import com.intellij.model.SymbolPointer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.modplan.ModPlanOption
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanQuestion
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.checkSuperMethods
import org.jetbrains.kotlin.idea.refactoring.rename.containingClassNameOf
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * The question of the interactive safe delete of a function or a property that overrides some: whether to delete the base declarations
 * instead. It shows the dialog of the legacy flow, and the answer is the key of the declaration in
 * [ModPlanKotlinSafeDeleteOptions.SUPER_DECLARATIONS_OF], chosen for a yes and not chosen for a no. A declaration is asked about on its
 * own, so the answer of one declaration does not change the answer of another.
 * [ModPlanKotlinSafeDeleteOptions.DELETE_SUPER_DECLARATIONS] is the general answer of a request or of the [fallback] form, and the
 * dialog does not set it.
 *
 * @param overridden the declarations that [declaration] overrides; the fallback form names their classes
 */
internal class KotlinSafeDeleteSuperDeclarationsQuestion(
    declaration: KtDeclaration,
    overridden: List<PsiElement>,
) : ModPlanQuestion.Custom {
    private val subject = SymbolPointer.of(declaration)
    private val key = declarationKey(declaration)
    private val fallback = ModPlanQuestion.Form.yesNo(
        ModPlanKotlinSafeDeleteOptions.DELETE_SUPER_DECLARATIONS,
        KotlinBundle.message("delete.the.base.declarations.in.0", overridden.joinToString { containingClassNameOf(it) }),
        KotlinBundle.message("delete.only.the.declaration.in.0", containingClassNameOf(declaration)),
    )

    override fun options(): List<ModPlanOption<*>> = listOf(
        ModPlanKotlinSafeDeleteOptions.DELETE_SUPER_DECLARATIONS,
        ModPlanKotlinSafeDeleteOptions.SUPER_DECLARATIONS_OF,
    )

    /**
     * @return whether the switch has an explicit value, or the keys answer this declaration, which an answer to this question adds
     */
    override fun isAnswered(currentValues: ModPlanOptionValues): Boolean =
        currentValues.has(ModPlanKotlinSafeDeleteOptions.DELETE_SUPER_DECLARATIONS) ||
                key in currentValues.answeredKeys(ModPlanKotlinSafeDeleteOptions.SUPER_DECLARATIONS_OF)

    /**
     * @return whether the base declarations of this declaration are deleted. An explicit false keeps them. Else they go if the chosen keys
     * hold this declaration; they stay if it was asked about and not chosen, or if the keys hold only other declarations; with no keys, the
     * switch decides, explicit or by default.
     */
    fun shouldDeleteSuperDeclarations(answers: ModPlanOptionValues): Boolean {
        val deleteOption = ModPlanKotlinSafeDeleteOptions.DELETE_SUPER_DECLARATIONS
        if (answers.has(deleteOption) && !answers.get(deleteOption)) return false
        val chosenKeys = answers.chosenKeys(ModPlanKotlinSafeDeleteOptions.SUPER_DECLARATIONS_OF)
        if (key in chosenKeys) return true
        if (key in answers.answeredKeys(ModPlanKotlinSafeDeleteOptions.SUPER_DECLARATIONS_OF)) return false
        if (chosenKeys.isNotEmpty()) return false
        return answers.get(deleteOption)
    }

    @RequiresEdt
    override fun ask(project: Project, currentValues: ModPlanOptionValues): ModPlanOptionValues? {
        val declaration = subject.element() as? KtDeclaration ?: return null
        val chosenDeclarations = checkSuperMethods(declaration, listOf(declaration), RefactoringBundle.message("to.refactor"))
        if (chosenDeclarations.isEmpty()) return null
        // As in the legacy flow, a base declaration that cannot be written, such as one of a library, ends it before anything is searched.
        if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, chosenDeclarations, true)) return null
        val chosenKeys = if (chosenDeclarations != listOf(declaration)) { listOf(key) } else { emptyList() }
        return currentValues.withAnswers(ModPlanKotlinSafeDeleteOptions.SUPER_DECLARATIONS_OF, listOf(key), chosenKeys)
    }

    /** The form of the general answer [ModPlanKotlinSafeDeleteOptions.DELETE_SUPER_DECLARATIONS]: it answers every declaration. */
    override fun fallback(): ModPlanQuestion.Form = fallback
}


/** A name that tells [declaration] from the other declarations of one request: its fully qualified name and the text of its parameters. */
internal fun declarationKey(declaration: KtDeclaration): String =
    (declaration.kotlinFqName?.asString() ?: declaration.name.orEmpty()) + ((declaration as? KtNamedFunction)?.valueParameterList?.text.orEmpty())
