// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.core.Reader
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.ModuleConfiguratorSetting
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.BuildSystemIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.irsList
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.multiplatform.DefaultTargetConfigurationIR
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.JsBrowserBasedConfigurator.Companion.browserSubTarget
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleSubType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleType
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module

object WasmTargetConfigurator : TargetConfigurator, ModuleConfiguratorWithSettings {
    @NonNls
    override val id = "wasmSimple"

    override val moduleType: ModuleType get() = ModuleType.wasm

    override val text = KotlinNewProjectWizardBundle.message("module.configurator.wasm.simple")

    override fun getConfiguratorSettings(): List<ModuleConfiguratorSetting<*, *>> =
        super.getConfiguratorSettings() + JSConfigurator.kind

    override fun Reader.createTargetIrs(
        module: Module
    ): List<BuildSystemIR> = irsList {
        +DefaultTargetConfigurationIR(
            module.createTargetAccessIr(
                ModuleSubType.wasm
            )
        ) {
            browserSubTarget(
                module,
                this@createTargetIrs,
                cssSupportNeeded = false
            )
        }
    }
}