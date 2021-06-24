// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators


import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.core.*
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.ModuleConfiguratorSetting
import org.jetbrains.kotlin.tools.projectWizard.core.service.JvmTargetVersionsProviderService
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.BuildFileIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.BuildSystemIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.KotlinBuildSystemPluginIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.*
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.maven.MavenPropertyIR
import org.jetbrains.kotlin.tools.projectWizard.phases.GenerationPhase
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.*
import org.jetbrains.kotlin.tools.projectWizard.settings.DisplayableSettingItem
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.ModuleKind

interface JvmModuleConfigurator : ModuleConfiguratorWithTests {
    companion object : ModuleConfiguratorSettings() {
        val targetJvmVersion by enumSetting<TargetJvmVersion>(
            KotlinNewProjectWizardBundle.message("module.configurator.jvm.setting.target.jvm.version"),
            GenerationPhase.PROJECT_GENERATION
        ) {
            description = KotlinNewProjectWizardBundle.message("module.configurator.jvm.setting.target.jvm.version.description")
            defaultValue = value(TargetJvmVersion.JVM_1_8)
            filter = { _, targetJvmVersion ->
                // we need to make sure that kotlin compiler supports this target
                val projectKind = KotlinPlugin.projectKind.settingValue
                val service = service<JvmTargetVersionsProviderService>()
                val jvmTargetVersions = service.listSupportedJvmTargetVersions(projectKind)
                targetJvmVersion in jvmTargetVersions
            }
        }
    }

    override fun getConfiguratorSettings(): List<ModuleConfiguratorSetting<*, *>> = buildList {
        +super.getConfiguratorSettings()
        +targetJvmVersion
    }
}

enum class TargetJvmVersion(@NonNls val value: String) : DisplayableSettingItem {
    JVM_1_8("1.8"),
    JVM_9("9"),
    JVM_10("10"),
    JVM_11("11"),
    JVM_12("12"),
    JVM_13("13"),
    JVM_14("14"),
    JVM_15("15"),
    JVM_16("16");

    override val text: String
        get() = value
}


interface ModuleConfiguratorWithModuleType : ModuleConfigurator {
    val moduleType: ModuleType
}

val ModuleConfigurator.moduleType: ModuleType?
    get() = safeAs<ModuleConfiguratorWithModuleType>()?.moduleType


interface SinglePlatformModuleConfigurator : ModuleConfigurator {
    val needCreateBuildFile: Boolean get() = true
}

interface CustomPlatformModuleConfigurator : ModuleConfigurator {
    fun createPlatformModule(
        writer: Writer,
        moduleConverter: ModulesToIRsConverter,
        module: Module,
        state: ModulesToIrsState
    ): TaskResult<List<BuildFileIR>>
}

object JvmSinglePlatformModuleConfigurator : JvmModuleConfigurator,
    SinglePlatformModuleConfigurator,
    ModuleConfiguratorWithModuleType {
    override val moduleType get() = ModuleType.jvm
    override val moduleKind: ModuleKind get() = ModuleKind.singlePlatformJvm

    @NonNls
    override val suggestedModuleName = "jvm"

    @NonNls
    override val id = "JVM Module"
    override val text = KotlinNewProjectWizardBundle.message("module.configurator.jvm")


    override fun defaultTestFramework(): KotlinTestFramework = KotlinTestFramework.JUNIT5

    override val canContainSubModules = true

    override fun createKotlinPluginIR(configurationData: ModulesToIrConversionData, module: Module): KotlinBuildSystemPluginIR? =
        KotlinBuildSystemPluginIR(
            KotlinBuildSystemPluginIR.Type.jvm,
            version = configurationData.kotlinVersion
        )


    override fun createBuildFileIRs(
        reader: Reader,
        configurationData: ModulesToIrConversionData,
        module: Module
    ): List<BuildSystemIR> =
        buildList {
            +super<JvmModuleConfigurator>.createBuildFileIRs(reader, configurationData, module)
            if (configurationData.buildSystemType == BuildSystemType.GradleKotlinDsl) {
                +GradleImportIR("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
            }

            val targetVersionValue = inContextOfModuleConfigurator(module) {
                reader {
                    JvmModuleConfigurator.targetJvmVersion.reference.settingValue.value
                }
            }
            when (configurationData.buildSystemType) {
                BuildSystemType.GradleKotlinDsl -> {
                    +GradleConfigureTaskIR(
                        GradleByClassTasksAccessIR("KotlinCompile"),
                        irs = listOf(
                            GradleAssignmentIR("kotlinOptions.jvmTarget", GradleStringConstIR(targetVersionValue))
                        )
                    )
                }
                BuildSystemType.GradleGroovyDsl -> {
                    +jvmTargetSetup("compileKotlin", targetVersionValue)
                    +jvmTargetSetup("compileTestKotlin", targetVersionValue)
                }
                BuildSystemType.Maven -> {
                    +MavenPropertyIR("kotlin.compiler.jvmTarget", targetVersionValue)
                }
            }
        }

    private fun jvmTargetSetup(taskName: String, targetVersion: String) = GradleSectionIR(
        taskName,
        irs = listOf(
            GradleAssignmentIR("kotlinOptions.jvmTarget", GradleStringConstIR(targetVersion))
        )
    )
}


val ModuleType.defaultTarget
    get() = when (this) {
        ModuleType.jvm -> JvmTargetConfigurator
        ModuleType.js -> JsBrowserTargetConfigurator
        ModuleType.native -> NativeForCurrentSystemTarget
        ModuleType.common -> CommonTargetConfigurator
        ModuleType.android -> AndroidTargetConfigurator
    }