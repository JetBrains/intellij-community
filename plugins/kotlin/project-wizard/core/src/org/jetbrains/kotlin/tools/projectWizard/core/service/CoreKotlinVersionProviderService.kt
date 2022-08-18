// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.core.service

import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.settings.version.Version

class CoreKotlinVersionProviderService : KotlinVersionProviderService(), IdeaIndependentWizardService {
    companion object {
        internal fun getKotlinVersion(projectKind: ProjectKind): Version = when (projectKind) {
            ProjectKind.COMPOSE -> Versions.KOTLIN_VERSION_FOR_COMPOSE
            else -> Versions.KOTLIN
        }
    }

    override fun getKotlinVersion(projectKind: ProjectKind): WizardKotlinVersion {
        val version = CoreKotlinVersionProviderService.getKotlinVersion(projectKind)
        return kotlinVersionWithDefaultValues(version)
    }
}