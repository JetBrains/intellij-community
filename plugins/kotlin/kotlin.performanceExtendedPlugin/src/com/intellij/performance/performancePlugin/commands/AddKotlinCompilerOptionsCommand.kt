// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.performanceTesting

import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerWorkspaceSettings

/**
 *
 */
class AddKotlinCompilerOptionsCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
    companion object {
        const val NAME = "addKotlinCompilerOptions"
        const val PREFIX = "$CMD_PREFIX$NAME"
    }

    override suspend fun doExecute(context: PlaybackContext) {
        val project = context.project
        val options = extractCommandArgument(PREFIX)
        val compilerSettings = KotlinCompilerWorkspaceSettings.getInstance(project)
        compilerSettings.daemonVmOptions += (if (compilerSettings.daemonVmOptions.isEmpty()) "" else " ") + options
    }

    override fun getName(): String {
        return NAME
    }
}