// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard.impl.projectPath

import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.showingScope
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathProvider
import kotlinx.coroutines.CoroutineScope


/**
 * Wraps [field] that represents project path, and emits [projectPathFlows] out of it
 *
 * [onProjectFileNameChanged] allows caller to receive every [ProjectPathFlows.projectName] event as long as [field] is visible
 * [fieldShowingScope] shouldn't be changes (except for test purposes)
 */
class ProjectPathImpl(
  private val field: TextFieldWithBrowseButton,
  private val fieldShowingScope: FieldShowingScopeRunner = object : FieldShowingScopeRunner {
    override fun onShowingScope(code: suspend CoroutineScope.() -> Unit) {
      field.showingScope("On Project Changed") {
        code()
      }
    }
  },
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
    fieldShowingScope.onShowingScope {
      projectPathFlows.projectName.collect(code)
    }
  }


  companion object {
    fun interface FieldShowingScopeRunner {
      fun onShowingScope(code: (suspend CoroutineScope.() -> Unit))
    }
  }
}
