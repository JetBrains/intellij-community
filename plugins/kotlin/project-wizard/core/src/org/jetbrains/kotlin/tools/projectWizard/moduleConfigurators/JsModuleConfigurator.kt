// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators


import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.core.Reader
import org.jetbrains.kotlin.tools.projectWizard.core.TaskResult
import org.jetbrains.kotlin.tools.projectWizard.core.Writer
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.ModuleConfiguratorSetting
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.ModuleConfiguratorSettingReference
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.BuildSystemIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.KotlinBuildSystemPluginIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.GradleIRListBuilder
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.irsList
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.BrowserJsSinglePlatformModuleConfigurator.settingsValue
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.JSConfigurator.Companion.isApplication
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.JsBrowserBasedConfigurator.Companion.browserSubTarget
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.JsNodeBasedConfigurator.Companion.nodejsSubTarget
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.JvmModuleConfigurator.Companion.testFramework
import org.jetbrains.kotlin.tools.projectWizard.phases.GenerationPhase
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.gradle.GradlePlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.KotlinPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModulesToIrConversionData
import org.jetbrains.kotlin.tools.projectWizard.plugins.printer.GradlePrinter
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.ModuleKind
import org.jetbrains.kotlin.tools.projectWizard.templates.ReactJsClientTemplate
import org.jetbrains.kotlin.tools.projectWizard.templates.SimpleJsClientTemplate
import org.jetbrains.kotlin.tools.projectWizard.templates.SimpleNodeJsTemplate
import java.nio.file.Path

interface JSConfigurator : ModuleConfiguratorWithModuleType, ModuleConfiguratorWithSettings {
    override val moduleType: ModuleType get() = ModuleType.js

    override fun Writer.runArbitraryTask(
        configurationData: ModulesToIrConversionData,
        module: Module,
        modulePath: Path
    ): TaskResult<Unit> =
        GradlePlugin.gradleProperties
            .addValues(
                "kotlin.js.compiler" to irOrLegacyCompiler(module).lowercase()
            )

    override fun getConfiguratorSettings(): List<ModuleConfiguratorSetting<*, *>> =
        super.getConfiguratorSettings() + kind + useJsLegacyCompiler

    companion object : ModuleConfiguratorSettings() {
        val kind by enumSetting<JsTargetKind>(
            KotlinNewProjectWizardBundle.message("module.configurator.js.target.settings.kind"),
            GenerationPhase.PROJECT_GENERATION
        ) {
            tooltipText = KotlinNewProjectWizardBundle.message("module.configurator.js.target.settings.kind.hint")
            defaultValue = value(JsTargetKind.APPLICATION)
            filter = filter@{ reference, kindCandidate ->
                when {
                    reference !is ModuleConfiguratorSettingReference<*, *> -> false
                    kindCandidate == JsTargetKind.LIBRARY
                            && (reference.module?.template is SimpleJsClientTemplate ||
                            reference.module?.template is ReactJsClientTemplate ||
                            reference.module?.template is SimpleNodeJsTemplate) -> false
                    else -> true
                }
            }
        }

        val compiler by enumSetting<JsCompiler>(
            KotlinNewProjectWizardBundle.message("module.configurator.js.target.settings.compiler"),
            GenerationPhase.PROJECT_GENERATION
        ) {
            defaultValue = value(JsCompiler.BOTH)
            tooltipText = KotlinNewProjectWizardBundle.message("module.configurator.js.target.settings.compiler.tooltip")
            filter = { reference, compilerCandidate ->
                when {
                    reference !is ModuleConfiguratorSettingReference<*, *> -> false
                    reference.module?.let { settingValue(it, kind) } == JsTargetKind.LIBRARY -> true
                    reference.module?.let { settingValue(it, kind) } == JsTargetKind.APPLICATION
                            && compilerCandidate == JsCompiler.BOTH -> false
                    else -> true
                }
            }
        }

        internal fun Reader.jsCompilerParam(module: Module): String? = settingValue(module, compiler)?.scriptValue

        val useJsLegacyCompiler by booleanSetting(
            KotlinNewProjectWizardBundle.message("module.configurator.js.target.settings.use.js.legacy.title"),
            GenerationPhase.PROJECT_GENERATION
        ) {
            defaultValue = value(false)
            description = KotlinNewProjectWizardBundle.message("module.configurator.js.target.settings.use.js.ir.description")
        }

        internal fun Reader.irOrLegacyCompiler(module: Module): String {
            fun Reader.useJsLegacyCompiler(module: Module): Boolean = settingValue(module, useJsLegacyCompiler) ?: false
            return if (!useJsLegacyCompiler(module)) JsCompiler.IR.scriptValue else JsCompiler.LEGACY.scriptValue
        }

        fun Reader.isApplication(module: Module): Boolean =
            settingsValue(module, kind) == JsTargetKind.APPLICATION
    }
}

interface JsBrowserBasedConfigurator {
    companion object : ModuleConfiguratorSettings() {
        private fun Reader.cssSupportNeeded(module: Module): Boolean =
            isApplication(module) || settingValue(module, testFramework) != KotlinTestFramework.NONE

        fun GradleIRListBuilder.browserSubTarget(module: Module, reader: Reader) {
            if (reader.isApplication(module)) {
                applicationSupport()
            }
            "browser" {
                if (reader.cssSupportNeeded(module)) commonCssSupport(reader)
            }
        }
    }
}

interface JsNodeBasedConfigurator {
    companion object : ModuleConfiguratorSettings() {
        fun GradleIRListBuilder.nodejsSubTarget(module: Module, reader: Reader) {
            if (reader.isApplication(module)) {
                applicationSupport()
            }
            "nodejs" {
            }
        }
    }
}

abstract class JsSinglePlatformModuleConfigurator :
    JSConfigurator,
    ModuleConfiguratorWithTests,
    SinglePlatformModuleConfigurator,
    ModuleConfiguratorWithSettings {
    override fun getConfiguratorSettings(): List<ModuleConfiguratorSetting<*, *>> =
        super<ModuleConfiguratorWithTests>.getConfiguratorSettings() +
                super<JSConfigurator>.getConfiguratorSettings()

    override fun defaultTestFramework(): KotlinTestFramework = KotlinTestFramework.JS

    override val canContainSubModules = false

    override fun createKotlinPluginIR(configurationData: ModulesToIrConversionData, module: Module): KotlinBuildSystemPluginIR? =
        KotlinBuildSystemPluginIR(
            KotlinBuildSystemPluginIR.Type.js,
            version = configurationData.kotlinVersion
        )

    override fun createBuildFileIRs(
        reader: Reader,
        configurationData: ModulesToIrConversionData,
        module: Module
    ): List<BuildSystemIR> = irsList {
        "kotlin" {
            "js" {
                subTarget(module, reader)
            }
        }
    }

    protected abstract fun GradleIRListBuilder.subTarget(module: Module, reader: Reader)
}

object BrowserJsSinglePlatformModuleConfigurator : JsSinglePlatformModuleConfigurator(), JsBrowserBasedConfigurator {
    @NonNls
    override val id = "jsBrowserSinglePlatform"

    @NonNls
    override val suggestedModuleName = "browser"

    override val moduleKind = ModuleKind.singlePlatformJsBrowser

    override fun GradleIRListBuilder.subTarget(module: Module, reader: Reader) {
        browserSubTarget(module, reader)
    }

    override val text = KotlinNewProjectWizardBundle.message("module.configurator.simple.js.browser")
}

object NodeJsSinglePlatformModuleConfigurator : JsSinglePlatformModuleConfigurator() {
    @NonNls
    override val id = "jsNodeSinglePlatform"

    @NonNls
    override val suggestedModuleName = "nodejs"

    override val moduleKind = ModuleKind.singlePlatformJsNode

    override fun GradleIRListBuilder.subTarget(module: Module, reader: Reader) {
        nodejsSubTarget(module, reader)
    }

    override val text = KotlinNewProjectWizardBundle.message("module.configurator.simple.js.node")
}

fun GradleIRListBuilder.applicationSupport() {
    +"binaries.executable()"
}

fun GradleIRListBuilder.commonCssSupport(reader: Reader) {
    val version = with (reader) {
        KotlinPlugin.version.propertyValue
    }.version.text

    "commonWebpackConfig" {
        val more1_8 =
            version.substringBefore(".").toInt() >= 1 &&
                    version.substringAfter(".").substringBefore(".").toInt() >= 8
        if (more1_8) {
            "cssSupport" {
                addRaw {
                    val receiver = when (dsl) {
                        GradlePrinter.GradleDsl.KOTLIN -> ""
                        GradlePrinter.GradleDsl.GROOVY -> "it."
                    }
                    +"${receiver}enabled.set(true)"
                }
            }
        } else {
            +"cssSupport.enabled = true"
        }
    }
}
