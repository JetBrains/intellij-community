// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performance.performancePlugin.commands

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.psi.PsiJavaFile
import com.jetbrains.performancePlugin.commands.OpenFileCommand
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.actions.JavaToKotlinAction
import org.jetbrains.kotlin.idea.core.util.toPsiFile

class ConvertJavaToKotlinCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
    companion object {
        const val NAME = "convertJavaToKotlin"
        const val PREFIX = "$CMD_PREFIX$NAME"
    }

    override suspend fun doExecute(context: PlaybackContext) {
        val project = context.project
        val (moduleName, filePath) = extractCommandList(PREFIX, " ")
        withContext(Dispatchers.EDT) {
            val module = ModuleManager.getInstance(project).modules.firstOrNull { it.name == moduleName }
                ?: throw IllegalArgumentException("There is no module with name $moduleName")
            val javaFile = readAction { OpenFileCommand.findFile(filePath, project)?.toPsiFile(project) as? PsiJavaFile }
                ?: throw IllegalArgumentException("There is no file $filePath")

            TelemetryManager.getTracer(Scope("javaToKotlin")).spanBuilder(NAME).use {
                //readaction is not enough
                writeIntentReadAction {
                    JavaToKotlinAction.Handler.convertFiles(
                        files = listOf(javaFile),
                        project = project,
                        module = module,
                        enableExternalCodeProcessing = false,
                        askExternalCodeProcessing = false,
                        forceUsingOldJ2k = false
                    )
                }
            }
        }
    }

    override fun getName(): String {
        return NAME
    }
}