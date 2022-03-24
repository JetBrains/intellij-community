// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.templates

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.WizardGradleRunConfiguration
import org.jetbrains.kotlin.tools.projectWizard.WizardRunConfiguration
import org.jetbrains.kotlin.tools.projectWizard.core.Reader
import org.jetbrains.kotlin.tools.projectWizard.core.buildList
import org.jetbrains.kotlin.tools.projectWizard.core.safeAs
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.*
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.*
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.multiplatform.DefaultTargetConfigurationIR
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.*
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleSubType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.plugins.projectName
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.ModuleKind
import org.jetbrains.kotlin.tools.projectWizard.transformers.interceptors.TemplateInterceptor
import org.jetbrains.kotlin.tools.projectWizard.transformers.interceptors.interceptTemplate

abstract class JsClientTemplate : Template() {
    override fun isApplicableTo(module: Module, projectKind: ProjectKind, reader: Reader): Boolean =
        module.configurator.moduleType == ModuleType.js
                && when (module.configurator) {
                    JsBrowserTargetConfigurator, MppLibJsBrowserTargetConfigurator -> true
                    BrowserJsSinglePlatformModuleConfigurator -> {
                        with(reader) {
                            inContextOfModuleConfigurator(module, module.configurator) {
                                JSConfigurator.kind.reference.notRequiredSettingValue == JsTargetKind.APPLICATION
                            }
                        }
                    }
                    else -> false
                }

    override fun Reader.createRunConfigurations(module: ModuleIR): List<WizardRunConfiguration> = buildList {
        if (module.originalModule.kind == ModuleKind.singlePlatformJsBrowser) {
            +WizardGradleRunConfiguration(
                org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle.message("module.template.js.simple.run.configuration.dev"),
                "browserDevelopmentRun",
                listOf("--continuous")
            )
            +WizardGradleRunConfiguration(
                org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle.message("module.template.js.simple.run.configuration.prod"),
                "browserProductionRun",
                listOf("--continuous")
            )
        }
    }

    override fun Reader.createInterceptors(module: ModuleIR): List<TemplateInterceptor> = buildList {
        +interceptTemplate(KtorServerTemplate) {
            applicableIf { buildFileIR ->
                if (module !is MultiplatformModuleIR) return@applicableIf false
                val tasks = buildFileIR.irsOfTypeOrNull<GradleConfigureTaskIR>() ?: return@applicableIf true
                tasks.none { it.taskAccess.safeAs<GradleNamedTaskAccessIR>()?.name?.endsWith("Jar") == true }
            }

            interceptAtPoint(template.routes) { value ->
                if (value.isNotEmpty()) return@interceptAtPoint value
                buildList {
                    +value
                    +"""
                    static("/static") {
                        resources()
                    }
                    """.trimIndent()
                }
            }

            interceptAtPoint(template.imports) { value ->
                if (value.isNotEmpty()) return@interceptAtPoint value
                buildList {
                    +value
                    +"io.ktor.http.content.resources"
                    +"io.ktor.http.content.static"
                }
            }

            interceptAtPoint(template.elements) { value ->
                if (value.isNotEmpty()) return@interceptAtPoint value
                buildList {
                    +value
                    +"""
                     div {
                        id = "root"
                     }
                    """.trimIndent()
                    +"""script(src = "/static/${indexFileName(module.originalModule)}.js") {}"""
                }
            }

            transformBuildFile { buildFileIR ->
                val jsSourceSetName = module.safeAs<MultiplatformModuleIR>()?.name ?: return@transformBuildFile null
                val jvmTarget = buildFileIR.targets.firstOrNull { target ->
                    target.safeAs<DefaultTargetConfigurationIR>()?.targetAccess?.type == ModuleSubType.jvm
                } as? DefaultTargetConfigurationIR ?: return@transformBuildFile null
                val jvmTargetName = jvmTarget.targetName

                val distributionTaskName = "$jsSourceSetName$BROWSER_DISTRIBUTION_TASK_SUFFIX"

                val jvmProcessResourcesTaskAccess = GradleNamedTaskAccessIR(
                    "${jvmTargetName}ProcessResources",
                    "Copy"
                )

                val jvmProcessResourcesTaskConfiguration = run {
                    val distributionTaskVariable = CreateGradleValueIR(
                        distributionTaskName,
                        GradleNamedTaskAccessIR(distributionTaskName)
                    )
                    val from = GradleCallIr(
                        "from",
                        listOf(
                            GradlePropertyAccessIR(distributionTaskName)
                        )
                    )
                    GradleConfigureTaskIR(
                        jvmProcessResourcesTaskAccess,
                        irs = listOf(
                            distributionTaskVariable,
                            from
                        )
                    )
                }

                val jvmJarTaskAccess = GradleNamedTaskAccessIR(
                    "${jvmTargetName}Jar",
                    "Jar"
                )

                val runTaskConfiguration = run {
                    val taskAccess = GradleNamedTaskAccessIR("run", "JavaExec")
                    val classpath = GradleCallIr("classpath", listOf(jvmJarTaskAccess))
                    GradleConfigureTaskIR(
                        taskAccess,
                        dependsOn = listOf(jvmJarTaskAccess),
                        irs = listOf(classpath)
                    )
                }

                buildFileIR.withIrs(jvmProcessResourcesTaskConfiguration, runTaskConfiguration)
            }
        }
    }

    override fun Reader.getAdditionalSettings(module: Module): Map<String, Any> = withSettingsOf(module) {
        jsSettings(module)
    }

    protected fun Reader.jsSettings(module: Module): Map<String, String> {
        return mapOf("indexFile" to indexFileName(module))
    }

    private fun Reader.indexFileName(
        module: Module
    ): String {
        val buildFiles = BuildSystemPlugin.buildFiles.propertyValue
        return if (buildFiles.size == 1) projectName else module.parent?.name ?: module.name
    }

    protected fun hasKtorServNeighbourTarget(module: ModuleIR) =
        module.safeAs<MultiplatformModuleIR>()
            ?.neighbourTargetModules()
            .orEmpty()
            .any { it.template is KtorServerTemplate }

    companion object {
        @NonNls
        private const val BROWSER_DISTRIBUTION_TASK_SUFFIX = "BrowserDistribution"
    }
}