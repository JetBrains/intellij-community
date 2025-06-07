// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard.impl.projectPath

import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.newProjectWizard.PyV3UIServices
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathProvider
import org.jetbrains.annotations.ApiStatus


/**
 * Wraps [field] that represents project path, and emits [projectPathFlows] out of it
 *
 * [onProjectFileNameChanged] allows caller to receive every [ProjectPathFlows.projectName] event as long as [field] is visible
 */
@ApiStatus.Internal
class ProjectPathImpl(
  private val field: TextFieldWithBrowseButton,
  private val uiServices: PyV3UIServices,
) : ProjectPathProvider {


  private val listener = DocumentListenerToFlowAdapter(field)
  override val projectPathFlows: ProjectPathFlows = ProjectPathFlows.create(listener.flow)

  init {
    field.addDocumentListener(listener)
    Disposer.register(field) {
      field.textField.document.removeDocumentListener(listener)
    }
  }

  @RequiresEdt
  override fun onProjectFileNameChanged(code: suspend (projectPathName: @NlsSafe String) -> Unit) {
    uiServices.runWhenComponentDisplayed(field) {
      projectPathFlows.projectName.collect(code)
    }
  }
}
