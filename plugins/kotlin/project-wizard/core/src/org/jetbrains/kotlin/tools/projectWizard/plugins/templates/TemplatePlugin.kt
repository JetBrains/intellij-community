// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.plugins.templates

import org.jetbrains.kotlin.tools.projectWizard.core.Context
import org.jetbrains.kotlin.tools.projectWizard.core.Plugin
import org.jetbrains.kotlin.tools.projectWizard.core.PluginSettingsOwner
import org.jetbrains.kotlin.tools.projectWizard.core.entity.PipelineTask
import org.jetbrains.kotlin.tools.projectWizard.core.entity.properties.Property
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.PluginSetting

abstract class TemplatePlugin(context: Context) : Plugin(context) {
    override val path = pluginPath

    override val settings: List<PluginSetting<*, *>> = emptyList()
    override val pipelineTasks: List<PipelineTask> = emptyList()
    override val properties: List<Property<*>> = emptyList()

    companion object : PluginSettingsOwner() {
        override val pluginPath = "template"
    }
}
