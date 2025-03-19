// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.templates


import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.Dependencies
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.WizardGradleRunConfiguration
import org.jetbrains.kotlin.tools.projectWizard.WizardRunConfiguration
import org.jetbrains.kotlin.tools.projectWizard.core.Reader
import org.jetbrains.kotlin.tools.projectWizard.core.Writer
import org.jetbrains.kotlin.tools.projectWizard.core.buildList
import org.jetbrains.kotlin.tools.projectWizard.core.div
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.TemplateSetting
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.*
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.moduleType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.plugins.pomIR
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Repositories
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.SourcesetType
import org.jetbrains.kotlin.tools.projectWizard.settings.javaPackage
import org.jetbrains.kotlin.tools.projectWizard.transformers.interceptors.InterceptionPoint

object KtorServerTemplate : Template() {
    override val title: String = KotlinNewProjectWizardBundle.message("module.template.ktor.server.title")
    override val description: String = KotlinNewProjectWizardBundle.message("module.template.ktor.server.description")

    override fun isApplicableTo(module: Module, projectKind: ProjectKind, reader: Reader): Boolean =
        module.configurator.moduleType == ModuleType.jvm

    @NonNls
    override val id: String = "ktorServer"

    private const val fileToCreate = "Server.kt"
    override val filesToOpenInEditor: List<String> = listOf(fileToCreate)

    override fun Writer.getRequiredLibraries(module: ModuleIR): List<DependencyIR> =
        withSettingsOf(module.originalModule) {
            buildList {
                +ArtifactBasedLibraryDependencyIR(Dependencies.KTOR.KTOR_SERVER_NETTY, DependencyType.MAIN)
                +ArtifactBasedLibraryDependencyIR(Dependencies.KTOR.KTOR_SERVER_HTML_BUILDER, DependencyType.MAIN)
                +ArtifactBasedLibraryDependencyIR(
                    Dependencies.KOTLINX.KOTLINX_HTML,
                    DependencyType.MAIN
                )
            }
        }

    override fun Writer.getIrsToAddToBuildFile(module: ModuleIR): List<BuildSystemIR> = buildList {
        +RepositoryIR(Repositories.KTOR)

        val packageName = module.originalModule.javaPackage(pomIR()).asCodePackage()
        +runTaskIrs(mainClass = "$packageName.ServerKt")
    }

    override fun Reader.createRunConfigurations(module: ModuleIR): List<WizardRunConfiguration> = buildList {
        +WizardGradleRunConfiguration(KotlinNewProjectWizardBundle.message("configuration.name.run"), "run", emptyList())
    }

    override fun Reader.getFileTemplates(module: ModuleIR): List<FileTemplateDescriptorWithPath> {
        val packageName = module.originalModule.javaPackage(pomIR()).asCodePackage()
        return listOf(FileTemplateDescriptor("$id/server.kt.vm", packageName / fileToCreate) asSrcOf SourcesetType.main)
    }

    val imports = InterceptionPoint("imports", emptyList<String>())
    val routes = InterceptionPoint("routes", emptyList<String>())
    val elements = InterceptionPoint("elements", emptyList<String>())

    override val interceptionPoints: List<InterceptionPoint<Any>> = listOf(imports, routes, elements)
    override val settings: List<TemplateSetting<*, *>> = listOf()

}