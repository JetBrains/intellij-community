// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.cli

import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.core.service.EapVersionDownloader
import org.jetbrains.kotlin.tools.projectWizard.core.service.WizardKotlinVersion
import org.jetbrains.kotlin.tools.projectWizard.core.service.KotlinVersionKind
import org.jetbrains.kotlin.tools.projectWizard.core.service.KotlinVersionProviderService
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.*

class KotlinVersionProviderTestWizardService : KotlinVersionProviderService(), TestWizardService {
    override fun getKotlinVersion(projectKind: ProjectKind): WizardKotlinVersion =
        kotlinVersionWithDefaultValues(
            when (projectKind) {
                ProjectKind.COMPOSE -> Versions.KOTLIN_VERSION_FOR_COMPOSE
                else -> TEST_KOTLIN_VERSION
            }
        )

    companion object {
        val TEST_KOTLIN_VERSION by lazy {
            EapVersionDownloader.getLatestEapVersion()!!
        }
    }
}
