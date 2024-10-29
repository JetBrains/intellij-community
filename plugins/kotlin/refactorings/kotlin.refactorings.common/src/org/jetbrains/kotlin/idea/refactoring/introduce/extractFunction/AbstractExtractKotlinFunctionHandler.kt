// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.getExtractionContainers
import org.jetbrains.kotlin.idea.refactoring.introduce.selectElementsWithTargetSibling
import org.jetbrains.kotlin.idea.refactoring.introduce.validateExpressionElements
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.psi.KtFile


abstract class AbstractExtractKotlinFunctionHandler(
    private val allContainersEnabled: Boolean = false,
    private val acceptScripts: Boolean = false
) : RefactoringActionHandler {
    abstract fun doInvoke(
        editor: Editor,
        file: KtFile,
        elements: List<PsiElement>,
        targetSibling: PsiElement
    )

    abstract fun restart(templateState: TemplateState, file: KtFile, restartInplace: Boolean): Boolean

    fun selectElements(editor: Editor, file: KtFile, continuation: (elements: List<PsiElement>, targetSibling: PsiElement) -> Unit) {
        val selection: ((elements: List<PsiElement>, commonParent: PsiElement) -> PsiElement?)? = if (allContainersEnabled) {
            { elements, parent -> parent.getExtractionContainers(elements.size == 1, false, acceptScripts).firstOrNull() }
        } else null
        selectElementsWithTargetSibling(
            EXTRACT_FUNCTION,
            editor,
            file,
            KotlinBundle.message("title.select.target.code.block"),
            listOf(ElementKind.EXPRESSION),
            ::validateExpressionElements,
            { elements, parent -> parent.getExtractionContainers(elements.size == 1, allContainersEnabled, acceptScripts) },
            continuation,
            selection
        )
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        if (file !is KtFile) return

        val activeTemplateState = TemplateManagerImpl.getTemplateState(editor)
        if (activeTemplateState != null && restart(activeTemplateState, file, false)) {
            return
        }

        selectElements(editor, file) { elements, targetSibling -> doInvoke(editor, file, elements, targetSibling) }
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        throw AssertionError("Extract Function can only be invoked from editor")
    }
}

val EXTRACT_FUNCTION: String
    @Nls
    get() = KotlinBundle.message("extract.function")
