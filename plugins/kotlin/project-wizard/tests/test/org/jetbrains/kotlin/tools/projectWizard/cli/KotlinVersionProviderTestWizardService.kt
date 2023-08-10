// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.cli

import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradlePluginVersions
import org.jetbrains.kotlin.tools.projectWizard.core.service.WizardKotlinVersion
import org.jetbrains.kotlin.tools.projectWizard.core.service.KotlinVersionProviderService
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.*
import org.jetbrains.kotlin.tools.projectWizard.settings.version.Version

class KotlinVersionProviderTestWizardService : KotlinVersionProviderService(), TestWizardService {
    override fun getKotlinVersion(projectKind: ProjectKind): WizardKotlinVersion {
        val repositories = listOf(
            Repositories.JETBRAINS_KOTLIN_BOOTSTRAP,
            getKotlinVersionRepository(TEST_KOTLIN_VERSION),
            DefaultRepository.MAVEN_LOCAL
        )
        return WizardKotlinVersion(
            TEST_KOTLIN_VERSION,
            getKotlinVersionKind(TEST_KOTLIN_VERSION),
            repositories,
            getBuildSystemPluginRepository(
                getKotlinVersionKind(TEST_KOTLIN_VERSION),
                repositories
            ),
        )
    }

    companion object {
        val TEST_KOTLIN_VERSION by lazy {
            Version(KotlinGradlePluginVersions.latest.toString())
        }
    }
}
