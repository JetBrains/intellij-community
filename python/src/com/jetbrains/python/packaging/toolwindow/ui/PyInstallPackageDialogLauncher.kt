// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Public entry point that opens [PyInstallPackageDialog] outside the Python Packages tool window.
 *
 * [PyInstallPackageDialog] and its `show` method are `internal` to this module because they lean on
 * the packaging service's private state; this launcher is `object`-public so callers in downstream
 * modules (for example, the redesigned "Workspace Structure" settings page — PY-89840) can open the
 * dialog without going through the packaging tool window.
 *
 * When `interpreter` is supplied and the packaging service is bound to another one, the service is
 * bound to that interpreter before the dialog is shown so the popup opens on the target environment
 * instead of a blank state. Without it the dialog's own `ensureSdkInitialized` step would pick the
 * interpreter of the first Python project, which is the wrong one in a multi-project workspace.
 */
@ApiStatus.Internal
object PyInstallPackageDialogLauncher {
  fun open(
    project: Project,
    interpreter: PythonInterpreter? = null,
    initialSearchText: String? = null,
    preselectModuleName: String? = null,
    preselectGroupName: String? = null,
  ) {
    // Taken on the caller's thread, so the dialog opens over a modal Settings window instead of waiting for it to close.
    val modality = ModalityState.current().asContextElement()
    val service = project.service<PyPackagingToolWindowService>()
    if (interpreter == null || service.currentInterpreter == interpreter) {
      PyInstallPackageDialog(project).show(
        initialSearchText = initialSearchText,
        preselectModuleName = preselectModuleName,
        preselectGroupName = preselectGroupName,
      )
      return
    }
    service.serviceScope.launch {
      service.initForInterpreter(interpreter)
      withContext(Dispatchers.EDT + modality) {
        PyInstallPackageDialog(project).show(
          initialSearchText = initialSearchText,
          preselectModuleName = preselectModuleName,
          preselectGroupName = preselectGroupName,
        )
      }
    }
  }
}
