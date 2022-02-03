// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.templates.compose

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.core.*
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.*
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.GradleImportIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.irsList
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.moduleType
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.plugins.pomIR
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.*
import org.jetbrains.kotlin.tools.projectWizard.settings.javaPackage
import org.jetbrains.kotlin.tools.projectWizard.templates.*

class ComposeJvmDesktopTemplate : Template() {
    @NonNls
    override val id: String = "composeDesktopTemplate"

    override val title: String = KotlinNewProjectWizardBundle.message("module.template.compose.desktop.title")
    override val description: String = KotlinNewProjectWizardBundle.message("module.template.compose.desktop.description")

    override fun isApplicableTo(module: Module, projectKind: ProjectKind, reader: Reader): Boolean =
        module.configurator.moduleType == ModuleType.jvm && projectKind == ProjectKind.COMPOSE
                && (module.kind == ModuleKind.singlePlatformJvm || module.kind == ModuleKind.target)


    override fun Writer.getIrsToAddToBuildFile(
        module: ModuleIR
    ) = irsList {
        +RepositoryIR(Repositories.JETBRAINS_COMPOSE_DEV)
        +RepositoryIR(DefaultRepository.GOOGLE)
        +GradleOnlyPluginByNameIR("org.jetbrains.compose", version = Versions.JETBRAINS_COMPOSE)

        +GradleImportIR("org.jetbrains.compose.desktop.application.dsl.TargetFormat")
        "compose.desktop" {
            "application" {
                "mainClass" assign const("MainKt")

                "nativeDistributions" {
                    "targetFormats"(raw("TargetFormat.Dmg"), raw("TargetFormat.Msi"), raw("TargetFormat.Deb"))
                    "packageName" assign const(module.name)
                    "packageVersion" assign const("1.0.0")
                }
            }
        }

        +GradleImportIR("org.jetbrains.compose.compose")
    }

    override fun Writer.getRequiredLibraries(module: ModuleIR): List<DependencyIR> = listOf(
        CustomGradleDependencyDependencyIR("compose.desktop.currentOs", dependencyType = DependencyType.MAIN, DependencyKind.implementation)
    )

    override fun Writer.runArbitratyTask(module: ModuleIR): TaskResult<Unit> =
        BuildSystemPlugin.pluginRepositoreis.addValues(Repositories.JETBRAINS_COMPOSE_DEV, DefaultRepository.GOOGLE)

    override fun Reader.getFileTemplates(module: ModuleIR) = buildList<FileTemplateDescriptorWithPath> {
        val dependsOnMppModule: Module? =
            module.originalModule.dependencies.map { moduleByReference(it) }.firstOrNull { it.template is ComposeMppModuleTemplate }
        if (dependsOnMppModule == null) {
            +(FileTemplateDescriptor("$id/main.kt", "Main.kt".asPath()) asSrcOf SourcesetType.main)
        } else {
            val javaPackage = dependsOnMppModule.javaPackage(pomIR()).asCodePackage()
            +(FileTemplateDescriptor("composeMpp/main.kt.vm", "Main.kt".asPath())
                    asSrcOf SourcesetType.main
                    withSettings ("sharedPackage" to javaPackage)
                    )
        }
    }
}

class ComposeCommonDesktopTemplate : Template() {
    @NonNls
    override val id: String = "composeCommonDesktopTemplate"

    override val title: String = KotlinNewProjectWizardBundle.message("module.template.compose.desktop.title")
    override val description: String = KotlinNewProjectWizardBundle.message("module.template.compose.desktop.description")

    override fun isApplicableTo(module: Module, projectKind: ProjectKind, reader: Reader): Boolean =
        module.configurator.moduleType == ModuleType.jvm && projectKind == ProjectKind.COMPOSE
}