// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.openapi.module.Module
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
 * A target-aware SDK ([PyTargetAwareAdditionalData]) gets the target-specific form
 * ([PyRemoteInterpreterEditDialog], Docker / SSH / WSL / …). Everything else is treated as a local
 * interpreter and gets the compact path + associate form ([PyLocalInterpreterEditDialog]). Legacy
 * remote SDKs whose target info has been lost — and broken local SDKs with no home path — cannot
 * be edited, and [editorFor] returns `null` for them so the caller can disable the toolbar button
 * and skip the open.
 *
 * @param associationTarget module the "Associate with project" checkbox writes to. `null` means the
 *   caller has no module context (e.g. a project-scoped page with no modules), so the local dialog
 *   allows only clearing an existing association, not establishing a new one.
 */
@ApiStatus.Internal
object PyInterpreterEditDialogLauncher {

  @JvmStatic
  fun editorFor(
    project: Project,
    interpreter: PythonInterpreter,
    associationTarget: Module?,
    parent: Configurable,
  ): DialogWrapper? {
    val sdk: Sdk = interpreter.getSdkAPI()
    if (sdk.sdkAdditionalData is PyTargetAwareAdditionalData) {
      // Target-aware SDK: gets the target-specific form. `createFor` returns `null` for a legacy
      // remote SDK whose target information has been lost — the caller treats it as "not editable".
      return PyRemoteInterpreterEditDialog.createFor(project, sdk, parent)
    }
    // Swallowing `null` here is intentional, not a fallback for a bug: `createOrNull` returns
    // `null` when the interpreter carries no `PythonEnvironment` — a legitimate state for a broken
    // or half-configured local SDK. The Edit action has nothing to render for it (path field would
    // be empty, apply would fail), so the launcher returns `null` and the caller treats it as
    // "hide the Edit button". No error is surfaced because there is nothing to fix from within
    // this dialog — the user recreates the SDK via the Add Interpreter flow.
    val handle = PyLocalInterpreterHandle.createOrNull(interpreter) ?: return null
    val model = PyLocalInterpreterEditModel(project, handle, associationTarget)
    return PyLocalInterpreterEditDialog(project, model)
  }
}
