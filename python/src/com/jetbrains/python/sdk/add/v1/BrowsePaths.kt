// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v1

import com.intellij.execution.target.BrowsableTargetEnvironmentType
import com.intellij.execution.target.TargetBrowserHints
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.getTargetType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton

fun TextFieldWithBrowseButton.addBrowseFolderListener(
  project: Project?,
  configuration: TargetEnvironmentConfiguration?,
  targetBrowserHints: TargetBrowserHints
) {
  if (configuration == null) {
    addBrowseFolderListener(project, targetBrowserHints.customFileChooserDescriptor!!)
  }
  else {
    val targetType = configuration.getTargetType()
    if (targetType is BrowsableTargetEnvironmentType) {
      addActionListener(targetType.createBrowser(
        project ?: ProjectManager.getInstance().defaultProject,
        targetBrowserHints.customFileChooserDescriptor!!.title,
        TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
        this.textField,
        { configuration },
        targetBrowserHints
      ))
    }
    else {
      setButtonVisible(false)
    }
  }
}
