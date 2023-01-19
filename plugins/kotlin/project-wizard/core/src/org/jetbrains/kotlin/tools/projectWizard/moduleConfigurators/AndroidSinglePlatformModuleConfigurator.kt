// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators


import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.core.*
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.*
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.AndroidConfigIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.BuildScriptDependencyIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.BuildScriptRepositoryIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.irsList
import org.jetbrains.kotlin.tools.projectWizard.library.MavenArtifact
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.gradle.GradlePlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModulesToIrConversionData
import org.jetbrains.kotlin.tools.projectWizard.plugins.templates.TemplatesPlugin
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.DefaultRepository
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.ModuleKind
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.ModuleReference
import org.jetbrains.kotlin.tools.projectWizard.settings.javaPackage
import org.jetbrains.kotlin.tools.projectWizard.templates.FileTemplate
import java.nio.file.Path

abstract class AndroidSinglePlatformModuleConfiguratorBase :
    SinglePlatformModuleConfigurator,
    AndroidModuleConfigurator {
    override val moduleKind: ModuleKind get() = ModuleKind.singlePlatformAndroid

    @NonNls
    override val id = "android"

    @NonNls
    override val suggestedModuleName = "android"
    override val text = KotlinNewProjectWizardBundle.message("module.configurator.android")

    override val requiresRootBuildFile: Boolean = true

    override val resourcesDirectoryName: String = "res"
    override val kotlinDirectoryName: String = "java"


    override fun createRootBuildFileIrs(configurationData: ModulesToIrConversionData): List<BuildSystemIR> = irsList {
        (listOf(
            DefaultRepository.GRADLE_PLUGIN_PORTAL,
            DefaultRepository.GOOGLE,
        ) + configurationData.kotlinVersion.repositories).forEach { repository ->
            +BuildScriptRepositoryIR(RepositoryIR(repository))
        }

        irsList {
            "classpath"(const("org.jetbrains.kotlin:kotlin-gradle-plugin:${configurationData.kotlinVersion.version}"))
            "classpath"(const("com.android.tools.build:gradle:${Versions.GRADLE_PLUGINS.ANDROID}"))
        }.forEach {
            +BuildScriptDependencyIR(it)
        }
    }

    override fun createBuildFileIRs(reader: Reader, configurationData: ModulesToIrConversionData, module: Module) = irsList {
        +super<AndroidModuleConfigurator>.createBuildFileIRs(reader, configurationData, module)
        +RepositoryIR((DefaultRepository.GOOGLE))
        +AndroidConfigIR(
            javaPackage = module.javaPackage(configurationData.pomIr),
            isApplication = true,
            useCompose = useCompose,
        )
    }

    protected open val useCompose: Boolean
        get() = true

    override fun createKotlinPluginIR(configurationData: ModulesToIrConversionData, module: Module): KotlinBuildSystemPluginIR =
        KotlinBuildSystemPluginIR(
            KotlinBuildSystemPluginIR.Type.android,
            version = configurationData.kotlinVersion,
            priority = 2
        )


    override fun createModuleIRs(
        reader: Reader,
        configurationData: ModulesToIrConversionData,
        module: Module
    ): List<BuildSystemIR> = buildList {
        +super<AndroidModuleConfigurator>.createModuleIRs(reader, configurationData, module)
        if (useCompose) {
            +DEPENDENCIES.COMPOSE_UI
            +DEPENDENCIES.COMPOSE_UI_TOOLING
            +DEPENDENCIES.COMPOSE_UI_TOOLING_PREVIEW
            +DEPENDENCIES.COMPOSE_FOUNDATION
            +DEPENDENCIES.COMPOSE_MATERIAL
            +DEPENDENCIES.ACTIVITY
        }
        else {
            +DEPENDENCIES.MATERIAL
            +DEPENDENCIES.APP_COMPAT
            +DEPENDENCIES.CONSTRAINT_LAYOUT
        }
    }

    override fun Writer.runArbitraryTask(
        configurationData: ModulesToIrConversionData,
        module: Module,
        modulePath: Path
    ): TaskResult<Unit> = doRunArbitraryTask(configurationData, module, modulePath)

    fun Writer.doRunArbitraryTask(
        configurationData: ModulesToIrConversionData,
        module: Module,
        modulePath: Path
    ): TaskResult<Unit> = computeM {
        val sharedModule = module.dependencies
            .map { if (it is ModuleReference.ByModule) it.module else null }
            .firstOrNull { it?.configurator == MppModuleConfigurator }

        val javaPackage = module.javaPackage(configurationData.pomIr)
        val sharedPackage = sharedModule?.javaPackage(configurationData.pomIr)

        val settings = mapOf(
            "package" to javaPackage.asCodePackage(),
            "sharedPackage" to sharedPackage?.asCodePackage()
        )

        val files = mutableListOf(
            FileTemplate(AndroidModuleConfigurator.FileTemplateDescriptors.androidManifestXml, modulePath, settings),
            FileTemplate(AndroidModuleConfigurator.FileTemplateDescriptors.stylesXml(useCompose), modulePath, settings),
            FileTemplate(AndroidModuleConfigurator.FileTemplateDescriptors.mainActivityKt(javaPackage, useCompose), modulePath, settings)
        )
        if (useCompose) {
            files.add(FileTemplate(AndroidModuleConfigurator.FileTemplateDescriptors.myApplicationThemeKt(javaPackage), modulePath, settings))
        }
        else {
            files.add(FileTemplate(AndroidModuleConfigurator.FileTemplateDescriptors.activityMainXml, modulePath, settings))
            files.add(FileTemplate(AndroidModuleConfigurator.FileTemplateDescriptors.colorsXml, modulePath, settings))
        }
        TemplatesPlugin.addFileTemplates.execute(files)
        GradlePlugin.gradleProperties.addValues("android.useAndroidX" to true)
        GradlePlugin.gradleProperties.addValues("android.nonTransitiveRClass" to true)
    }

    override fun Reader.createAndroidPlugin(module: Module): AndroidGradlePlugin =
        AndroidGradlePlugin.APPLICATION


    object DEPENDENCIES {
        val COMPOSE_UI = ArtifactBasedLibraryDependencyIR(
            MavenArtifact(DefaultRepository.GOOGLE, "androidx.compose.ui", "ui"),
            version = Versions.ANDROID.ANDROIDX_COMPOSE,
            dependencyType = DependencyType.MAIN
        )

        val COMPOSE_UI_TOOLING = ArtifactBasedLibraryDependencyIR(
            MavenArtifact(DefaultRepository.GOOGLE, "androidx.compose.ui", "ui-tooling"),
            version = Versions.ANDROID.ANDROIDX_COMPOSE,
            dependencyType = DependencyType.MAIN
        )

        val COMPOSE_UI_TOOLING_PREVIEW = ArtifactBasedLibraryDependencyIR(
            MavenArtifact(DefaultRepository.GOOGLE, "androidx.compose.ui", "ui-tooling-preview"),
            version = Versions.ANDROID.ANDROIDX_COMPOSE,
            dependencyType = DependencyType.MAIN
        )

        val COMPOSE_FOUNDATION = ArtifactBasedLibraryDependencyIR(
            MavenArtifact(DefaultRepository.GOOGLE, "androidx.compose.foundation", "foundation"),
            version = Versions.ANDROID.ANDROIDX_COMPOSE,
            dependencyType = DependencyType.MAIN
        )

        val COMPOSE_MATERIAL = ArtifactBasedLibraryDependencyIR(
            MavenArtifact(DefaultRepository.GOOGLE, "androidx.compose.material", "material"),
            version = Versions.ANDROID.ANDROIDX_COMPOSE,
            dependencyType = DependencyType.MAIN
        )

        val ACTIVITY = ArtifactBasedLibraryDependencyIR(
            MavenArtifact(DefaultRepository.GOOGLE, "androidx.activity", "activity-compose"),
            version = Versions.ANDROID.ANDROIDX_ACTIVITY,
            dependencyType = DependencyType.MAIN
        )

        val MATERIAL = ArtifactBasedLibraryDependencyIR(
            MavenArtifact(DefaultRepository.GOOGLE, "com.google.android.material", "material"),
            version = Versions.ANDROID.ANDROID_MATERIAL,
            dependencyType = DependencyType.MAIN
        )

        val CONSTRAINT_LAYOUT = ArtifactBasedLibraryDependencyIR(
            MavenArtifact(DefaultRepository.GOOGLE, "androidx.constraintlayout", "constraintlayout"),
            version = Versions.ANDROID.ANDROIDX_CONSTRAINTLAYOUT,
            dependencyType = DependencyType.MAIN
        )

        val APP_COMPAT = ArtifactBasedLibraryDependencyIR(
            MavenArtifact(DefaultRepository.GOOGLE, "androidx.appcompat", "appcompat"),
            version = Versions.ANDROID.ANDROIDX_APPCOMPAT,
            dependencyType = DependencyType.MAIN
        )
    }
}

object AndroidWithoutComposeSinglePlatformModuleConfigurator : AndroidSinglePlatformModuleConfiguratorBase() {
    override val useCompose: Boolean
        get() = false
}

object AndroidSinglePlatformModuleConfigurator : AndroidSinglePlatformModuleConfiguratorBase()