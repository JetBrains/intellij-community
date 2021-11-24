// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.templates

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.core.Reader
import org.jetbrains.kotlin.tools.projectWizard.core.Writer
import org.jetbrains.kotlin.tools.projectWizard.core.asPath
import org.jetbrains.kotlin.tools.projectWizard.core.buildList
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.BuildSystemIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.ModuleIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.runTaskIrs
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.moduleType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.SourcesetType

object ConsoleJvmApplicationTemplate : Template() {
    @NonNls
    override val id: String = "consoleJvmApp"

    override val title: String = KotlinNewProjectWizardBundle.message("module.template.console.jvm.title")
    override val description: String = KotlinNewProjectWizardBundle.message("module.template.console.jvm.description")

    private const val fileToCreate = "Main.kt"
    override val filesToOpenInEditor = listOf(fileToCreate)

    override fun isApplicableTo(module: Module, projectKind: ProjectKind, reader: Reader): Boolean =
        module.configurator.moduleType == ModuleType.jvm

    override fun Writer.getIrsToAddToBuildFile(
        module: ModuleIR
    ) = buildList<BuildSystemIR> {
        +runTaskIrs("MainKt")
    }

    override fun Reader.getFileTemplates(module: ModuleIR) =
        buildList<FileTemplateDescriptorWithPath> {
            +(FileTemplateDescriptor("$id/main.kt.vm", fileToCreate.asPath()) asSrcOf SourcesetType.main)
        }
}
