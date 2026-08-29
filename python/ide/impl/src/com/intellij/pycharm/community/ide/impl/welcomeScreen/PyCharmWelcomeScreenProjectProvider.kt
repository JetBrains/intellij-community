// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.welcomeScreen

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.pycharm.community.ide.impl.miscProject.impl.miscProjectDefaultPath
import com.jetbrains.python.orLogException
import com.jetbrains.python.projectCreation.SystemPythonRequirements
import com.jetbrains.python.projectCreation.createVenvAndSdk
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.withSdkConfigurationLock
import java.nio.file.Path
import kotlin.io.path.extension

internal class PyCharmWelcomeScreenProjectProvider : WelcomeScreenProjectProvider() {
  override fun getWelcomeScreenProjectPath(): Path = miscProjectDefaultPath

  override fun doIsWelcomeScreenProject(project: Project): Boolean {
    val projectBasePath = project.basePath ?: return false
    return Path.of(projectBasePath) == miscProjectDefaultPath
  }

  override fun doIsEditableProject(project: Project): Boolean {
    return true
  }

  override fun doIsForceDisabledFileColors(): Boolean = true

  override fun doGetCreateNewFileProjectPrefix(): String = "awesomeProject"

  // We explicitly want to open existing project if the file already belongs to it
  override fun shouldOpenInWelcomeScreenIfFileBelongsToProject(filePath: Path): Boolean = false

  override fun canOpenFilesFromSystemFileManager(filePath: Path): Boolean {
    return Registry.`is`("welcome.screen.open.files", false) && filePath.extension == "ipynb"
  }

  override suspend fun doCreateOrOpenWelcomeScreenProject(path: Path): Project {
    val project = super.doCreateOrOpenWelcomeScreenProject(path)

    if (PlatformProjectOpenProcessor.isNewProject(project)) {
      // Don't prompt to install Python on the welcome screen (PY-88204).
      // If Python is already available, the venv/SDK will be configured silently.
      withSdkConfigurationLock(project) {
        val systemPythonRequirements = SystemPythonRequirements.ByVersionSpecifier(confirmInstallation = { false })
        createVenvAndSdk(ModuleOrProject.ProjectOnly(project), systemPythonRequirements).orLogException(thisLogger())
      }
    }
    return project
  }

  override fun doIsHiddenInRecentProjects(): Boolean = false
}
