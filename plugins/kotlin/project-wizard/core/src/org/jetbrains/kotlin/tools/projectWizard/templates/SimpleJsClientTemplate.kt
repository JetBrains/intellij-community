// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.templates


import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.core.Reader
import org.jetbrains.kotlin.tools.projectWizard.core.Writer
import org.jetbrains.kotlin.tools.projectWizard.core.asPath
import org.jetbrains.kotlin.tools.projectWizard.core.buildList
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.TemplateSetting
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.ArtifactBasedLibraryDependencyIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.DependencyIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.DependencyType
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.ModuleIR
import org.jetbrains.kotlin.tools.projectWizard.library.MavenArtifact
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.MppModuleConfigurator.getTestFramework
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.isPresent
import org.jetbrains.kotlin.tools.projectWizard.phases.GenerationPhase
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Repositories
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.SourcesetType

object SimpleJsClientTemplate : JsClientTemplate() {
    override val title: String = KotlinNewProjectWizardBundle.message("module.template.js.simple.title")
    override val description: String = KotlinNewProjectWizardBundle.message("module.template.js.simple.description")

    @NonNls
    override val id: String = "simpleJsClient"

    private const val clientFileToCreate = "Client.kt"
    override val filesToOpenInEditor = listOf(clientFileToCreate)

    private val useKotlinxHtml by booleanSetting(
        KotlinNewProjectWizardBundle.message("module.template.simple.use.kotlinx.html"),
        GenerationPhase.PROJECT_GENERATION
    ) {
        defaultValue = value(false)
        description = KotlinNewProjectWizardBundle.message("module.template.simple.use.kotlinx.html.description")
    }

    override val settings: List<TemplateSetting<*, *>> = listOf(useKotlinxHtml)

    override fun Writer.getRequiredLibraries(module: ModuleIR): List<DependencyIR> = withSettingsOf(module.originalModule) {
        buildList {
            if (useKotlinxHtml.reference.settingValue()) {
                +ArtifactBasedLibraryDependencyIR(
                    MavenArtifact(Repositories.KOTLINX_HTML, "org.jetbrains.kotlinx", "kotlinx-html"),
                    Versions.KOTLINX.KOTLINX_HTML,
                    DependencyType.MAIN
                )
            }
        }
    }

    override fun Reader.getFileTemplates(module: ModuleIR): List<FileTemplateDescriptorWithPath> =
        withSettingsOf(module.originalModule) {
            buildList {
                val hasKtorServNeighbourTarget = hasKtorServNeighbourTarget(module)
                if (!hasKtorServNeighbourTarget) {
                    +(FileTemplateDescriptor("jsClient/index.html.vm") asResourceOf SourcesetType.main)
                }
                val hasTestingFramework = getTestFramework(module.originalModule).isPresent
                if (useKotlinxHtml.reference.settingValue()) {
                    +(FileTemplateDescriptor("$id/client.kt.vm", clientFileToCreate.asPath()) asSrcOf SourcesetType.main)
                    if (hasTestingFramework) {
                        +(FileTemplateDescriptor("$id/TestClient.kt.vm", "TestClient.kt".asPath()) asSrcOf SourcesetType.test)
                    }
                } else {
                    +(FileTemplateDescriptor("$id/simple.kt.vm", "Simple.kt".asPath()) asSrcOf SourcesetType.main)
                    if (hasTestingFramework) {
                        +(FileTemplateDescriptor("$id/SimpleTest.kt.vm", "SimpleTest.kt".asPath()) asSrcOf SourcesetType.test)
                    }
                }
            }
        }
}
