// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.refactoring.move.MoveHandler
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.plus
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import javax.swing.JComponent

class K2MoveDialog(project: Project, private val model: K2MoveModel) : RefactoringDialog(project, true, true) {
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
                    model.updateUsages.createComboBox(this, model.inSourceRoot)
                    model.mppDeclarations.createComboBox(this, model.inSourceRoot && model is K2MoveModel.Declarations)
                }.align(AlignY.TOP + AlignX.LEFT)
                panel {
                    model.updateTextOccurrences.createComboBox(this)
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
        if (isOpenInEditor) PropertiesComponent.getInstance().setValue("MoveFile.OpenInEditor", true)
        val descriptor = ActionUtil.underModalProgress(
            project,
            KotlinBundle.message("preparing.move.descriptor")
        ) {
            model.toDescriptor()
        }
        invokeRefactoring(descriptor.refactoringProcessor())
    }

    override fun hasHelpAction(): Boolean = false
}