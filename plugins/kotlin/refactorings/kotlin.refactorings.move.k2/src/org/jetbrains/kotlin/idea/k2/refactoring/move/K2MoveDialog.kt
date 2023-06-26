// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.refactoring.move.MoveHandler
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import javax.swing.JComponent

class K2MoveDialog(project: Project, private val type: K2MoveDescriptor) : RefactoringDialog(project, true) {
    private lateinit var mainPanel: DialogPanel

    init {
        title = MoveHandler.getRefactoringName()
        init()
    }

    override fun createCenterPanel(): JComponent {
        mainPanel = panel {
            type.createPanel(::setErrorText)
        }
        return mainPanel
    }

    private fun saveSettings() {
        mainPanel.apply()
        KotlinCommonRefactoringSettings.getInstance().MOVE_PREVIEW_USAGES = isPreviewUsages
    }

    override fun doAction() {
        saveSettings()
        if (type.source.elements.isEmpty()) {
            throw ConfigurationException(KotlinBundle.message("text.no.elements.to.move.are.selected"))
        }
        invokeRefactoring(type.refactoringProcessor)
    }
}