// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.miscProject.impl

import com.intellij.openapi.project.Project
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeFilesLanguageSupport
import com.intellij.pycharm.community.ide.impl.miscProject.PyMiscService

internal class WelcomePythonFilesSupport : WelcomeFilesLanguageSupport() {
  override fun configureProject(project: Project) {
    PyMiscService.getInstance().createMiscProject(project)
  }
}