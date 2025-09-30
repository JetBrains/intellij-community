// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.welcomeScreen

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider

private const val WELCOME_SCREEN_PROJECT_NAME: String = "PyCharmWelcomeScreen"

private class PyCharmWelcomeScreenProjectProvider : WelcomeScreenProjectProvider() {
  override fun getWelcomeScreenProjectName(): String = WELCOME_SCREEN_PROJECT_NAME

  override fun doIsWelcomeScreenProject(project: Project): Boolean = project.name == WELCOME_SCREEN_PROJECT_NAME

  override fun doIsForceDisabledFileColors(): Boolean = true

  override fun doGetCreateNewFileProjectPrefix(): String = "awesomeProject"
}
