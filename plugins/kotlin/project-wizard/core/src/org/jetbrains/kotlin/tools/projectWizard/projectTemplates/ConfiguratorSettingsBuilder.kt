// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.projectTemplates

import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.ModuleConfiguratorSetting
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.SettingType
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.ModuleBasedConfiguratorContext
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.ModuleConfigurator
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.ModuleConfiguratorContext
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module

class ConfiguratorSettingsBuilder<C : ModuleConfigurator>(
  val module: Module,
  val configurator: C
) : ModuleConfiguratorContext by ModuleBasedConfiguratorContext(configurator, module) {
    init {
        assert(module.configurator === configurator)
    }

    private val settings = mutableListOf<SettingWithValue<*, *>>()
    val setsSettings: List<SettingWithValue<*, *>>
        get() = settings

    infix fun <V : Any, T : SettingType<V>> ModuleConfiguratorSetting<V, T>.withValue(value: V) {
        settings += SettingWithValue(reference, value)
    }

}