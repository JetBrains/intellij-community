//// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.poetry

import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.PythonSdkUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * This class represents a post-startup activity for PyProjectToml files in a project.
 * It finds valid python versions in PyProjectToml files and saves them in PyProjectTomlPythonVersionsService.
 */
private class PoetryPyProjectTomlPostStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    val modulesRoots = PythonSdkUpdater.getModuleRoots(project)
    for (module in modulesRoots) {
      val tomlFile = withContext(Dispatchers.IO) {
        module.findChild(PY_PROJECT_TOML)?.let { getPyProjectTomlForPoetry(it) }
      } ?: continue
      val versionString = poetryFindPythonVersionFromToml(tomlFile, project) ?: continue

      PoetryPyProjectTomlPythonVersionsService.instance.setVersion(module, versionString)
      addDocumentListener(tomlFile, project, module)
    }
  }


  /**
   * Adds a document listener to a given toml file.
   * Updates PyProjectTomlPythonVersionsService map if needed.
   *
   * @param tomlFile The VirtualFile representing the toml file.
   * @param project The Project in which the toml file exists.
   * @param module The VirtualFile representing the module.
   */
  private suspend fun addDocumentListener(tomlFile: VirtualFile, project: Project, module: VirtualFile) {
    readAction {
      tomlFile.findDocument()?.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          PyPackageCoroutine.launch(project) {
            val newVersion = poetryFindPythonVersionFromToml(tomlFile, project) ?: return@launch
            val oldVersion = PoetryPyProjectTomlPythonVersionsService.instance.getVersionString(module)
            if (oldVersion != newVersion) {
              PoetryPyProjectTomlPythonVersionsService.instance.setVersion(module, newVersion)
            }
          }
        }
      }, PoetryPyProjectTomlPythonVersionsService.instance)
    }
  }
}
