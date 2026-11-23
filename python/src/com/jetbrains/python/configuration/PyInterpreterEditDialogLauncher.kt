// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.python.sdk.backend.NativePythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.TargetPythonInterpreter
import org.jetbrains.annotations.ApiStatus

/**
 * Opens the "Interpreter Settings" edit form for the given [PythonInterpreter] as a modal dialog.
 *
 * Only a target interpreter has a form: the target-specific one ([PyRemoteInterpreterEditDialog], Docker / SSH / WSL /
 * …). An interpreter keeps its binary, its name and its owner, so a native interpreter has nothing to edit. [editorFor]
 * returns `null` for it, and the caller skips the open.
 */
@ApiStatus.Internal
object PyInterpreterEditDialogLauncher {

  @JvmStatic
  fun editorFor(project: Project, interpreter: PythonInterpreter, parent: Configurable): DialogWrapper? = when (interpreter) {
    is NativePythonInterpreter -> null
    is TargetPythonInterpreter -> PyRemoteInterpreterEditDialog.createFor(project, interpreter, parent)
  }
}
