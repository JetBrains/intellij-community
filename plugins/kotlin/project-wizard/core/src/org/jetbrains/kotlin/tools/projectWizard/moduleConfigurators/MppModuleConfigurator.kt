// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.core.TaskResult
import org.jetbrains.kotlin.tools.projectWizard.core.Writer
import org.jetbrains.kotlin.tools.projectWizard.core.compute
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.ModuleConfiguratorSetting
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.KotlinBuildSystemPluginIR
import org.jetbrains.kotlin.tools.projectWizard.phases.GenerationPhase
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.gradle.GradlePlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModulesToIrConversionData
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.ModuleKind
import java.nio.file.Path

object MppModuleConfigurator : ModuleConfigurator,
    ModuleConfiguratorWithSettings,
    ModuleConfiguratorWithTests,
    ModuleConfiguratorSettings() {

    @OptIn(ExperimentalStdlibApi::class)
    override fun getConfiguratorSettings(): List<ModuleConfiguratorSetting<*, *>> = buildList {
        addAll(super<ModuleConfiguratorWithTests>.getConfiguratorSettings())
    }

    override fun defaultTestFramework(): KotlinTestFramework {
        return KotlinTestFramework.COMMON
    }

    override val moduleKind = ModuleKind.multiplatform

    @NonNls
    override val suggestedModuleName = "shared"

    @NonNls
    override val id = "multiplatform"
    override val text = KotlinNewProjectWizardBundle.message("module.configurator.mpp")
    override val canContainSubModules = true

    override fun createKotlinPluginIR(configurationData: ModulesToIrConversionData, module: Module): KotlinBuildSystemPluginIR? =
        KotlinBuildSystemPluginIR(
            KotlinBuildSystemPluginIR.Type.multiplatform,
            version = configurationData.kotlinVersion
        )

    // TODO remove when be removed in KMM wizard
    val generateTests by booleanSetting("Generate Tests", GenerationPhase.PROJECT_GENERATION) {
        defaultValue = value(false)
    }

    override fun Writer.runArbitraryTask(
        configurationData: ModulesToIrConversionData,
        module: Module,
        modulePath: Path,
    ): TaskResult<Unit> = compute {
        if (shouldApplyHmppGradleProperties(configurationData)) {
            GradlePlugin.gradleProperties.addValues("kotlin.mpp.enableGranularSourceSetsMetadata" to true)
            GradlePlugin.gradleProperties.addValues("kotlin.native.enableDependencyPropagation" to false)
        }
    }

    private fun shouldApplyHmppGradleProperties(configurationData: ModulesToIrConversionData): Boolean {
        val kotlinVersionText = configurationData.kotlinVersion.version.text
        val kotlinVersion = kotlinVersionFromString(kotlinVersionText) ?: return false
        return kotlinVersion < HMPP_BY_DEFAULT_VERSION
    }

    private val KOTLIN_COMPILER_VERSION_PATTERN = "(\\d+)\\.(\\d+)(?:\\.(\\d+))?.*".toRegex()

    // basically a duplicate of the org.jetbrains.kotlin.idea.versions.KotlinRuntimeLibraryUtilKt.fromString
    // I've copied it, so we won't have the whole kotlin.idea module in the dependencies.
    private fun kotlinVersionFromString(version: String): KotlinVersion? {
        val (major, minor, patch) = KOTLIN_COMPILER_VERSION_PATTERN.matchEntire(version)?.destructured ?: return null
        val majorValue = major.toIntOrNull() ?: return null
        val minorValue = minor.toIntOrNull() ?: return null
        val patchValue = patch.toIntOrNull() ?: 0
        return KotlinVersion(majorValue, minorValue, patchValue)
    }

    private val HMPP_BY_DEFAULT_VERSION = KotlinVersion(1, 6, 20)
}
