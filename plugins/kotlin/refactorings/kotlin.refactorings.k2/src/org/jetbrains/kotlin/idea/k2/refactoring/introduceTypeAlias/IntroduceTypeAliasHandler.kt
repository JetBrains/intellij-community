// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.refactoring.introduceTypeAlias

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.actions.BasePlatformRefactoringAction
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.KotlinFirRefactoringSupportProvider
import org.jetbrains.kotlin.idea.k2.refactoring.introduceTypeAlias.ui.KotlinIntroduceTypeAliasDialog
import org.jetbrains.kotlin.idea.refactoring.checkConflictsInteractively
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.processDuplicates
import org.jetbrains.kotlin.idea.refactoring.introduce.selectElementsWithTargetSibling
import org.jetbrains.kotlin.idea.refactoring.introduce.showErrorHint
import org.jetbrains.kotlin.idea.util.ElementKind.TYPE_CONSTRUCTOR
import org.jetbrains.kotlin.idea.util.ElementKind.TYPE_ELEMENT
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

open class KotlinIntroduceTypeAliasHandler : RefactoringActionHandler {
    companion object {
        @JvmStatic
        val REFACTORING_NAME
            @Nls
            get() = KotlinBundle.message("name.introduce.type.alias")

        val INSTANCE = KotlinIntroduceTypeAliasHandler()
    }

    private fun selectElements(
        editor: Editor,
        file: KtFile,
        continuation: (elements: List<PsiElement>, targetSibling: PsiElement) -> Unit
    ) {
        selectElementsWithTargetSibling(
            REFACTORING_NAME,
            editor,
            file,
            KotlinBundle.message("title.select.target.code.block"),
            listOf(TYPE_ELEMENT, TYPE_CONSTRUCTOR),
            { null },
            { _, parent -> listOf(parent.containingFile) },
            continuation
        )
    }

    private fun runRefactoring(descriptor: IntroduceTypeAliasDescriptor, project: Project, editor: Editor) {
        val typeAlias = project.executeWriteCommand<KtTypeAlias>(REFACTORING_NAME) { descriptor.generateTypeAlias() }

        val duplicateReplacers = analyzeInModalWindow(typeAlias, KotlinBundle.message("text.process.duplicates")) {
            findDuplicates(typeAlias)
        }
        if (duplicateReplacers.isNotEmpty()) {
            processDuplicates(duplicateReplacers, project, editor)
        }
    }

    open fun doInvoke(
        project: Project,
        editor: Editor,
        elements: List<PsiElement>,
        targetSibling: KtElement,
        descriptorSubstitutor: ((IntroduceTypeAliasDescriptor) -> IntroduceTypeAliasDescriptor)? = null
    ) {
        val elementToExtract = elements.singleOrNull()

        val errorMessage = when (elementToExtract) {
            is KtSimpleNameExpression -> {
                if (!(isTypeConstructorReference(elementToExtract) || isDoubleColonReceiver(elementToExtract))) KotlinBundle.message("error.text.type.reference.is.expected"
                ) else null
            }
            !is KtTypeElement -> KotlinBundle.message("error.text.no.type.to.refactor")
            else -> null
        }
        if (errorMessage != null) return showErrorHint(project, editor, errorMessage, REFACTORING_NAME)

        val introduceData = when (elementToExtract) {
            is KtTypeElement -> IntroduceTypeAliasData(elementToExtract, targetSibling)
            else -> IntroduceTypeAliasData(
                elementToExtract!!.getStrictParentOfType<KtTypeElement>() ?: elementToExtract as KtElement,
                targetSibling,
                true
            )
        }
        when (val analysisResult = analyzeResult(introduceData)) {
            is IntroduceTypeAliasAnalysisResult.Error -> {
                return showErrorHint(project, editor, analysisResult.message, REFACTORING_NAME)
            }

            is IntroduceTypeAliasAnalysisResult.Success -> {
                val originalDescriptor = analysisResult.descriptor
                if (isUnitTestMode()) {
                    val (descriptor, conflicts) = descriptorSubstitutor!!(originalDescriptor).validate()
                    project.checkConflictsInteractively(conflicts) { runRefactoring(descriptor, project, editor) }
                } else {
                    KotlinIntroduceTypeAliasDialog(project, originalDescriptor) {
                        runRefactoring(
                            it.currentDescriptor,
                            project,
                            editor
                        )
                    }.show()
                }
            }
        }
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        if (file !is KtFile) return

        val offset = if (editor.selectionModel.hasSelection()) editor.selectionModel.selectionStart else editor.caretModel.offset

        val refExpression = file.findElementAt(offset)?.getNonStrictParentOfType<KtSimpleNameExpression>()
        if (refExpression != null && isDoubleColonReceiver(refExpression)) {
            return doInvoke(project, editor, listOf(refExpression), refExpression.getOutermostParentContainedIn(file) as KtElement)
        }

        selectElements(editor, file) { elements, targetSibling -> doInvoke(project, editor, elements, targetSibling as KtElement) }
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        throw AssertionError("$REFACTORING_NAME can only be invoked from editor")
    }
}

class IntroduceTypeAliasAction : BasePlatformRefactoringAction() {
    override fun getRefactoringHandler(provider: RefactoringSupportProvider): RefactoringActionHandler? {
        return if (provider is KotlinFirRefactoringSupportProvider) KotlinIntroduceTypeAliasHandler.INSTANCE else null
    }

    override fun isAvailableInEditorOnly(): Boolean {
        return true
    }

    override fun isAvailableOnElementInEditorAndFile(element: PsiElement, editor: Editor, file: PsiFile, context: DataContext): Boolean {
        return super.isAvailableOnElementInEditorAndFile(element, editor, file, context) &&
                (ModuleUtil.findModuleForPsiElement(file)?.languageVersionSettings?.supportsFeature(LanguageFeature.TypeAliases) ?: false)
    }
}