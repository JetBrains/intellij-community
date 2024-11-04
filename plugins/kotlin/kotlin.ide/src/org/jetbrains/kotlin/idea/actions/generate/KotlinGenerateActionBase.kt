// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.actions.generate

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.actions.CodeInsightAction
import com.intellij.lang.ContextAwareActionHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindMatcher
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

abstract class KotlinGenerateActionBase : CodeInsightAction(), CodeInsightActionHandler {
    override fun update(
        presentation: Presentation,
        project: Project,
        editor: Editor,
        file: PsiFile,
        dataContext: DataContext,
        actionPlace: String?
    ) {
        super.update(presentation, project, editor, file, dataContext, actionPlace)
        val actionHandler = getHandler(dataContext)
        if (actionHandler is ContextAwareActionHandler && presentation.isEnabled) {
            presentation.isEnabled = actionHandler.isAvailableForQuickList(editor, file, dataContext)
        }
    }

    override fun isValidForFile(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (file !is KtFile || file.isCompiled) return false

        val targetClass = getTargetClass(editor, file) ?: return false
        if (!targetClass.isValid) return false
        val filter = RootKindFilter.projectSources.copy(includeScriptsOutsideSourceRoots = true)
        return RootKindMatcher.matches(targetClass, filter) && isValidForClass(targetClass)
    }

    protected open fun getTargetClass(editor: Editor, file: PsiFile): KtClassOrObject? {
        val offset = editor.caretModel.offset
        return (file.findElementAt(offset)?.takeUnless { it is PsiWhiteSpace } ?: if (offset > 0) file.findElementAt(offset - 1) else null)?.getNonStrictParentOfType<KtClassOrObject>()
    }

    protected abstract fun isValidForClass(targetClass: KtClassOrObject): Boolean

    override fun startInWriteAction(): Boolean = false

    override fun getHandler(): KotlinGenerateActionBase = this
}