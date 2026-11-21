// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.sdk.backend.PythonInterpreter
import org.jetbrains.annotations.ApiStatus

/**
 * Creates the field for the qualified name of a Python module in a run configuration.
 * The field implements [com.intellij.ui.TextAccessor].
 * The default implementation creates a text field without a browse button.
 */
@ApiStatus.Internal
interface PyModuleNameFieldFactory {
  fun createModuleNameField(
    project: Project,
    module: Module?,
    interpreter: PythonInterpreter?,
    startFromDirectory: (() -> VirtualFile)?,
  ): ComponentWithBrowseButton<*>

  companion object {
    @JvmStatic
    fun getInstance(): PyModuleNameFieldFactory = ApplicationManager.getApplication().getService(PyModuleNameFieldFactory::class.java)
  }
}

internal class PyDefaultModuleNameFieldFactory : PyModuleNameFieldFactory {
  override fun createModuleNameField(
    project: Project,
    module: Module?,
    interpreter: PythonInterpreter?,
    startFromDirectory: (() -> VirtualFile)?,
  ): ComponentWithBrowseButton<*> = TextFieldWithBrowseButton().apply { setButtonVisible(false) }
}
