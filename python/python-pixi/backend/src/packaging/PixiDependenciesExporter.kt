// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.vfs.writeText
import com.intellij.psi.PsiFile
import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.management.DependenciesExporter
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException

internal val PIXI_ENVIRONMENT_EXPORT_FILES: Set<String> = setOf("environment.yml", "environment.yaml")

internal class PixiDependenciesExporter(
  private val manager: PixiPackageManager,
  private val scope: CoroutineScope,
) : DependenciesExporter {
  override fun export(file: PsiFile) {
    val destination = file.virtualFile ?: return
    if (destination.name !in PIXI_ENVIRONMENT_EXPORT_FILES) return
    scope.launch(Dispatchers.IO) {
      val title = PyPixiBackendBundle.message("action.Python.Pixi.ExportEnvironment.text")
      PythonPackageManagerUI.forPackageManager(manager).executeCommand(title) {
        val output = manager.exportEnvironment().getOr { return@executeCommand it }
        try {
          edtWriteAction {
            if (destination.isValid && destination.name in PIXI_ENVIRONMENT_EXPORT_FILES) destination.writeText(output)
          }
          PyResult.success(Unit)
        }
        catch (e: IOException) {
          PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.export.failed", destination.path, e.localizedMessage))
        }
      }
    }
  }
}
