// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle

import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.FreeIR
import org.jetbrains.kotlin.tools.projectWizard.plugins.printer.GradlePrinter
import org.jetbrains.kotlin.tools.projectWizard.settings.JavaPackage

interface AndroidIR : GradleIR

//TODO parameterize
data class AndroidConfigIR(
    val javaPackage: JavaPackage?,
    val isApplication: Boolean,
    val useCompose: Boolean,
    var androidSdkVersion: String = "33",
) : AndroidIR, FreeIR {
    override fun GradlePrinter.renderGradle() {
        sectionCall("android", needIndent = true) {
            if (javaPackage != null) {
                assignmentOrCall("namespace") { +javaPackage.asCodePackage().quotified }; nlIndented()
            }
            assignmentOrCall("compileSdk") { +androidSdkVersion }; nlIndented()
            sectionCall("defaultConfig", needIndent = true) {
                if (javaPackage != null && isApplication) {
                    assignmentOrCall("applicationId") { +javaPackage.asCodePackage().quotified }; nlIndented()
                }
                assignmentOrCall("minSdk") { +"24" }; nlIndented()  // TODO dehardcode
                assignmentOrCall("targetSdk") { +androidSdkVersion }
                if (isApplication) {
                    nlIndented()
                    assignmentOrCall("versionCode") { +"1" }; nlIndented()
                    assignmentOrCall("versionName") { +"1.0".quotified }
                }
            }
            if (isApplication && useCompose) {
                nlIndented()
                sectionCall("buildFeatures", needIndent = true) { assignment("compose") { +"true" } }
                nlIndented()
                sectionCall("composeOptions", needIndent = true) {
                    assignment("kotlinCompilerExtensionVersion") { +Versions.COMPOSE_COMPILER_EXTENSION.text.quotified }
                }
                nlIndented()
                sectionCall("packagingOptions", needIndent = true) {
                    sectionCall("resources", needIndent = true) {
                        +"excludes += "
                        + "/META-INF/{AL2.0,LGPL2.1}".quotified
                    }
                }
            }
            if (isApplication) {
                nlIndented()
                sectionCall("buildTypes", needIndent = true) {
                    val sectionIdentifier = when (dsl) {
                        GradlePrinter.GradleDsl.KOTLIN -> """getByName("release")"""
                        GradlePrinter.GradleDsl.GROOVY -> "release".quotified
                    }
                    sectionCall(sectionIdentifier, needIndent = true) {
                        val minifyCallName = when (dsl) {
                            GradlePrinter.GradleDsl.KOTLIN -> "isMinifyEnabled"
                            GradlePrinter.GradleDsl.GROOVY -> "minifyEnabled"
                        }

                        assignmentOrCall(minifyCallName) { +"false" }
                    }
                }
            }
        }
    }
}
