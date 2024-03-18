// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.refactoring.move.MoveHandler
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.plus
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import javax.swing.JComponent

internal class K2MoveDialog(project: Project, private val model: K2MoveModel) : RefactoringDialog(project, true) {
    private lateinit var mainPanel: DialogPanel

    init {
        title = MoveHandler.getRefactoringName()
        init()
    }

    override fun createCenterPanel(): JComponent {
        mainPanel = panel {
            model.target.buildPanel(::setErrorText, ::validateRefactorButton)
            model.source.buildPanel(::setErrorText, ::validateRefactorButton)
            row {
                panel {
                    model.searchForText.createComboBox()
                    if (model.inSourceRoot) model.searchReferences.createComboBox()
                }.align(AlignY.TOP + AlignX.LEFT)
                panel {
                    model.searchInComments.createComboBox()
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
        invokeRefactoring(descriptor.refactoringProcessor())
    }

    override fun hasHelpAction(): Boolean = false
}