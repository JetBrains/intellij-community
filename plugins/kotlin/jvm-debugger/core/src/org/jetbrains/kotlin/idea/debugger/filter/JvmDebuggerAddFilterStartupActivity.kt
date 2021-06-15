// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.filter

import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.ui.classFilter.ClassFilter

private const val KOTLIN_STDLIB_FILTER = "kotlin.*"

class JvmDebuggerAddFilterStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        addKotlinStdlibDebugFilterIfNeeded()
    }
}

fun addKotlinStdlibDebugFilterIfNeeded() {
    val settings = DebuggerSettings.getInstance() ?: return
    val existingFilters = settings.steppingFilters

    if (existingFilters.any { it.pattern == KOTLIN_STDLIB_FILTER }) {
        return
    }

    settings.steppingFilters = settings.steppingFilters + ClassFilter(KOTLIN_STDLIB_FILTER)
}