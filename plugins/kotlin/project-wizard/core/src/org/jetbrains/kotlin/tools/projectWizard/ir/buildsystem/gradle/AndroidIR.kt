// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle

import org.jetbrains.kotlin.tools.projectWizard.core.asStringWithUnixSlashes
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.FreeIR
import org.jetbrains.kotlin.tools.projectWizard.plugins.printer.GradlePrinter
import org.jetbrains.kotlin.tools.projectWizard.settings.JavaPackage
import java.nio.file.Path

interface AndroidIR : GradleIR

//TODO parameterize
data class AndroidConfigIR(
    val javaPackage: JavaPackage?,
    val newManifestPath: Path?,
    val printVersionCode: Boolean,
    val printBuildTypes: Boolean,
    var androidSdkVersion: String = "32"
) : AndroidIR, FreeIR {
    override fun GradlePrinter.renderGradle() {
        sectionCall("android", needIndent = true) {
            call("compileSdkVersion") { +androidSdkVersion }; nlIndented()
            if (newManifestPath != null) {
                when (dsl) {
                    GradlePrinter.GradleDsl.KOTLIN -> {
                        +"""sourceSets["main"].manifest.srcFile("${newManifestPath.asStringWithUnixSlashes()}")"""
                    }
                    GradlePrinter.GradleDsl.GROOVY -> {
                        +"""sourceSets.main.manifest.srcFile('${newManifestPath.asStringWithUnixSlashes()}')"""
                    }
                }
                nlIndented()
            }
            sectionCall("defaultConfig", needIndent = true) {
                if (javaPackage != null) {
                    assignmentOrCall("applicationId") { +javaPackage.asCodePackage().quotified }; nlIndented()
                }
                call("minSdkVersion") { +"24" }; nlIndented()  // TODO dehardcode
                call("targetSdkVersion") { +androidSdkVersion };
                if (printVersionCode) {
                    nlIndented()
                    assignmentOrCall("versionCode") { +"1" }; nlIndented()
                    assignmentOrCall("versionName") { +"1.0".quotified }
                }
            }
            nlIndented()
            sectionCall("compileOptions", needIndent = true) {
                assignmentOrCall("sourceCompatibility") { +"JavaVersion.VERSION_1_8" }; nlIndented()
                assignmentOrCall("targetCompatibility") { +"JavaVersion.VERSION_1_8" }
            }
            if (printBuildTypes) {
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
