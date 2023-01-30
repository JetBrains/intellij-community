// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.plugins

import org.jetbrains.kotlin.tools.projectWizard.core.Context
import org.jetbrains.kotlin.tools.projectWizard.core.Plugin
import org.jetbrains.kotlin.tools.projectWizard.core.PluginSettingsOwner
import org.jetbrains.kotlin.tools.projectWizard.core.UNIT_SUCCESS
import org.jetbrains.kotlin.tools.projectWizard.core.entity.PipelineTask
import org.jetbrains.kotlin.tools.projectWizard.core.entity.properties.Property
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.PluginSetting
import org.jetbrains.kotlin.tools.projectWizard.core.service.InspectionWizardService
import org.jetbrains.kotlin.tools.projectWizard.phases.GenerationPhase

class InspectionsPlugin(context: Context) : Plugin(context) {
    override val path = pluginPath

    override val settings: List<PluginSetting<*, *>> = emptyList()
    override val pipelineTasks: List<PipelineTask> = listOf(createInspectionTasks)
    override val properties: List<Property<*>> = emptyList()

    companion object : PluginSettingsOwner() {
        override val pluginPath = "inspections"

        val createInspectionTasks by pipelineTask(GenerationPhase.PROJECT_IMPORT) {
            withAction {
                // The service is only available in K1 (yet)
                serviceOrNull<InspectionWizardService>()?.changeInspectionSettings()
                UNIT_SUCCESS
            }
        }
    }
}
