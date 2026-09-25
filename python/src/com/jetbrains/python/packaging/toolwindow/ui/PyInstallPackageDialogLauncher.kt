// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
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
 * When [sdk] is supplied and the packaging service has no bound SDK yet, the service is warmed up
 * with [sdk] before the dialog is shown so the popup opens on the target environment instead of a
 * blank state. Without pre-init the dialog's own `ensureSdkInitialized` step would eventually pick
 * `Project.findFirstPythonSdk()`, which is the wrong module in a multi-module workspace.
 */
@ApiStatus.Internal
object PyInstallPackageDialogLauncher {
  fun open(
    project: Project,
    sdk: Sdk? = null,
    initialSearchText: String? = null,
    preselectModuleName: String? = null,
    preselectGroupName: String? = null,
  ) {
    val service = project.service<PyPackagingToolWindowService>()
    if (sdk == null || service.currentSdk == sdk) {
      PyInstallPackageDialog(project).show(
        initialSearchText = initialSearchText,
        preselectModuleName = preselectModuleName,
        preselectGroupName = preselectGroupName,
      )
      return
    }
    service.serviceScope.launch {
      service.initForSdk(sdk)
      withContext(Dispatchers.EDT) {
        PyInstallPackageDialog(project).show(
          initialSearchText = initialSearchText,
          preselectModuleName = preselectModuleName,
          preselectGroupName = preselectGroupName,
        )
      }
    }
  }
}
