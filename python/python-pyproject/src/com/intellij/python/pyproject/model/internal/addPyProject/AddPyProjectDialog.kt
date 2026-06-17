// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.addPyProject

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.validation.CHECK_NAME_FORMAT
import com.intellij.openapi.ui.validation.CHECK_NON_EMPTY
import com.intellij.python.pyproject.model.internal.PyProjectTomlBundle
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.trimmedTextValidation
import javax.swing.JComponent

/**
 * Dialog to ask user for project name
 */
internal class AddPyProjectDialog(project: Project, private val presenter: AddPyProjectPresenter) : DialogWrapper(project) {

  init {
    title = presenter.actionText
    init()
  }

  override fun createCenterPanel(): JComponent = panel {
    row {
      textField().resizableColumn()
        .bindText(presenter::projectName).label(PyProjectTomlBundle.message("new.pyproject.dialog.project.name"))
        .trimmedTextValidation(CHECK_NON_EMPTY, CHECK_NAME_FORMAT)
        .focused()
    }
  }
}