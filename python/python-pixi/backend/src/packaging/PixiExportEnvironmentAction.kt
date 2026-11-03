// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptorFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.intellij.python.pyproject.model.evolution.findEvoPyProjectIfReady
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.io.path.writeText

internal class PixiExportEnvironmentAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = manager(e) != null
    e.presentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.coroutineScope.launch {
      val manager = readAction { manager(e) } ?: return@launch
      val title = PyPixiBackendBundle.message("action.Python.Pixi.ExportEnvironment.text")
      val destination = withContext(Dispatchers.EDT) {
        FileChooserFactory.getInstance().createSaveFileDialog(
          FileSaverDescriptorFactory.createSingleFileNoJarsDescriptor().apply {
            withTitle(title)
            withDescription(PyPixiBackendBundle.message("python.pixi.export.description"))
            withExtensionFilter(PyPixiBackendBundle.message("python.pixi.export.format"), "yml", "yaml")
          }, manager.project,
        ).save(manager.interpreter.ownerDir, "environment.yml")?.file?.toPath()
      } ?: return@launch
      PythonPackageManagerUI.forPackageManager(manager).executeCommand(title) {
        val output = manager.exportEnvironment().getOr { return@executeCommand it }
        withContext(Dispatchers.IO) {
          try {
            destination.writeText(output)
            PyResult.success(Unit)
          }
          catch (e: IOException) {
            PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.export.failed", destination, e.localizedMessage))
          }
        }
      }
    }
  }

  private fun manager(e: AnActionEvent): PixiPackageManager? {
    val file = e.getData(CommonDataKeys.PSI_FILE) ?: return null
    if (file.name != "pixi.toml" && file.name != "pyproject.toml") return null
    val interpreter = file.findEvoPyProjectIfReady(mainForOrphans = false)?.interpreter ?: return null
    return PythonPackageManager.forPythonInterpreter(file.project, interpreter) as? PixiPackageManager
  }
}
