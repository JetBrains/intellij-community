// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.filter

import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.ui.classFilter.ClassFilter

private const val KOTLIN_STDLIB_FILTER = "kotlin.*"
private const val COMPOSE_RUNTIME_FILTER = "androidx.compose.runtime.*"

private class JvmDebuggerAddFilterStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val settings = DebuggerSettings.getInstance() ?: return
        settings.addSteppingFilterIfNeeded(KOTLIN_STDLIB_FILTER)
        settings.addSteppingFilterIfNeeded(COMPOSE_RUNTIME_FILTER)
    }
}

private fun DebuggerSettings.addSteppingFilterIfNeeded(pattern: String) {
    val steppingFilters = this.steppingFilters
    when (val occurrencesNum = steppingFilters.count { it.pattern == pattern }) {
        0 -> setSteppingFilters(steppingFilters + ClassFilter(pattern))
        1 -> return
        else -> leaveOnlyFirstOccurenceOfSteppingFilter(pattern, occurrencesNum)
    }
}

private fun DebuggerSettings.leaveOnlyFirstOccurenceOfSteppingFilter(pattern: String, occurrencesNum: Int) {
    val steppingFilters = this.steppingFilters
    val newFilters = ArrayList<ClassFilter>(steppingFilters.size - occurrencesNum + 1)

    var firstOccurrenceFound = false
    for (filter in steppingFilters) {
        if (filter.pattern == pattern) {
            if (!firstOccurrenceFound) {
                newFilters.add(filter)
                firstOccurrenceFound = true
            }
        } else {
            newFilters.add(filter)
        }
    }

    setSteppingFilters(newFilters.toTypedArray())
}
