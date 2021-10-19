// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.templates


import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.ModuleIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.multiplatform.NativeTargetInternalIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.multiplatform.TargetConfigurationIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.withIrs
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleType
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.SourcesetType
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.core.*
import org.jetbrains.kotlin.tools.projectWizard.core.safeAs
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.NativeTargetConfigurator
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.moduleType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module

object NativeConsoleApplicationTemplate : Template() {
    override val title: String = KotlinNewProjectWizardBundle.message("module.template.native.console.title")
    override val description: String = KotlinNewProjectWizardBundle.message("module.template.native.console.description")

    override fun isApplicableTo(module: Module, projectKind: ProjectKind, reader: Reader): Boolean =
        module.configurator.moduleType == ModuleType.native
                && module.configurator.safeAs<NativeTargetConfigurator>()?.isDesktopTarget == true

    override val id: String = "nativeConsoleApp"

    private const val fileToCreate = "Main.kt"
    override val filesToOpenInEditor = listOf(fileToCreate)

    override fun updateTargetIr(module: ModuleIR, targetConfigurationIR: TargetConfigurationIR): TargetConfigurationIR =
        targetConfigurationIR.withIrs(NativeTargetInternalIR("main"))

    override fun Reader.getFileTemplates(module: ModuleIR): List<FileTemplateDescriptorWithPath> = buildList {
        +(FileTemplateDescriptor("$id/main.kt.vm", fileToCreate.asPath()) asSrcOf SourcesetType.main)
    }
}