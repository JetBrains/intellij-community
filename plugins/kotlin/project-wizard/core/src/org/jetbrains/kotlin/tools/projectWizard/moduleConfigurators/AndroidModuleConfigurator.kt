// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators


import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.Dependencies
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.core.*
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.ModuleConfiguratorSetting
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.PluginSettingReference
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.SettingType
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.reference
import org.jetbrains.kotlin.tools.projectWizard.core.service.WizardKotlinVersion
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.*
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.AndroidConfigIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.irsList
import org.jetbrains.kotlin.tools.projectWizard.phases.GenerationPhase
import org.jetbrains.kotlin.tools.projectWizard.plugins.AndroidPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.gradle.GradlePlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.KotlinPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleSubType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModulesToIrConversionData
import org.jetbrains.kotlin.tools.projectWizard.settings.DisplayableSettingItem
import org.jetbrains.kotlin.tools.projectWizard.settings.JavaPackage
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.DefaultRepository
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Repository
import org.jetbrains.kotlin.tools.projectWizard.settings.javaPackage
import org.jetbrains.kotlin.tools.projectWizard.templates.FileTemplateDescriptor
import java.nio.file.Path

interface AndroidModuleConfigurator : ModuleConfigurator,
    ModuleConfiguratorWithSettings,
    ModuleConfiguratorWithModuleType,
    GradleModuleConfigurator {

    override val moduleType: ModuleType
        get() = ModuleType.android

    override fun getPluginSettings(): List<PluginSettingReference<Any, SettingType<Any>>> =
        listOf(AndroidPlugin.androidSdkPath.reference)

    override fun createBuildFileIRs(
        reader: Reader,
        configurationData: ModulesToIrConversionData,
        module: Module
    ) = buildList<BuildSystemIR> {
        +GradleOnlyPluginByNameIR(reader.createAndroidPlugin(module).pluginName, priority = 1)
    }

    fun Reader.createAndroidPlugin(module: Module): AndroidGradlePlugin

    override fun createSettingsGradleIRs(
        reader: Reader,
        module: Module,
        data: ModulesToIrConversionData
    ) = buildList<BuildSystemIR> {
        +createRepositories(reader { KotlinPlugin.version.propertyValue })
            .map { PluginManagementRepositoryIR(RepositoryIR(it)) }
    }

    override fun createStdlibType(configurationData: ModulesToIrConversionData, module: Module): StdlibType? =
        StdlibType.StdlibJdk7

    object FileTemplateDescriptors {
        val androidManifestXml = FileTemplateDescriptor(
            "android/AndroidManifest.xml",
            "src" / "main" / "AndroidManifest.xml"
        )

        val activityMainXml = FileTemplateDescriptor(
            "android/activity_main.xml.vm",
            "src" / "main" / "res" / "layout" / "activity_main.xml"
        )

        val colorsXml = FileTemplateDescriptor(
            "android/colors.xml",
            "src" / "main" / "res" / "values" / "colors.xml"
        )

        fun stylesXml(compose: Boolean) = FileTemplateDescriptor(
            "android/styles${if (compose) "Compose" else ""}.xml",
            "src" / "main" / "res" / "values" / "styles.xml"
        )

        fun mainActivityKt(javaPackage: JavaPackage, compose: Boolean) = FileTemplateDescriptor(
            "android/MainActivity${if (compose) "Compose" else ""}.kt.vm",
            "src" / "main" / "java" / javaPackage.asPath() / "MainActivity.kt"
        )

        fun myApplicationThemeKt(javaPackage: JavaPackage) = FileTemplateDescriptor(
            "android/MyApplicationTheme.kt.vm",
            "src" / "main" / "java" / javaPackage.asPath() / "MyApplicationTheme.kt"
        )
    }

    companion object {
        fun createRepositories(kotlinVersion: WizardKotlinVersion) = buildList<Repository> {
            +DefaultRepository.GRADLE_PLUGIN_PORTAL
            +DefaultRepository.GOOGLE
            +kotlinVersion.repositories
        }
    }
}

object AndroidTargetConfigurator : AndroidTargetConfiguratorBase(),
                                   ModuleConfiguratorWithTests {
    override fun defaultTestFramework(): KotlinTestFramework = KotlinTestFramework.JUNIT4

    override fun skipResolutionStrategy(data: ModulesToIrConversionData) =
        data.allModules.any { module ->
            module.configurator is AndroidSinglePlatformModuleConfiguratorBase ||
                    module.configurator is MppModuleConfigurator &&
                    module.subModules.any { subModule ->
                        subModule.configurator is AndroidTargetConfiguratorBase &&
                                subModule.configurator != this &&
                                subModule.configurator.skipResolutionStrategy(data)
                    }
        }

    override fun getConfiguratorSettings() = buildList<ModuleConfiguratorSetting<*, *>> {
        +super<AndroidTargetConfiguratorBase>.getConfiguratorSettings()
        +JvmModuleConfigurator.testFramework
    }

    override fun createModuleIRs(reader: Reader, configurationData: ModulesToIrConversionData, module: Module): List<BuildSystemIR> =
        buildList {
            +super<ModuleConfiguratorWithTests>.createModuleIRs(reader, configurationData, module)
            +super<AndroidTargetConfiguratorBase>.createModuleIRs(reader, configurationData, module)
            +ArtifactBasedLibraryDependencyIR(
                Dependencies.JUNIT,
                dependencyType = DependencyType.TEST
            )
        }

    override fun createBuildFileIRs(reader: Reader, configurationData: ModulesToIrConversionData, module: Module): List<BuildSystemIR> =
        buildList {
            +super<AndroidTargetConfiguratorBase>.createBuildFileIRs(reader, configurationData, module)
            +super<ModuleConfiguratorWithTests>.createBuildFileIRs(reader, configurationData, module)
        }
}

abstract class AndroidTargetConfiguratorBase : TargetConfigurator,
    SimpleTargetConfigurator,
    AndroidModuleConfigurator,
    SingleCoexistenceTargetConfigurator,
    ModuleConfiguratorSettings() {
    override val moduleSubType = ModuleSubType.android
    override val moduleType = ModuleType.android

    override val text = KotlinNewProjectWizardBundle.message("module.configurator.android")

    override fun Reader.createAndroidPlugin(module: Module): AndroidGradlePlugin =
        inContextOfModuleConfigurator(module) { androidPlugin.reference.settingValue }

    override fun getConfiguratorSettings() = buildList<ModuleConfiguratorSetting<*, *>> {
        +super.getConfiguratorSettings()
        +androidPlugin
    }

    override fun Writer.runArbitraryTask(
        configurationData: ModulesToIrConversionData,
        module: Module,
        modulePath: Path
    ): TaskResult<Unit> =
        GradlePlugin.gradleProperties.addValues("android.useAndroidX" to true)

    override fun createSettingsGradleIRs(
        reader: Reader,
        module: Module,
        data: ModulesToIrConversionData
    ): List<BuildSystemIR> = irsList {
        +super.createSettingsGradleIRs(reader, module, data)
        if (!skipResolutionStrategy(data)) {
            +AndroidResolutionStrategyIR(Versions.GRADLE_PLUGINS.ANDROID)
        }
    }

    open fun skipResolutionStrategy(data: ModulesToIrConversionData) = true

    override fun createBuildFileIRs(reader: Reader, configurationData: ModulesToIrConversionData, module: Module): List<BuildSystemIR> =
        buildList {
            +super<AndroidModuleConfigurator>.createBuildFileIRs(reader, configurationData, module)
            +RepositoryIR(DefaultRepository.GOOGLE)
            +AndroidConfigIR(
                javaPackage = module.javaPackage(configurationData.pomIr),
                isApplication = reader.createAndroidPlugin(module) == AndroidGradlePlugin.APPLICATION,
                useCompose = false,
            )
        }

    val androidPlugin by enumSetting<AndroidGradlePlugin>(
        KotlinNewProjectWizardBundle.message("module.configurator.android.setting.android.plugin"),
        neededAtPhase = GenerationPhase.PROJECT_GENERATION
    ) {
        tooltipText = KotlinNewProjectWizardBundle.message("module.configurator.android.setting.android.plugin.tooltip")
    }
}

enum class AndroidGradlePlugin(override val text: String, @NonNls val pluginName: String) : DisplayableSettingItem {
    APPLICATION(
        KotlinNewProjectWizardBundle.message("module.configurator.android.setting.android.plugin.application"),
        "com.android.application"
    ),
    LIBRARY(
        KotlinNewProjectWizardBundle.message("module.configurator.android.setting.android.plugin.library"),
        "com.android.library"
    )
}
