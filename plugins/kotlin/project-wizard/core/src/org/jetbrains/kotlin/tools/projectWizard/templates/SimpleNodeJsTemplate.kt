// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.templates

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.core.*
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.TemplateSetting
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.*
import org.jetbrains.kotlin.tools.projectWizard.library.MavenArtifact
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.JsNodeTargetConfigurator
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.NodeJsSinglePlatformModuleConfigurator
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.moduleType
import org.jetbrains.kotlin.tools.projectWizard.phases.GenerationPhase
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.DefaultRepository.Companion.JCENTER
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.SourcesetType


object SimpleNodeJsTemplate : Template() {
    override val title: String = KotlinNewProjectWizardBundle.message("module.template.simple.nodejs.title")

    override val description: String = KotlinNewProjectWizardBundle.message("module.template.simple.nodejs.description")


    override fun isApplicableTo(module: Module, projectKind: ProjectKind, reader: Reader): Boolean =
        module.configurator.moduleType == ModuleType.js
                && when (module.configurator) {
                    JsNodeTargetConfigurator, NodeJsSinglePlatformModuleConfigurator -> true
                    else -> false
                }

    @NonNls
    override val id: String = "simpleNodeJs"

    private const val mainFile = "Main.kt"
    override val filesToOpenInEditor = listOf(mainFile)

    val useKotlinxNodejs by booleanSetting(
        KotlinNewProjectWizardBundle.message("module.template.simple.nodejs.use.kotlinx.nodejs"),
        GenerationPhase.PROJECT_GENERATION
    ) {
        defaultValue = value(false)
        description = KotlinNewProjectWizardBundle.message("module.template.simple.nodejs.use.kotlinx.nodejs.description")
    }

    override val settings: List<TemplateSetting<*, *>> = listOf(useKotlinxNodejs)

    override fun Writer.getRequiredLibraries(module: ModuleIR): List<DependencyIR> = withSettingsOf(module.originalModule) {
        buildList {
            if (this@SimpleNodeJsTemplate.useKotlinxNodejs.reference.settingValue()) {
                +ArtifactBasedLibraryDependencyIR(
                    MavenArtifact(
                        JCENTER,
                        "org.jetbrains.kotlinx",
                        "kotlinx-nodejs"
                    ),
                    Versions.KOTLINX.KOTLINX_NODEJS,
                    DependencyType.MAIN
                )
            }
        }
    }

    override fun Reader.getFileTemplates(module: ModuleIR): List<FileTemplateDescriptorWithPath> =
        withSettingsOf(module.originalModule) {
            buildList {
                +(FileTemplateDescriptor("$id/main.kt.vm", mainFile.asPath()) asSrcOf SourcesetType.main)
                +(FileTemplateDescriptor("$id/GreetingTest.kt.vm") asSrcOf SourcesetType.test)
            }
        }
}