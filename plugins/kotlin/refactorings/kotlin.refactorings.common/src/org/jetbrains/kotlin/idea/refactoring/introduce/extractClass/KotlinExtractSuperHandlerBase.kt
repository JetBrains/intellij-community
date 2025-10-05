// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.introduce.extractClass

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.extractSuperclass.ExtractSuperClassUtil
import com.intellij.refactoring.lang.ElementsHandler
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.chooseContainer.SeparateFileWrapper
import org.jetbrains.kotlin.idea.refactoring.chooseContainer.chooseContainerElementIfNecessary
import org.jetbrains.kotlin.idea.refactoring.getExtractionContainers
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.ui.KotlinExtractSuperDialogBase
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration

@ApiStatus.Internal
abstract class KotlinExtractSuperHandlerBase(private val isExtractInterface: Boolean) : RefactoringActionHandler, ElementsHandler {
    override fun isEnabledOnElements(elements: Array<out PsiElement>): Boolean = elements.singleOrNull() is KtClassOrObject

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return
        val klass = element.getNonStrictParentOfType<KtClassOrObject>() ?: return
        if (!checkClass(klass, editor)) return
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        selectElements(klass, editor)
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        if (dataContext == null) return
        val editor = CommonDataKeys.EDITOR.getData(dataContext)
        val klass = PsiTreeUtil.findCommonParent(*elements)?.getNonStrictParentOfType<KtClassOrObject>() ?: return
        if (!checkClass(klass, editor)) return
        selectElements(klass, editor)
    }

    private fun checkClass(klass: KtClassOrObject, editor: Editor?): Boolean {
        val project = klass.project

        if (!CommonRefactoringUtil.checkReadOnlyStatus(project, klass)) return false

        val errorMessage = getErrorMessage(klass)
        if (errorMessage != null) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                RefactoringBundle.getCannotRefactorMessage(errorMessage),
                KotlinExtractSuperclassHandler.REFACTORING_NAME,
                HelpID.EXTRACT_SUPERCLASS
            )
            return false
        }

        return true
    }

    private fun doInvoke(klass: KtClassOrObject, targetParent: PsiElement) {
        createDialog(klass, targetParent).show()
    }

    private fun selectElements(klass: KtClassOrObject, editor: Editor?) {
        val containers = klass.getExtractionContainers(strict = true, includeAll = true) + SeparateFileWrapper(klass.manager)

        if (editor == null) return doInvoke(klass, containers.first())

        chooseContainerElementIfNecessary(
            containers,
            editor,
            if (containers.first() is KtFile)
                KotlinBundle.message("text.select.target.file")
            else
                KotlinBundle.message("text.select.target.code.block.file"),
            true
        ) {
            doInvoke(klass, if (it is SeparateFileWrapper) klass.containingFile.parent!! else it)
        }
    }

    protected fun checkConflicts(originalClass: KtClassOrObject, dialog: KotlinExtractSuperDialogBase): Boolean {
        val conflicts = KotlinExtractSuperConflictSearcher.getInstance().collectConflicts(
            originalClass,
            dialog.selectedMembers,
            dialog.selectedTargetParent,
            dialog.extractedSuperName,
            isExtractInterface
        )
        return ExtractSuperClassUtil.showConflicts(dialog, conflicts, originalClass.project)
    }

    @NlsContexts.DialogMessage
    open fun getErrorMessage(klass: KtClassOrObject): String? = when {
        klass.isExpectDeclaration() -> KotlinBundle.message("error.text.extraction.from.expect.class.is.not.yet.supported")
        klass.toLightClass() == null -> KotlinBundle.message("error.text.extraction.from.non.jvm.class.is.not.yet.supported")
        else -> null
    }

    protected abstract fun createDialog(klass: KtClassOrObject, targetParent: PsiElement): KotlinExtractSuperDialogBase
}