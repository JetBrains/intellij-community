// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.templates.compose

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.core.*
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.*
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.GradleImportIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.irsList
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.*
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.*
import org.jetbrains.kotlin.tools.projectWizard.templates.*

object ComposeWebModuleTemplate : Template() {
    @NonNls
    override val id: String = "composeWebTemplate"

    override val title: String = KotlinNewProjectWizardBundle.message("module.template.compose.web.title")
    override val description: String = KotlinNewProjectWizardBundle.message("module.template.compose.web.description")

    override fun isApplicableTo(module: Module, projectKind: ProjectKind, reader: Reader): Boolean =
        module.configurator.moduleType == ModuleType.js
                && projectKind == ProjectKind.COMPOSE
                && when (module.configurator) {
                    JsComposeMppConfigurator -> true
                    else -> false
                }

    override fun Writer.getIrsToAddToBuildFile(
        module: ModuleIR
    ) = irsList {
        +RepositoryIR(Repositories.JETBRAINS_COMPOSE_DEV)
        +RepositoryIR(DefaultRepository.GOOGLE)
        +GradleOnlyPluginByNameIR("org.jetbrains.compose", version = Versions.JETBRAINS_COMPOSE)
        +GradleImportIR("org.jetbrains.compose.compose")
    }

    override fun Writer.getRequiredLibraries(module: ModuleIR): List<DependencyIR> = listOf(
        CustomGradleDependencyDependencyIR("compose.web.core", dependencyType = DependencyType.MAIN, DependencyKind.implementation),
        CustomGradleDependencyDependencyIR("compose.runtime", dependencyType = DependencyType.MAIN, DependencyKind.implementation),
        CustomGradleDependencyDependencyIR("kotlin(\"test-js\")", dependencyType = DependencyType.TEST, DependencyKind.implementation)
    )

    override fun Writer.runArbitratyTask(module: ModuleIR): TaskResult<Unit> =
        BuildSystemPlugin.pluginRepositoreis.addValues(Repositories.JETBRAINS_COMPOSE_DEV)

    override fun Reader.getFileTemplates(module: ModuleIR) = buildList<FileTemplateDescriptorWithPath> {
        +(FileTemplateDescriptor("$id/main.kt", "Main.kt".asPath()) asSrcOf SourcesetType.main)
        +(FileTemplateDescriptor("$id/index.html.vm") asResourceOf SourcesetType.main)
    }
}
