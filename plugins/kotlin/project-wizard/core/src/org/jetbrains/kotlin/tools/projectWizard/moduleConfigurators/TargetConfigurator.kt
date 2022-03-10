// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators

import kotlinx.collections.immutable.toPersistentList
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.core.Reader
import org.jetbrains.kotlin.tools.projectWizard.core.buildList
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.ModuleConfiguratorSetting
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.BuildSystemIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.GradleStringConstIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.irsList
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.multiplatform.DefaultTargetConfigurationIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.multiplatform.TargetAccessIR
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.JSConfigurator.Companion.compiler
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.JSConfigurator.Companion.jsCompilerParam
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.JSConfigurator.Companion.irOrLegacyCompiler
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.JSConfigurator.Companion.kind
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.JsBrowserBasedConfigurator.Companion.browserSubTarget
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.JsNodeBasedConfigurator.Companion.nodejsSubTarget
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.JvmModuleConfigurator.Companion.testFramework
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.buildSystemType
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.isGradle
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleSubType
import org.jetbrains.kotlin.tools.projectWizard.settings.DisplayableSettingItem
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.ModuleKind


interface TargetConfigurator : ModuleConfiguratorWithModuleType {
    override val moduleKind get() = ModuleKind.target

    fun canCoexistsWith(other: List<TargetConfigurator>): Boolean = true

    fun Reader.createTargetIrs(module: Module): List<BuildSystemIR>

    fun createInnerTargetIrs(reader: Reader, module: Module): List<BuildSystemIR> = emptyList()
}

abstract class TargetConfiguratorWithTests : ModuleConfiguratorWithTests, TargetConfigurator

interface SingleCoexistenceTargetConfigurator : TargetConfigurator {
    override fun canCoexistsWith(other: List<TargetConfigurator>): Boolean =
        other.none { it == this }
}

interface SimpleTargetConfigurator : TargetConfigurator {
    val moduleSubType: ModuleSubType
    override val moduleType get() = moduleSubType.moduleType
    override val id get() = "${moduleSubType.name}Target"

    override val suggestedModuleName: String? get() = moduleSubType.name


    override fun Reader.createTargetIrs(
        module: Module
    ): List<BuildSystemIR> = buildList {
        +DefaultTargetConfigurationIR(
            module.createTargetAccessIr(moduleSubType),
            createInnerTargetIrs(this@createTargetIrs, module).toPersistentList()
        )
    }
}

internal fun Module.createTargetAccessIr(
    moduleSubType: ModuleSubType,
    additionalParams: List<Any?> = listOf()
) =
    TargetAccessIR(
        moduleSubType,
        name.takeIf { it != moduleSubType.toString() },
        additionalParams.filterNotNull()
    )


interface JsTargetConfigurator : JSConfigurator, TargetConfigurator, SingleCoexistenceTargetConfigurator, ModuleConfiguratorWithSettings

enum class JsTargetKind(override val text: String) : DisplayableSettingItem {
    LIBRARY(KotlinNewProjectWizardBundle.message("module.configurator.js.target.settings.kind.library")),
    APPLICATION(KotlinNewProjectWizardBundle.message("module.configurator.js.target.settings.kind.application"))
}

enum class JsCompiler(override val text: String, val scriptValue: String) : DisplayableSettingItem {
    IR(KotlinNewProjectWizardBundle.message("module.configurator.js.target.settings.compiler.ir"), "IR"),
    LEGACY(KotlinNewProjectWizardBundle.message("module.configurator.js.target.settings.compiler.legacy"), "LEGACY"),
    BOTH(KotlinNewProjectWizardBundle.message("module.configurator.js.target.settings.compiler.both"), "BOTH")
}

abstract class AbstractBrowserTargetConfigurator: JsTargetConfigurator, ModuleConfiguratorWithTests {
    override fun getConfiguratorSettings(): List<ModuleConfiguratorSetting<*, *>> =
         super<JsTargetConfigurator>.getConfiguratorSettings()

    override val text = KotlinNewProjectWizardBundle.message("module.configurator.js.browser")

    override fun defaultTestFramework(): KotlinTestFramework = KotlinTestFramework.JS

    override fun Reader.createTargetIrs(
        module: Module
    ): List<BuildSystemIR> = irsList {
        +DefaultTargetConfigurationIR(
            module.createTargetAccessIr(
                ModuleSubType.js,
                createAdditionalParams(module)
            )
        ) {
            browserSubTarget(module, this@createTargetIrs)
        }
    }

    abstract fun Reader.createAdditionalParams(module: Module): List<String>
}

object JsBrowserTargetConfigurator : AbstractBrowserTargetConfigurator() {
    @NonNls
    override val id = "jsBrowser"

    override fun Reader.createAdditionalParams(module: Module): List<String> = listOf(irOrLegacyCompiler(module))
}

object MppLibJsBrowserTargetConfigurator : AbstractBrowserTargetConfigurator() {
    @NonNls
    override val id = "mppLibJsBrowser"

    override fun getConfiguratorSettings(): List<ModuleConfiguratorSetting<*, *>> {
        return listOf(testFramework, kind, compiler)
    }

    override fun Reader.createAdditionalParams(module: Module): List<String> = jsCompilerParam(module)?.let { listOf(it) } ?: emptyList()
}

object JsNodeTargetConfigurator : JsTargetConfigurator {
    @NonNls
    override val id = "jsNode"

    override val text = KotlinNewProjectWizardBundle.message("module.configurator.js.node")

    override fun Reader.createTargetIrs(module: Module): List<BuildSystemIR> = irsList {
        +DefaultTargetConfigurationIR(
            module.createTargetAccessIr(
                ModuleSubType.js,
                listOf(irOrLegacyCompiler(module))
            )
        ) {
            nodejsSubTarget(module, this@createTargetIrs)
        }
    }
}

object CommonTargetConfigurator : TargetConfiguratorWithTests(), SimpleTargetConfigurator, SingleCoexistenceTargetConfigurator {
    override val moduleSubType = ModuleSubType.common
    override val text: String = KotlinNewProjectWizardBundle.message("module.configurator.common")

    override fun defaultTestFramework(): KotlinTestFramework = KotlinTestFramework.COMMON

    override fun getConfiguratorSettings(): List<ModuleConfiguratorSetting<*, *>> = emptyList()
}

object JvmTargetConfigurator : JvmModuleConfigurator,
    TargetConfigurator,
    SimpleTargetConfigurator {
    override val moduleSubType = ModuleSubType.jvm

    override val text: String = KotlinNewProjectWizardBundle.message("module.configurator.jvm")

    override fun defaultTestFramework(): KotlinTestFramework = KotlinTestFramework.JUNIT5

    override fun createInnerTargetIrs(
        reader: Reader,
        module: Module
    ): List<BuildSystemIR> = irsList {
        +super<SimpleTargetConfigurator>.createInnerTargetIrs(reader, module)
        reader {
            inContextOfModuleConfigurator(module) {
                val targetVersionValue = JvmModuleConfigurator.targetJvmVersion.reference.settingValue.value
                if (buildSystemType.isGradle) {
                    "compilations.all" {
                        "kotlinOptions.jvmTarget" assign GradleStringConstIR(targetVersionValue)
                    }

                }
                if (!module.hasAndroidSibling()) {
                    "withJava"()
                }
            }
            val testFramework = inContextOfModuleConfigurator(module) { getTestFramework(module) }
            if (testFramework != KotlinTestFramework.NONE) {
                testFramework.usePlatform?.let { usePlatform ->
                    "testRuns[\"test\"].executionTask.configure" {
                        +"$usePlatform()"
                    }
                }
            }
        }
    }

    private fun Module.hasAndroidSibling(): Boolean =
        configurator is TargetConfigurator
                && parent?.subModules?.any { it.configurator is AndroidModuleConfigurator } ?: false
}
