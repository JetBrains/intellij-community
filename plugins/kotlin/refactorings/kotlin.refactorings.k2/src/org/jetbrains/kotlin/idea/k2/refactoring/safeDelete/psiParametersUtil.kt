// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete

import com.intellij.ide.IdeBundle
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.util.RefactoringDescriptionLocation
import org.jetbrains.kotlin.idea.searching.inheritors.findHierarchyWithSiblings
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex

internal fun getParametersToSearch(element: KtParameter): List<PsiElement> {
    val elements = checkParametersInMethodHierarchy(element) ?: return listOf(element)
    return elements.toList()
}

private fun checkParametersInMethodHierarchy(parameter: KtParameter): Collection<PsiElement>? {
    val method = parameter.ownerFunction ?: return null
    val parametersToDelete = collectParameterHierarchy( parameter)
    if (parametersToDelete.size <= 1 || isUnitTestMode()) return parametersToDelete

    val message = JavaRefactoringBundle.message(
        "0.is.a.part.of.method.hierarchy.do.you.want.to.delete.multiple.parameters",
        ElementDescriptionUtil.getElementDescription(method, RefactoringDescriptionLocation.WITHOUT_PARENT)
    )
    val exitCode =
        Messages.showOkCancelDialog(parameter.project, message, IdeBundle.message("title.warning"), Messages.getQuestionIcon())
    return if (exitCode == Messages.OK) parametersToDelete else null
}

private fun collectParameterHierarchy(parameter: KtParameter): Set<PsiElement> {
    val function = parameter.ownerFunction as? KtFunction ?: return emptySet()
    val parameterIndex = parameter.parameterIndex()
    val parametersToDelete = HashSet<PsiElement>()
    val processElement: (PsiElement) -> Unit = {
        when (it) {
            is KtFunction -> {
                parametersToDelete.add(it.valueParameters[parameterIndex])
            }

            is PsiMethod -> {
                parametersToDelete.add(it.parameterList.parameters[parameterIndex])
            }
        }
    }
    processElement(function)
    function.findHierarchyWithSiblings().forEach(processElement)
    return parametersToDelete
}