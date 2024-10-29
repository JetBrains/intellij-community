// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProjectWizard.impl

import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase
import com.jetbrains.python.newProjectWizard.PyV3BaseProjectSettings
import com.jetbrains.python.newProjectWizard.PyV3ProjectBaseGenerator

internal class PyV3ProjectSpecificStep(
  generator: PyV3ProjectBaseGenerator<*>,
  callback: AbstractNewProjectStep.AbstractCallback<PyV3BaseProjectSettings>,
) : ProjectSettingsStepBase<PyV3BaseProjectSettings>(generator, callback, generator.newProjectName)