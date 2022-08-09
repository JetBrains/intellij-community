// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.core.service

import org.jetbrains.kotlin.tools.projectWizard.WizardRunConfiguration
import org.jetbrains.kotlin.tools.projectWizard.core.Reader


interface RunConfigurationsService : WizardService {
    fun Reader.addRunConfigurations(configurations: List<WizardRunConfiguration>)
}

class RunConfigurationsServiceImpl : RunConfigurationsService, IdeaIndependentWizardService {
    override fun Reader.addRunConfigurations(configurations: List<WizardRunConfiguration>) = Unit
}