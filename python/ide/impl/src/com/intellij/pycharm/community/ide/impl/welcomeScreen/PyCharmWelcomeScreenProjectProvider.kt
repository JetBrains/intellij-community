// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.welcomeScreen

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.pycharm.community.ide.impl.miscProject.impl.MISC_PROJECT_WITH_WELCOME_NAME
import com.intellij.pycharm.community.ide.impl.miscProject.impl.miscProjectDefaultPath
import com.jetbrains.python.projectCreation.createVenvAndSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

internal class PyCharmWelcomeScreenProjectProvider : WelcomeScreenProjectProvider() {
  override fun getWelcomeScreenProjectName(): String = MISC_PROJECT_WITH_WELCOME_NAME

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

  override suspend fun doCreateOrOpenWelcomeScreenProject(path: Path): Project {
    val project = super.doCreateOrOpenWelcomeScreenProject(path)
    // The name might be a MiscProject, since we are reusing that project.
    // We have to rename it to Welcome.
    if (project.name != MISC_PROJECT_WITH_WELCOME_NAME && project is ProjectEx) {
      project.setProjectName(MISC_PROJECT_WITH_WELCOME_NAME)
      project.save()
    }

    if (PlatformProjectOpenProcessor.isNewProject(project)) {
      createVenvAndSdk(project, confirmInstallation = {
        withContext(Dispatchers.EDT) {
          MessageDialogBuilder.yesNo(
            PyCharmCommunityCustomizationBundle.message("misc.no.python.found"),
            PyCharmCommunityCustomizationBundle.message("misc.install.python.question")
          ).ask(project)
        }
      })
    }
    return project
  }

  override fun doIsHiddenInRecentProjects(): Boolean = false
}
