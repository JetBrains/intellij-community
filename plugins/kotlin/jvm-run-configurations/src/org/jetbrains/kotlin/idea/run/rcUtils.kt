// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.run

import com.intellij.compiler.options.CompileStepBeforeRun
import com.intellij.execution.configurations.RunConfiguration

fun RunConfiguration.addBuildTask() {
    beforeRunTasks = beforeRunTasks + CompileStepBeforeRun.MakeBeforeRunTask()
}