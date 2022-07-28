// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.multiplatform

import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.GradleIR
import org.jetbrains.kotlin.tools.projectWizard.plugins.printer.GradlePrinter

data class NativeTargetInternalIR(
    val mainClassFqName: String?
) : GradleIR {
    override fun GradlePrinter.renderGradle() {
        sectionCall("binaries") {
            indent()
            sectionCall("executable") {
                mainClassFqName?.let { mainClass ->
                    indent(); assignment("entryPoint") { +mainClass.quotified }
                }
            }
        }
    }
}