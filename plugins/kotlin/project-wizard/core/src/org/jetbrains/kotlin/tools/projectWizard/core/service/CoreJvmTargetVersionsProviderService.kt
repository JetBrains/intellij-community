// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.core.service

import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.TargetJvmVersion
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.TargetJvmVersion.*
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.settings.version.Version

class CoreJvmTargetVersionsProviderService : JvmTargetVersionsProviderService(), IdeaIndependentWizardService {
    companion object {
        private val KOTLIN_14_JVM_TARGETS = setOf(JVM_1_8, JVM_9, JVM_10, JVM_11, JVM_12, JVM_13, JVM_14, JVM_15)

        private val kotlinToJvmTargetVersions: Map<Version, Set<TargetJvmVersion>> = mapOf(
            Versions.KOTLIN to KOTLIN_14_JVM_TARGETS,
        )

        private fun listSupportedJvmTargetVersions(kotlinVersion: Version) =
            kotlinToJvmTargetVersions[kotlinVersion] ?: error("Kotlin version $kotlinVersion is not associated with any jvm targets")
    }

    override fun listSupportedJvmTargetVersions(projectKind: ProjectKind): Set<TargetJvmVersion> {
        return listSupportedJvmTargetVersions(Versions.KOTLIN)
    }
}