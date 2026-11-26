// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProjectWizard.impl.emptyProject

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorOpenRequest
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pycharm.community.ide.impl.newProjectWizard.welcome.PyWelcome
import com.intellij.pycharm.community.ide.impl.newProjectWizard.welcome.PyWelcomeSettings
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProjectWizard.PyV3ProjectTypeSpecificSettings

class PyV3EmptyProjectSettings(
  var generateWelcomeScript: Boolean = PyWelcomeSettings.instance.createWelcomeScriptForEmptyProject,
) : PyV3ProjectTypeSpecificSettings {

  override suspend fun generateProject(module: Module, baseDir: VirtualFile, interpreter: PythonInterpreter): PyResult<Unit> {
    PyWelcomeSettings.instance.createWelcomeScriptForEmptyProject = generateWelcomeScript
    if (!generateWelcomeScript)
      return PyResult.success(Unit)

    val sourceRoot = module.rootManager.sourceRoots.firstOrNull() ?: baseDir
    val project = module.project
    val file = edtWriteAction {
      PyWelcome.prepareFile(project, sourceRoot).virtualFile
    }
    val editorManager = FileEditorManager.getInstanceAsync(project)
    if (file.isValid) {
      editorManager.openFile(file, FileEditorOpenRequest.withFocus(true))
    }

    return PyResult.success(Unit)
  }

  override fun toString(): String {
    return super.toString() + ",PyV3EmptyProjectRequest(generateWelcomeScript=$generateWelcomeScript)"
  }
}