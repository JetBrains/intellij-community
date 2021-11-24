// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add.target

import com.intellij.execution.target.BrowsableTargetEnvironmentType
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.getTargetType
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsContexts
import java.util.function.Supplier

fun TextFieldWithBrowseButton.addBrowseFolderListener(@NlsContexts.DialogTitle title: String,
                                                      project: Project?,
                                                      configuration: TargetEnvironmentConfiguration?,
                                                      fileChooserDescriptor: FileChooserDescriptor) {
  if (configuration == null) {
    addBrowseFolderListener(title, null, project, fileChooserDescriptor)
  }
  else {
    val targetType = configuration.getTargetType()
    if (targetType is BrowsableTargetEnvironmentType) {
      withTargetBrowser(targetType, { configuration }, project, title)
    }
  }
}

fun TextFieldWithBrowseButton.withTargetBrowser(targetType: BrowsableTargetEnvironmentType,
                                                targetSupplier: Supplier<TargetEnvironmentConfiguration>,
                                                project: Project?,
                                                @NlsContexts.DialogTitle title: String) {
  val browser = targetType.createBrowser(project ?: ProjectManager.getInstance().defaultProject,
                                         title,
                                         com.intellij.openapi.ui.TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
                                         textField,
                                         targetSupplier)
  addActionListener(browser)
}