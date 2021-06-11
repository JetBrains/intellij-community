// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.core.service

import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind

class KotlinVersionProviderServiceImpl : KotlinVersionProviderService(), IdeaIndependentWizardService {
    override fun getKotlinVersion(projectKind: ProjectKind): WizardKotlinVersion {
        val version = when (projectKind) {
            ProjectKind.COMPOSE -> Versions.KOTLIN_VERSION_FOR_COMPOSE
            else -> getKotlinVersionFromCompiler() ?: Versions.KOTLIN
        }
        return kotlinVersionWithDefaultValues(version)
    }
}