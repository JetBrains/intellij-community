// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.wizard.service

import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.tools.projectWizard.core.service.JvmTargetVersionsProviderService
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.TargetJvmVersion
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind

class IdeaJvmTargetVersionProviderService: JvmTargetVersionsProviderService(), IdeaWizardService {

    override fun listSupportedJvmTargetVersions(projectKind: ProjectKind): Set<TargetJvmVersion> {
        return JvmTarget.supportedValues().mapNotNull { mapToInternal(it) }.toSet()
    }

    private fun mapToInternal(target: JvmTarget): TargetJvmVersion? = try {
        TargetJvmVersion.valueOf(target.name)
    } catch (e: Exception) { null /* we do not support deprecated targets */}
}