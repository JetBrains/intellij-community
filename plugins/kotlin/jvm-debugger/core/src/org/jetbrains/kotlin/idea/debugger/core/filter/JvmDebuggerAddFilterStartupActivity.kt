// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.filter

import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.ui.classFilter.ClassFilter

private const val KOTLIN_STDLIB_FILTER = "kotlin.*"
private const val KOTLINX_FILTER = "kotlinx.*"
private const val COMPOSE_RUNTIME_FILTER = "androidx.compose.runtime.*"

private class JvmDebuggerAddFilterStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val settings = serviceAsync<DebuggerSettings>()
        addSteppingFilterIfNeeded(settings, KOTLIN_STDLIB_FILTER)
        addSteppingFilterIfNeeded(settings, KOTLINX_FILTER)
        addSteppingFilterIfNeeded(settings, COMPOSE_RUNTIME_FILTER)
    }
}

private fun addSteppingFilterIfNeeded(debuggerSettings: DebuggerSettings, pattern: String) {
    val steppingFilters = debuggerSettings.steppingFilters
    when (val occurrencesNum = steppingFilters.count { it.pattern == pattern }) {
        0 -> debuggerSettings.steppingFilters = steppingFilters + ClassFilter(pattern)
        1 -> return
        else -> leaveOnlyFirstOccurenceOfSteppingFilter(debuggerSettings, pattern, occurrencesNum)
    }
}

private fun leaveOnlyFirstOccurenceOfSteppingFilter(debuggerSettings: DebuggerSettings, pattern: String, occurrencesNum: Int) {
    val steppingFilters = debuggerSettings.steppingFilters
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

    debuggerSettings.steppingFilters = newFilters.toTypedArray()
}
