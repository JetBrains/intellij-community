// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import com.intellij.util.ui.FormBuilder
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import java.awt.BorderLayout

const val TYPE_REFERENCE_VARIABLE_NAME: String = "TypeReferenceVariable";

abstract class AbstractKotlinInplaceIntroducer<D : KtNamedDeclaration>(
    localVariable: D?,
    expression: KtExpression?,
    occurrences: Array<KtExpression>,
    @Nls title: String,
    project: Project,
    editor: Editor
) : AbstractInplaceIntroducer<D, KtExpression>(project, editor, expression, localVariable, occurrences, title, KotlinFileType.INSTANCE) {
    protected fun initFormComponents(init: FormBuilder.() -> Unit) {
        myWholePanel.layout = BorderLayout()

        with(FormBuilder.createFormBuilder()) {
            init()
            myWholePanel.add(panel, BorderLayout.CENTER)
        }
    }

    protected fun runWriteCommandAndRestart(action: () -> Unit) {
        myEditor.putUserData(InplaceRefactoring.INTRODUCE_RESTART, true)
        try {
            stopIntroduce(myEditor)
            myProject.executeWriteCommand(commandName, commandName, action)
            // myExprMarker was invalidated by stopIntroduce()
            myExprMarker = myExpr?.let { createMarker(it) }
            startInplaceIntroduceTemplate()
        } finally {
            myEditor.putUserData(InplaceRefactoring.INTRODUCE_RESTART, false)
        }
    }

    protected fun updateVariableName() {
        if (localVariable == null) return
        val currentName = inputName.quoteIfNeeded()
        if (currentName.isIdentifier()) {
            localVariable.setName(currentName)
        }
    }

    override fun getActionName(): String? = null

    override fun restoreExpression(
        containingFile: PsiFile,
        declaration: D,
        marker: RangeMarker,
        exprText: String?
    ): KtExpression? {
        if (exprText == null || !declaration.isValid) return null

        val leaf = containingFile.findElementAt(marker.startOffset) ?: return null

        leaf.getParentOfTypeAndBranch<KtProperty> { nameIdentifier }?.let {
            return it.replaced(KtPsiFactory(myProject).createDeclaration(exprText))
        }

        val occurrenceExprText = (myExpr as? KtProperty)?.name?.quoteIfNeeded() ?: exprText
        return leaf
            .getNonStrictParentOfType<KtSimpleNameExpression>()
            ?.replaced(KtPsiFactory(myProject).createExpression(occurrenceExprText))
    }

    override fun updateTitle(declaration: D?) = updateTitle(declaration, null)

    override fun saveSettings(declaration: D) {

    }
}