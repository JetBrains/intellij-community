// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.psi.util.descendantsOfType
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.move.MoveHandler
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.plus
import org.jetbrains.kotlin.idea.base.psi.isEffectivelyActual
import org.jetbrains.kotlin.idea.base.psi.isExpectDeclaration
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import javax.swing.JComponent

class K2MoveDialog(project: Project, private val model: K2MoveModel) : RefactoringDialog(project, true) {
    private lateinit var mainPanel: DialogPanel

    init {
        title = MoveHandler.getRefactoringName()
        init()
    }

    override fun createCenterPanel(): JComponent {
        mainPanel = panel {
            model.target.buildPanel(this, ::setErrorText, ::validateRefactorButton)
            model.source.buildPanel(this, ::setErrorText, ::validateRefactorButton)
            row {
                panel {
                    model.searchForText.createComboBox(this)
                    if (model.inSourceRoot) model.searchReferences.createComboBox(this)
                }.align(AlignY.TOP + AlignX.LEFT)
                panel {
                    model.searchInComments.createComboBox(this)
                    model.mppDeclarations.createComboBox(this)
                }.align(AlignY.TOP + AlignX.RIGHT)
            }
        }
        return mainPanel
    }

    private fun validateRefactorButton() {
        refactorAction.isEnabled = model.isValidRefactoring()
    }

    private fun saveSettings() {
        KotlinCommonRefactoringSettings.getInstance().MOVE_PREVIEW_USAGES = isPreviewUsages
    }

    override fun doAction() {
        saveSettings()
        val descriptor = model.toDescriptor()
        val allDeclarations = descriptor.sourceElements.flatMap { it.descendantsOfType<KtNamedDeclaration>() }
        if (allDeclarations.any { it.isExpectDeclaration() || it.isEffectivelyActual() }) {
            val response = Messages.showDialog(
                KotlinBundle.message("kmp.move.not.supported.message"),
                KotlinBundle.message("kmp.move.not.supported.title"),
                arrayOf(RefactoringBundle.message("cancel.button"), RefactoringBundle.message("refactor.anyway.button")), 1,
                Messages.getWarningIcon()
            )
            if (response == 0) {
                close(CLOSE_EXIT_CODE)
                return
            }

        }
        invokeRefactoring(descriptor.refactoringProcessor())
    }

    override fun hasHelpAction(): Boolean = false
}