// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.performanceTesting

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerWorkspaceSettings
import java.io.File
import kotlin.io.path.div

/**
 *
 */
class EnableKotlinDaemonLogCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
    companion object {
        const val NAME = "enableKotlinDaemonLog"
        const val PREFIX = "$CMD_PREFIX$NAME"
    }

    override suspend fun doExecute(context: PlaybackContext) {
        val project = context.project
        val kotlinDaemonLog: File = (PathManager.getLogDir() / "kotlin-daemon.log").toFile()
        withContext(Dispatchers.IO) {
            kotlinDaemonLog.createNewFile()
        }
        val compilerSettings = KotlinCompilerWorkspaceSettings.getInstance(project)
        val daemonDefaultVmOptions = compilerSettings.daemonVmOptions
        compilerSettings.daemonVmOptions =
            daemonDefaultVmOptions + (if (daemonDefaultVmOptions.isEmpty()) "" else " ") + "-Dkotlin.daemon.log.path=$kotlinDaemonLog"
    }

    override fun getName(): String {
        return NAME
    }
}