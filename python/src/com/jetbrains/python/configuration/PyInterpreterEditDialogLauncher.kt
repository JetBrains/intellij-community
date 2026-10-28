// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.jetbrains.annotations.ApiStatus

/**
 * Opens the "Interpreter Settings" edit form for the given [PythonInterpreter] as a modal dialog.
 *
 * Only a target-aware SDK ([PyTargetAwareAdditionalData]) has a form: the target-specific one ([PyRemoteInterpreterEditDialog],
 * Docker / SSH / WSL / …). An interpreter keeps its binary, its name and its owner, so a local interpreter has nothing
 * to edit, and a legacy remote SDK whose target info has been lost cannot be edited. [editorFor] returns `null` for
 * both, and the caller skips the open.
 */
@ApiStatus.Internal
object PyInterpreterEditDialogLauncher {

  @JvmStatic
  fun editorFor(project: Project, interpreter: PythonInterpreter, parent: Configurable): DialogWrapper? {
    val sdk: Sdk = interpreter.getSdkAPI()
    if (sdk.sdkAdditionalData !is PyTargetAwareAdditionalData) return null
    // `createFor` returns `null` for a legacy remote SDK whose target information has been lost.
    return PyRemoteInterpreterEditDialog.createFor(project, sdk, parent)
  }
}
