// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard.promotion

import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase
import com.jetbrains.python.newProjectWizard.PyV3BaseProjectSettings
import javax.swing.JPanel

class PromoStep(val generator: PromoProjectGenerator) : ProjectSettingsStepBase<PyV3BaseProjectSettings>(generator, AbstractNewProjectStep.AbstractCallback()) {
  override fun createBasePanel(): JPanel {
    myCreateButton.isEnabled = false
    return generator.createPromoPanel()
  }
}