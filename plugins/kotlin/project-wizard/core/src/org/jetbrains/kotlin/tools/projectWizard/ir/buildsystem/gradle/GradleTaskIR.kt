// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.BuildSystemIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.FreeIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.render
import org.jetbrains.kotlin.tools.projectWizard.plugins.printer.GradlePrinter

interface GradleTaskAccessIR : GradleIR

data class GradleNamedTaskAccessIR(
    val name: String,
    val taskClass: String? = null
) : GradleTaskAccessIR {
    override fun GradlePrinter.renderGradle() {
        when (dsl) {
            GradlePrinter.GradleDsl.GROOVY -> {
                +"tasks.named('$name')"
            }
            GradlePrinter.GradleDsl.KOTLIN -> {
                +"tasks.named"
                taskClass?.let { +"<$it>" }
                +"(${name.quotified})"
            }
        }
    }
}

data class GradleByClassTasksCreateIR(
    val taskName: String,
    val taskClass: String
) : GradleTaskAccessIR {
    override fun GradlePrinter.renderGradle() {
        when (dsl) {
            GradlePrinter.GradleDsl.KOTLIN -> {
                +"val $taskName by tasks.creating($taskClass::class)"
            }
            GradlePrinter.GradleDsl.GROOVY -> {
                +"task($taskName, type: $taskClass)"
            }
        }
    }
}


data class GradleConfigureTaskIR(
    val taskAccess: GradleTaskAccessIR,
    val dependsOn: List<BuildSystemIR> = emptyList(),
    val irs: List<BuildSystemIR> = emptyList()
) : GradleIR, FreeIR {
    constructor(
        taskAccess: GradleTaskAccessIR,
        dependsOn: List<BuildSystemIR> = emptyList(),
        createIrs: IRsListBuilderFunction
    ) : this(taskAccess, dependsOn, createIrs.build())

    override fun GradlePrinter.renderGradle() {
        taskAccess.render(this)
        +" "
        inBrackets {
            if (dependsOn.isNotEmpty()) {
                indent()
                call("dependsOn", forceBrackets = true) {
                    dependsOn.list { it.render(this) }
                }
                nl()
            }
            irs.listNl()
        }
    }
}
