// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.templates


import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.WizardGradleRunConfiguration
import org.jetbrains.kotlin.tools.projectWizard.WizardRunConfiguration
import org.jetbrains.kotlin.tools.projectWizard.core.Reader
import org.jetbrains.kotlin.tools.projectWizard.core.Writer
import org.jetbrains.kotlin.tools.projectWizard.core.asPath
import org.jetbrains.kotlin.tools.projectWizard.core.buildList
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.TemplateSetting
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.*
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.multiplatform.TargetConfigurationIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.multiplatform.addWithJavaIntoJvmTarget
import org.jetbrains.kotlin.tools.projectWizard.library.MavenArtifact
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleType
import org.jetbrains.kotlin.tools.projectWizard.transformers.interceptors.InterceptionPoint
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.moduleType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.*

class KtorServerTemplate : Template() {
    override val title: String = KotlinNewProjectWizardBundle.message("module.template.ktor.server.title")
    override val description: String = KotlinNewProjectWizardBundle.message("module.template.ktor.server.description")

    override fun isSupportedByModuleType(module: Module, projectKind: ProjectKind): Boolean =
        module.configurator.moduleType == ModuleType.jvm

    @NonNls
    override val id: String = "ktorServer"

    override fun Writer.getRequiredLibraries(module: ModuleIR): List<DependencyIR> =
        withSettingsOf(module.originalModule) {
            buildList {
                +DEPENDENCIES.KTOR_SERVER_NETTY
                +ktorArtifactDependency("ktor-html-builder")
                +ArtifactBasedLibraryDependencyIR(
                    MavenArtifact(Repositories.KOTLINX_HTML, "org.jetbrains.kotlinx", "kotlinx-html-jvm"),
                    Versions.KOTLINX.KOTLINX_HTML,
                    DependencyType.MAIN
                )
            }
        }

    override fun Writer.getIrsToAddToBuildFile(module: ModuleIR): List<BuildSystemIR> = buildList {
        +RepositoryIR(Repositories.KTOR)
        +RepositoryIR(DefaultRepository.JCENTER)
        +runTaskIrs(mainClass = "ServerKt")
    }

    override fun Reader.createRunConfigurations(module: ModuleIR): List<WizardRunConfiguration> = buildList {
        +WizardGradleRunConfiguration("Run", "run", emptyList())
    }

    override fun updateTargetIr(module: ModuleIR, targetConfigurationIR: TargetConfigurationIR): TargetConfigurationIR =
        targetConfigurationIR.addWithJavaIntoJvmTarget()

    override fun Reader.getFileTemplates(module: ModuleIR): List<FileTemplateDescriptorWithPath> = listOf(
        FileTemplateDescriptor("$id/server.kt.vm", "Server.kt".asPath()) asSrcOf SourcesetType.main
    )

    val imports = InterceptionPoint("imports", emptyList<String>())
    val routes = InterceptionPoint("routes", emptyList<String>())
    val elements = InterceptionPoint("elements", emptyList<String>())

    override val interceptionPoints: List<InterceptionPoint<Any>> = listOf(imports, routes, elements)
    override val settings: List<TemplateSetting<*, *>> = listOf()

    private object DEPENDENCIES {
        val KTOR_SERVER_NETTY = ktorArtifactDependency("ktor-server-netty")
    }
}

private fun ktorArtifactDependency(@NonNls name: String) = ArtifactBasedLibraryDependencyIR(
    MavenArtifact(Repositories.KTOR, "io.ktor", name),
    Versions.KTOR,
    DependencyType.MAIN
)