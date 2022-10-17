// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.core.service

import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.DefaultRepository
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Repositories
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Repository
import org.jetbrains.kotlin.tools.projectWizard.settings.version.Version

abstract class KotlinVersionProviderService : WizardService {
    abstract fun getKotlinVersion(projectKind: ProjectKind): WizardKotlinVersion

    protected fun kotlinVersionWithDefaultValues(version: Version) = WizardKotlinVersion(
        version,
        getKotlinVersionKind(version),
        getKotlinVersionRepository(version),
        getBuildSystemPluginRepository(getKotlinVersionKind(version), listOf(getDevVersionRepository())),
    )

    private fun getKotlinVersionRepository(versionKind: KotlinVersionKind): Repository = when (versionKind) {
        KotlinVersionKind.STABLE, KotlinVersionKind.EAP, KotlinVersionKind.M -> DefaultRepository.MAVEN_CENTRAL
        KotlinVersionKind.DEV -> getDevVersionRepository()
    }

    protected open fun getDevVersionRepository(): Repository = Repositories.JETBRAINS_KOTLIN_DEV

    protected fun getKotlinVersionRepository(version: Version) =
        getKotlinVersionRepository(getKotlinVersionKind(version))

    protected fun getKotlinVersionKind(version: Version) = when {
        "eap" in version.toString().toLowerCase() -> KotlinVersionKind.EAP
        "rc" in version.toString().toLowerCase() -> KotlinVersionKind.EAP
        "dev" in version.toString().toLowerCase() -> KotlinVersionKind.DEV
        "m" in version.toString().toLowerCase() -> KotlinVersionKind.M
        else -> KotlinVersionKind.STABLE
    }

    companion object {
        fun getBuildSystemPluginRepository(
            versionKind: KotlinVersionKind,
            devRepositories: List<Repository>
        ): (BuildSystemType) -> List<Repository> =
            when (versionKind) {
                KotlinVersionKind.STABLE, KotlinVersionKind.EAP, KotlinVersionKind.M -> { buildSystem ->
                    when (buildSystem) {
                        BuildSystemType.GradleKotlinDsl, BuildSystemType.GradleGroovyDsl -> listOf(DefaultRepository.GRADLE_PLUGIN_PORTAL)
                        BuildSystemType.Maven -> listOf(DefaultRepository.MAVEN_CENTRAL)
                        BuildSystemType.Jps -> emptyList()
                    }
                }
                KotlinVersionKind.DEV -> { _ -> devRepositories }
            }
    }
}