// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiReference
import com.intellij.refactoring.HelpID
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.findSimpleNameReference
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtWhenExpression

abstract class AbstractKotlinInlinePropertyHandler(protected val withPrompt: Boolean = true) : KotlinInlineActionHandler() {
    override val helpId: String get() = HelpID.INLINE_VARIABLE
    override val refactoringName: String get() = KotlinBundle.message("title.inline.property")
    override fun canInlineKotlinElement(element: KtElement): Boolean = element is KtProperty && element.name != null
    override fun inlineKotlinElement(project: Project, editor: Editor?, element: KtElement) {
        val declaration = element as KtProperty
        if (!checkSources(project, editor, element)) return

        if (!element.hasBody()) {
            val message = when {
                element.isAbstract() -> KotlinBundle.message("refactoring.cannot.be.applied.to.abstract.declaration", refactoringName)
                element.isExpectDeclaration() -> KotlinBundle.message(
                  "refactoring.cannot.be.applied.to.expect.declaration",
                  refactoringName
                )
                else -> null
            }

            if (message != null) {
                showErrorHint(project, editor, message)
                return
            }
        }

        val getter = declaration.getter?.takeIf { it.hasBody() }
        val setter = declaration.setter?.takeIf { it.hasBody() }
        if ((getter != null || setter != null) && declaration.initializer != null) {
            return showErrorHint(
                project,
                editor,
                KotlinBundle.message("cannot.inline.property.with.accessor.s.and.backing.field")
            )
        }

        var assignmentToDelete: KtBinaryExpression? = null
        if (getter == null && setter == null) {
            val initializer = AbstractKotlinInlinePropertyProcessor.extractInitialization(
              declaration).getInitializerOrShowErrorHint(project, editor) ?: return
            assignmentToDelete = initializer.assignment
        }

        performRefactoring(declaration, assignmentToDelete, editor)
    }

    private fun performRefactoring(
      declaration: KtProperty,
      assignmentToDelete: KtBinaryExpression?,
      editor: Editor?,
    ) {
        val reference = editor?.findSimpleNameReference()
        val dialog = createInlinePropertyDialog(declaration, reference, assignmentToDelete, editor)

        if (withPrompt && !isUnitTestMode() && dialog.shouldBeShown()) {
            dialog.show()
        } else {
            try {
                dialog.doAction()
            } finally {
                dialog.close(DialogWrapper.OK_EXIT_CODE, true)
            }
        }
    }

    private fun createInlinePropertyDialog(
        declaration: KtProperty,
        reference: PsiReference?,
        assignmentToDelete: KtBinaryExpression?,
        editor: Editor?
    ): AbstractKotlinInlinePropertyDialog = object : AbstractKotlinInlinePropertyDialog(
        property = declaration,
        reference = reference,
        withPreview = withPrompt,
        editor = editor
    ) {
        override fun createProcessor(): AbstractKotlinInlinePropertyProcessor = createProcessor(
            declaration = declaration,
            reference = reference,
            inlineThisOnly = isInlineThisOnly,
            deleteAfter = !isInlineThisOnly && !isKeepTheDeclaration,
            isWhenSubjectVariable = (declaration.parent as? KtWhenExpression)?.subjectVariable == declaration,
            editor = editor,
            statementToDelete = assignmentToDelete,
            project = project,
        )
    }

    abstract fun createProcessor(
        declaration: KtProperty,
        reference: PsiReference?,
        inlineThisOnly: Boolean,
        deleteAfter: Boolean,
        isWhenSubjectVariable: Boolean,
        editor: Editor?,
        statementToDelete: KtBinaryExpression?,
        project: Project
    ): AbstractKotlinInlinePropertyProcessor
}