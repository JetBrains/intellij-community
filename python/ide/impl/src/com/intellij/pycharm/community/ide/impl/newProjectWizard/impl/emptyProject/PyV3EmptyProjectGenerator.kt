// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProjectWizard.impl.emptyProject

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.PyBundle
import com.jetbrains.python.newProjectWizard.PyV3ProjectBaseGenerator
import com.jetbrains.python.newProjectWizard.collector.PyProjectTypeValidationRule
import com.jetbrains.python.psi.icons.PythonPsiApiIcons
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import javax.swing.Icon

@Internal
class PyV3EmptyProjectGenerator : PyV3ProjectBaseGenerator<PyV3EmptyProjectSettings>(
  typeSpecificSettings = PyV3EmptyProjectSettings(generateWelcomeScript = false),
  typeSpecificUI = PyV3EmptyProjectUI,
  _newProjectName = "PythonProject",
  supportsNotEmptyModuleStructure = true
) {
  override fun getName(): @Nls String = PyBundle.message("pure.python.project")

  override fun getLogo(): Icon = PythonPsiApiIcons.Python

  override val projectTypeForStatistics: @NlsSafe String = PyProjectTypeValidationRule.EMPTY_PROJECT_TYPE_ID
}