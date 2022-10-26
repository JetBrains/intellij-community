// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.plugins.projectTemplates

import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.core.*
import org.jetbrains.kotlin.tools.projectWizard.core.entity.PipelineTask
import org.jetbrains.kotlin.tools.projectWizard.core.entity.properties.Property
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.PluginSetting

import org.jetbrains.kotlin.tools.projectWizard.phases.GenerationPhase
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.KotlinPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.withAllSubModules
import org.jetbrains.kotlin.tools.projectWizard.projectTemplates.ProjectTemplate

class ProjectTemplatesPlugin(context: Context) : Plugin(context) {
    override val path = pluginPath

    companion object : PluginSettingsOwner() {
        override val pluginPath = "projectTemplates"

        val template by dropDownSetting<ProjectTemplate>(
            KotlinNewProjectWizardBundle.message("plugin.templates.setting.template"),
            GenerationPhase.INIT_TEMPLATE,
            parser = valueParserM { _, _ ->
                Failure(ParseError(KotlinNewProjectWizardBundle.message("error.text.project.templates.is.not.supported.in.yaml.for.now")))
            },
        ) {
            values = ProjectTemplate.ALL + extensionTemplates
            isRequired = false
            tooltipText = KotlinNewProjectWizardBundle.message("plugin.templates.setting.template.tooltip")
        }

        private val extensionTemplates: List<ProjectTemplate>
            get() = mutableListOf<ProjectTemplate>().also { list ->
                MultiplatformProjectTemplatesProvider.EP_NAME.forEachExtensionSafe { it.addTemplate(list) }
            }
    }

    override val settings: List<PluginSetting<*, *>> = listOf(template)
    override val pipelineTasks: List<PipelineTask> = listOf()
    override val properties: List<Property<*>> = listOf()
}

fun SettingsWriter.applyProjectTemplate(projectTemplate: ProjectTemplate) {
    projectTemplate.setsValues.forEach { (setting, value) ->
        setting.setValue(value)
    }
    KotlinPlugin.modules.settingValue.withAllSubModules(includeSourcesets = true).forEach { module ->
        module.apply { initDefaultValuesForSettings() }
    }
}