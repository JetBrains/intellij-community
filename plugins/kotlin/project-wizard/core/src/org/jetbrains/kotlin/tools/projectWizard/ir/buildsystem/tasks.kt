// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.GradleConfigureTaskIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.GradleIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.GradleNamedTaskAccessIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.irsList
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.TargetJvmVersion
import org.jetbrains.kotlin.tools.projectWizard.plugins.printer.GradlePrinter

fun runTaskIrs(@NonNls mainClass: String, classPath: BuildSystemIR? = null) = irsList {
    +ApplicationPluginIR(mainClass)
    +ApplicationConfigurationIR(mainClass)
    if (classPath != null) {
        +GradleConfigureTaskIR(GradleNamedTaskAccessIR("run", "JavaExec")) {
            "classpath" assign classPath
        }
    }
}

class ApplicationConfigurationIR(private val mainClass: String): GradleIR, FreeIR {
    override fun GradlePrinter.renderGradle() {
        sectionCall("application", needIndent = true) {
            when (dsl) {
                GradlePrinter.GradleDsl.KOTLIN -> {
                    call("mainClass.set") { +mainClass.quotified}
                }
                GradlePrinter.GradleDsl.GROOVY -> {
                    assignment("mainClassName") { +mainClass.quotified}
                }
            }
        }
    }
}

class KotlinExtensionConfigurationIR(private val targetJvmVersion: TargetJvmVersion) : GradleIR, FreeIR {
    override fun GradlePrinter.renderGradle() {
        sectionCall("kotlin", needIndent = true) {
            JvmToolchainConfigurationIR(targetJvmVersion).render(this)
        }
    }
}

class JvmToolchainConfigurationIR(private val targetJvmVersion: TargetJvmVersion) : GradleIR, FreeIR {
    override fun GradlePrinter.renderGradle() {
        +"jvmToolchain"
        par{ +targetJvmVersion.versionNumber.toString() }
    }
}
