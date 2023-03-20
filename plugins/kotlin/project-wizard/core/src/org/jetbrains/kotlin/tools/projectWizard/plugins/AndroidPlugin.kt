// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.plugins

import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.core.*
import org.jetbrains.kotlin.tools.projectWizard.core.entity.PipelineTask
import org.jetbrains.kotlin.tools.projectWizard.core.entity.properties.Property
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.PluginSetting
import org.jetbrains.kotlin.tools.projectWizard.core.service.FileSystemWizardService
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.AndroidModuleConfigurator
import org.jetbrains.kotlin.tools.projectWizard.phases.GenerationPhase
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.allIRModules
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.gradle.GradlePlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.KotlinPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.withAllSubModules

class AndroidPlugin(context: Context) : Plugin(context) {
    override val path = pluginPath

    override val settings: List<PluginSetting<*, *>> = listOf(
        androidSdkPath,
    )
    override val pipelineTasks: List<PipelineTask> = listOf(
        addAndroidSdkToLocalProperties
    )
    override val properties: List<Property<*>> = listOf()

    companion object : PluginSettingsOwner() {
        override val pluginPath: String = "android"

        val androidSdkPath by pathSetting(
            KotlinNewProjectWizardBundle.message("plugin.android.setting.sdk"),
            neededAtPhase = GenerationPhase.PROJECT_GENERATION,
        ) {
            tooltipText=KotlinNewProjectWizardBundle.message("plugin.android.setting.sdk.tooltip")
            isSavable = true
            isAvailable = isAndroidContainingProject
            defaultValue = dynamic { AndroidSdkProvider.EP_NAME.extensions.firstNotNullOfOrNull { it.getSdkPath() } }
            shouldExists()
        }

        private val isAndroidContainingProject = checker {
            KotlinPlugin.modules.settingValue
                .withAllSubModules(includeSourcesets = true)
                .any { it.configurator is AndroidModuleConfigurator }
        }

        val addAndroidSdkToLocalProperties by pipelineTask(GenerationPhase.PROJECT_GENERATION) {
            runBefore(GradlePlugin.createLocalPropertiesFile)
            runAfter(KotlinPlugin.createModules)
            isAvailable = isAndroidContainingProject
            withAction {
                if (allIRModules.none { it.originalModule.configurator is AndroidModuleConfigurator }) return@withAction UNIT_SUCCESS
                val path = androidSdkPath.settingValue
                val fileSystemService = service<FileSystemWizardService>()
                GradlePlugin.localProperties.addValues(
                    "sdk.dir" to fileSystemService.renderPath(path)
                )
            }
        }

    }
}