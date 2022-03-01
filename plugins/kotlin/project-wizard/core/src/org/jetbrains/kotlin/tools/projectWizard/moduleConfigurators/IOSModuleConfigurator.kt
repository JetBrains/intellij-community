// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.core.*
import org.jetbrains.kotlin.tools.projectWizard.core.entity.properties.ModuleConfiguratorProperty
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.gradle.GradlePlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModulesToIrConversionData
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.*
import org.jetbrains.kotlin.tools.projectWizard.templates.FileTemplate
import org.jetbrains.kotlin.tools.projectWizard.templates.FileTemplateDescriptor
import java.nio.file.Path

object IOSSinglePlatformModuleConfigurator : IOSSinglePlatformModuleConfiguratorBase() {
    override fun Writer.runArbitraryTask(
        configurationData: ModulesToIrConversionData,
        module: Module,
        modulePath: Path
    ): TaskResult<Unit> =
        GradlePlugin.gradleProperties.addValues("xcodeproj" to "./${module.name}")

    override val moduleTemplatePath: String get() = "singleplatformProject"

    override fun ListBuilder<FileTemplate>.additionalTemplates(fileTemplate: (Path) -> FileTemplate) {
        +fileTemplate("$DEFAULT_APP_NAME.xcodeproj" / "project.pbxproj")

        +fileTemplate(DEFAULT_APP_NAME / "Info.plist")
        +fileTemplate("${DEFAULT_APP_NAME}Tests" / "Info.plist")

        +fileTemplate("${DEFAULT_APP_NAME}UITests" / "Info.plist")
        +fileTemplate("${DEFAULT_APP_NAME}UITests" / "${DEFAULT_APP_NAME}UITests.swift")
    }
}

object IOSSinglePlatformCocoaPodsModuleConfigurator : IOSSinglePlatformModuleConfiguratorBase() {
    override val moduleTemplatePath: String get() = "singleplatformCocoaPodsProject"

    override fun Writer.runArbitraryTask(
        configurationData: ModulesToIrConversionData,
        module: Module,
        modulePath: Path
    ): TaskResult<Unit> = compute {
        GradlePlugin.gradleProperties.addValues("xcodeproj" to "./${module.name}")
        GradlePlugin.gradleProperties.addValues("kotlin.native.cocoapods.generate.wrapper" to true)
    }

    override fun Reader.createTemplates(
        configurationData: ModulesToIrConversionData,
        module: Module,
        modulePath: Path
    ): List<FileTemplate> {
        val settings = createTemplatesSettingValues(module)

        fun fileTemplate(path: Path) = FileTemplate(descriptor(path, module.name), modulePath, settings)

        return buildList {
            +fileTemplate(DEFAULT_APP_NAME / "$DEFAULT_APP_NAME.swift.vm")
            +fileTemplate(DEFAULT_APP_NAME / "ContentView.swift.vm")

            +fileTemplate(DEFAULT_APP_NAME / "Assets.xcassets" / "Contents.json")
            +fileTemplate(DEFAULT_APP_NAME / "Assets.xcassets" / "AppIcon.appiconset" / "Contents.json")
            +fileTemplate(DEFAULT_APP_NAME / "Assets.xcassets" / "AccentColor.colorset" / "Contents.json")
            +fileTemplate(DEFAULT_APP_NAME / "Preview Content" / "Preview Assets.xcassets" / "Contents.json")

            +fileTemplate("$DEFAULT_APP_NAME.xcodeproj" / "project.pbxproj")
            +fileTemplate(DEFAULT_APP_NAME / "Info.plist")

            +fileTemplate("Podfile".asPath())
        }
    }
}

abstract class IOSSinglePlatformModuleConfiguratorBase : SinglePlatformModuleConfigurator,
    ModuleConfiguratorSettings(),
    ModuleConfiguratorProperties,
    ModuleConfiguratorWithProperties {

    @NonNls
    override val id = "IOS Module"

    @NonNls
    override val suggestedModuleName = "ios"

    override val moduleKind: ModuleKind = ModuleKind.singlePlatformJvm
    override val greyText = KotlinNewProjectWizardBundle.message("module.configurator.ios.requires.xcode")
    override val text = KotlinNewProjectWizardBundle.message("module.configurator.ios")

    abstract val moduleTemplatePath: String?

    override val needCreateBuildFile: Boolean = false
    override val requiresRootBuildFile: Boolean = true

    override fun Reader.createTemplates(
        configurationData: ModulesToIrConversionData,
        module: Module,
        modulePath: Path
    ): List<FileTemplate> {
        val settings = createTemplatesSettingValues(module)

        fun fileTemplate(path: Path) = FileTemplate(descriptor(path, module.name), modulePath, settings)

        return buildList {
            +fileTemplate(DEFAULT_APP_NAME / "AppDelegate.swift")
            +fileTemplate(DEFAULT_APP_NAME / "ContentView.swift.vm")
            +fileTemplate(DEFAULT_APP_NAME / "SceneDelegate.swift")

            +fileTemplate(DEFAULT_APP_NAME / "Base.lproj" / "LaunchScreen.storyboard")

            +fileTemplate(DEFAULT_APP_NAME / "Assets.xcassets" / "Contents.json")
            +fileTemplate(DEFAULT_APP_NAME / "Assets.xcassets" / "AppIcon.appiconset" / "Contents.json")
            +fileTemplate(DEFAULT_APP_NAME / "Preview_Content" / "Preview_Assets.xcassets" / "Contents.json")

            +fileTemplate("${DEFAULT_APP_NAME}Tests" / "${DEFAULT_APP_NAME}Tests.swift.vm")

            additionalTemplates(::fileTemplate)
        }
    }

    open fun ListBuilder<FileTemplate>.additionalTemplates(fileTemplate: (Path) -> FileTemplate) { }

    protected fun Reader.createTemplatesSettingValues(module: Module): Map<String, Any?> {
        val dependentModule = inContextOfModuleConfigurator(module) {
            dependentModule.reference.propertyValue.module
        }

        return mapOf(
            "moduleName" to module.name,
            "sharedModuleName" to dependentModule?.name
        )
    }

    protected open fun descriptor(path: Path, moduleName: String) =
        FileTemplateDescriptor(
            "ios/$moduleTemplatePath/$path",
            path.toString()
                .removeSuffix(".vm")
                .replace(DEFAULT_APP_NAME, moduleName)
                .replace('_', ' ')
                .asPath()
        )

    companion object {
        @NonNls
        const val DEFAULT_APP_NAME = "appName"
    }

    @Suppress("LeakingThis")
    val dependentModule by property(DependentModuleReference.EMPTY)

    override fun getConfiguratorProperties(): List<ModuleConfiguratorProperty<*>> =
        listOf(dependentModule)

    data class DependentModuleReference(val module: Module?) {
        companion object {
            val EMPTY = DependentModuleReference(module = null)
        }
    }
}
