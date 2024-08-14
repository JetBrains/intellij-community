// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performance.performancePlugin.commands

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.vfs.findPsiFile
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import com.jetbrains.performancePlugin.commands.OpenFileCommand.Companion.findFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule

internal class AssertKotlinFileInSpecificRootCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
    companion object {
        const val PREFIX: @NonNls String = CMD_PREFIX + "assertOpenedKotlinFileInRoot"
    }

    override suspend fun doExecute(context: PlaybackContext) {
        withContext(Dispatchers.EDT) {
            val project = context.project
            val filePath = text.replace(PREFIX, "").trim()
            val file = findFile(filePath, project) ?: error(PerformanceTestingBundle.message("command.file.not.found", filePath))
            //maybe readaction
            writeIntentReadAction {
                val psiFile = file.findPsiFile(project) ?: error("Fail to find psi file $filePath")
                val module = KaModuleProvider.getModule(project, psiFile, useSiteModule = null)
                if (module !is KaSourceModule) {
                    throw IllegalStateException("File $file ($module) not in kt source root module")
                }
            }
        }
    }
}