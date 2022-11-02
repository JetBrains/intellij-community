// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.core.service

import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind

class CoreKotlinVersionProviderService : KotlinVersionProviderService(), IdeaIndependentWizardService {
    override fun getKotlinVersion(projectKind: ProjectKind): WizardKotlinVersion {
        return kotlinVersionWithDefaultValues(Versions.KOTLIN)
    }
}