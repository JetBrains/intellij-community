// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard.impl.projectPath

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.EelDescriptor
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.newProjectWizard.PyV3UIServices
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathProvider
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus


/**
 * Wraps [field] that represents project path, and emits [projectPathFlows] out of it
 *
 * [onProjectFileNameChanged] allows caller to receive every [ProjectPathFlows.projectName] event as long as [field] is visible
 * and [parentDisposable] is not disposed.
 * Use [parentDisposable] shorter than [field] when you create more than one instance for the same [field].
 */
@ApiStatus.Internal
class ProjectPathImpl(
  private val field: TextFieldWithBrowseButton,
  private val uiServices: PyV3UIServices,
  onlyAllowPathOn: EelDescriptor,
  private val parentDisposable: Disposable = field,
) : ProjectPathProvider {


  private val listener = DocumentListenerToFlowAdapter(field)
  override val projectPathFlows: ProjectPathFlows = ProjectPathFlows.create(listener.flow, onlyAllowPathsOn = onlyAllowPathOn)

  init {
    field.addDocumentListener(listener)
    Disposer.register(parentDisposable) {
      field.textField.document.removeDocumentListener(listener)
    }
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  override fun onProjectFileNameChanged(code: suspend (projectPathName: @NlsSafe String) -> Unit) {
    uiServices.runWhenComponentDisplayed(field) {
      val job = coroutineContext.job
      if (!Disposer.tryRegister(parentDisposable) { job.cancel() }) {
        return@runWhenComponentDisplayed
      }
      projectPathFlows.projectName.collect(code)
    }
  }
}
