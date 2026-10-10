// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete

import com.intellij.ide.IdeBundle
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.ui.Messages
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.modplan.impl.ModPlanSubjectConfirmation
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions
import com.intellij.refactoring.util.RefactoringDescriptionLocation
import org.jetbrains.kotlin.idea.searching.inheritors.findHierarchyWithSiblings
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex

/** @return [element] and the same parameter of the other declarations of its hierarchy */
internal fun parameterHierarchy(element: KtParameter): List<PsiElement> {
    if (element.ownerDeclaration == null) return listOf(element)
    return collectParameterHierarchy(element).toList()
}

/**
 * @return the question of the legacy flow whether to delete [parameter] in the whole hierarchy; its key is the [declarationKey] of the
 * owner and the name of the parameter
 */
internal fun parameterHierarchyQuestion(parameter: KtParameter): ModPlanSubjectConfirmation {
    val message = JavaRefactoringBundle.message(
        "0.is.a.part.of.method.hierarchy.do.you.want.to.delete.multiple.parameters",
        parameter.ownerDeclaration?.let { ElementDescriptionUtil.getElementDescription(it, RefactoringDescriptionLocation.WITHOUT_PARENT) }
    )
    val key = parameter.ownerDeclaration?.let { "${declarationKey(it)} ${parameter.name}" }
    return ModPlanSubjectConfirmation(
        ModPlanSafeDeleteOptions.DELETE_HIERARCHY, ModPlanSafeDeleteOptions.HIERARCHY_OF, key
    ) { project ->
        Messages.showOkCancelDialog(project, message, IdeBundle.message("title.warning"), Messages.getQuestionIcon()) == Messages.OK
    }
}

private fun collectParameterHierarchy(parameter: KtParameter): Set<PsiElement> {
    val function = parameter.ownerDeclaration as? KtCallableDeclaration ?: return emptySet()
    val parameterIndex = parameter.parameterIndex()
    val parametersToDelete = HashSet<PsiElement>()
    val processElement: (PsiElement) -> Unit = { owner ->

        fun rememberContextParameter(owner: KtCallableDeclaration) {
          owner.contextParameters[parameterIndex]?.let { param ->
                parametersToDelete.add(param)
            }
        }

        when (owner) {
            is KtFunction -> {
                if (parameter.isContextParameter) {
                    rememberContextParameter(owner)
                } else {
                    parametersToDelete.add(owner.valueParameters[parameterIndex])
                }
            }

            is KtProperty -> {
                if (parameter.isContextParameter) {
                    rememberContextParameter(owner)
                }
            }

            is PsiMethod -> {
                parametersToDelete.add(owner.parameterList.parameters[parameterIndex])
            }
        }
    }
    processElement(function)
    function.findHierarchyWithSiblings().forEach(processElement)
    return parametersToDelete
}