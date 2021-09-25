// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.core.buildList
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.*

fun runTaskIrs(@NonNls mainClass: String, classPath: BuildSystemIR? = null) = irsList {
    +ApplicationPluginIR(mainClass)

    "application" {
        "mainClassName" assign const(mainClass)
    }

    if (classPath != null) {
        +GradleConfigureTaskIR(GradleByNameTaskAccessIR("run", "JavaExec")) {
            "classpath" assign classPath
        }
    }
}

