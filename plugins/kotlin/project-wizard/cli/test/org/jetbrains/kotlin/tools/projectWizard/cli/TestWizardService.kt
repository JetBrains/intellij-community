// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.cli

import org.jetbrains.kotlin.tools.projectWizard.core.service.Services
import org.jetbrains.kotlin.tools.projectWizard.core.service.ServicesManager
import org.jetbrains.kotlin.tools.projectWizard.core.service.WizardService

interface TestWizardService : WizardService {
    companion object {
        val SERVICES = listOf(
            KotlinVersionProviderTestWizardService()
        )
    }
}

val CLI_WIZARD_TEST_SERVICES_MANAGER = ServicesManager(
    Services.IDEA_INDEPENDENT_SERVICES + TestWizardService.SERVICES
) { services ->
    services.firstOrNull { it is TestWizardService } ?: services.firstOrNull()
}