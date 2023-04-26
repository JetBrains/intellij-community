// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.templates


import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.core.Reader
import org.jetbrains.kotlin.tools.projectWizard.core.asPath
import org.jetbrains.kotlin.tools.projectWizard.core.buildList
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.ModuleIR
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.MppModuleConfigurator.getTestFramework
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.WasmTargetConfigurator
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.isPresent
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.moduleType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.SourcesetType

object SimpleWasmClientTemplate : JsClientTemplate() {
    override val title: String = KotlinNewProjectWizardBundle.message("module.template.wasm.browser.title")
    override val description: String = KotlinNewProjectWizardBundle.message("module.template.wasm.browser.description")

    @NonNls
    override val id: String = "simpleWasmClient"

    private const val clientFileToCreate = "Simple.kt"
    override val filesToOpenInEditor = listOf(clientFileToCreate)

    override fun isApplicableTo(module: Module, projectKind: ProjectKind, reader: Reader): Boolean =
        module.configurator.moduleType == ModuleType.wasm &&
                when (module.configurator) {
                    WasmTargetConfigurator -> true
                    else -> false
                }

    override fun Reader.getFileTemplates(module: ModuleIR): List<FileTemplateDescriptorWithPath> =
        withSettingsOf(module.originalModule) {
            buildList {
                val hasKtorServNeighbourTarget = hasKtorServNeighbourTarget(module)
                if (!hasKtorServNeighbourTarget) {
                    +(FileTemplateDescriptor("jsClient/index.html.vm") asResourceOf SourcesetType.main)
                }
                +(FileTemplateDescriptor("$id/simple.kt.vm", "Simple.kt".asPath()) asSrcOf SourcesetType.main)
                if (getTestFramework(module.originalModule).isPresent) {
                    +(FileTemplateDescriptor("$id/SimpleTest.kt.vm", "SimpleTest.kt".asPath()) asSrcOf SourcesetType.test)
                }
            }
        }

    override fun indexTitleName(): String {
        return "Kotlin/Wasm"
    }
}
