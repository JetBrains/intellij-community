// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.welcomeScreen

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.pycharm.community.ide.impl.miscProject.impl.MISC_PROJECT_NAME
import com.intellij.pycharm.community.ide.impl.miscProject.impl.miscProjectDefaultPath
import java.nio.file.Path

private class PyCharmWelcomeScreenProjectProvider : WelcomeScreenProjectProvider() {
  override fun getWelcomeScreenProjectName(): String = MISC_PROJECT_NAME

  override fun getWelcomeScreenProjectPath(): Path = miscProjectDefaultPath

  override fun doIsWelcomeScreenProject(project: Project): Boolean = project.name == MISC_PROJECT_NAME

  override fun doIsForceDisabledFileColors(): Boolean = true

  override fun doGetCreateNewFileProjectPrefix(): String = "awesomeProject"

  override suspend fun doCreateOrOpenWelcomeScreenProject(path: Path): Project {
    val project = super.doCreateOrOpenWelcomeScreenProject(path)
    // The name might be a MiscProject, since we are reusing that project.
    // We have to rename it to Welcome.
    if (project.name != MISC_PROJECT_NAME && project is ProjectEx) {
      project.setProjectName(MISC_PROJECT_NAME)
      project.save()
    }
    return project
  }

  override fun doIsHiddenInRecentProjects(): Boolean = false
}
