// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle

import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.BuildSystemIR
import org.jetbrains.kotlin.tools.projectWizard.plugins.printer.BuildFilePrinter
import org.jetbrains.kotlin.tools.projectWizard.plugins.printer.MavenPrinter

interface MavenIR : BuildSystemIR {
    fun MavenPrinter.render()

    override fun BuildFilePrinter.render() {
        if (this is MavenPrinter) render()
    }
}

data class ModulesDependencyMavenIR(val dependencies: List<String>) : MavenIR {
    override fun MavenPrinter.render() {
        node("modules") {
            dependencies.forEach { dependency ->
                singleLineNode("module") { +dependency }
            }
        }
    }
}


