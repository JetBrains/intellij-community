// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProject.steps

import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pycharm.community.ide.impl.newProject.welcome.PyWelcomeGenerator.createWelcomeSettingsPanel
import com.intellij.pycharm.community.ide.impl.newProject.welcome.PyWelcomeGenerator.welcomeUser
import com.jetbrains.python.PyBundle
import com.jetbrains.python.newProject.PyNewProjectSettings
import com.jetbrains.python.newProject.PythonProjectGenerator
import com.jetbrains.python.psi.icons.PythonPsiApiIcons
import com.jetbrains.python.remote.PyProjectSynchronizer
import com.jetbrains.python.sdk.pythonSdk
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JPanel

class PythonBaseProjectGenerator : PythonProjectGenerator<PyNewProjectSettings?>(true) {
  override fun getName(): @Nls String = PyBundle.message("pure.python.project")

  @Throws(ProcessCanceledException::class)
  override fun extendBasePanel(): JPanel = JPanel(VerticalFlowLayout(3, 0)).apply {
    add(createWelcomeSettingsPanel())
  }

  override fun getLogo(): Icon = PythonPsiApiIcons.Python

  public override fun configureProject(project: Project,
                                       baseDir: VirtualFile,
                                       settings: PyNewProjectSettings,
                                       module: Module,
                                       synchronizer: PyProjectSynchronizer?) {
    // Super should be called according to its contract unless we sync project explicitly (we do not, so we call super)
    super.configureProject(project, baseDir, settings, module, synchronizer)
    module.pythonSdk = settings.sdk
    welcomeUser(project, baseDir, module)
  }

  override fun getNewProjectPrefix(): String = "pythonProject"

  override fun supportsWelcomeScript(): Boolean = true
}
