// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.cli

import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradlePluginVersions
import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.core.service.EapVersionDownloader
import org.jetbrains.kotlin.tools.projectWizard.core.service.WizardKotlinVersion
import org.jetbrains.kotlin.tools.projectWizard.core.service.KotlinVersionProviderService
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.*
import org.jetbrains.kotlin.tools.projectWizard.settings.version.Version

class KotlinVersionProviderTestWizardService : KotlinVersionProviderService(), TestWizardService {
    override fun getKotlinVersion(projectKind: ProjectKind): WizardKotlinVersion =
        if (projectKind == ProjectKind.COMPOSE) {
            kotlinVersionWithDefaultValues(Versions.KOTLIN_VERSION_FOR_COMPOSE)
        } else {
            val version = Version(KotlinGradlePluginVersions.latest.toString())
            WizardKotlinVersion(
                version,
                getKotlinVersionKind(version),
                listOf(
                    getKotlinVersionRepository(version),
                    Repositories.JETBRAINS_KOTLIN_BOOTSTRAP
                ),
                getBuildSystemPluginRepository(getKotlinVersionKind(version), Repositories.JETBRAINS_KOTLIN_BOOTSTRAP),
            )
        }

    companion object {
        val TEST_KOTLIN_VERSION by lazy {
            EapVersionDownloader.getLatestEapVersion()!!
        }
    }
}
