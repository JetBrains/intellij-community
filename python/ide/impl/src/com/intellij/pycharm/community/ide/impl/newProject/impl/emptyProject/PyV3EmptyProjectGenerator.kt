// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProject.impl.emptyProject

import com.jetbrains.python.newProjectWizard.PyV3ProjectBaseGenerator
import com.jetbrains.python.PyBundle
import com.jetbrains.python.psi.icons.PythonPsiApiIcons
import org.jetbrains.annotations.Nls
import javax.swing.Icon

class PyV3EmptyProjectGenerator : PyV3ProjectBaseGenerator<PyV3EmptyProjectSettings>(
  PyV3EmptyProjectSettings(generateWelcomeScript = false), PyV3EmptyProjectUI) {
  override fun getName(): @Nls String = PyBundle.message("pure.python.project")

  override fun getLogo(): Icon = PythonPsiApiIcons.Python
}