// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performance.performancePlugin.commands

import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.vfs.findPsiFile
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import com.jetbrains.performancePlugin.commands.OpenFileCommand.Companion.findFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider

class AssertKotlinFileInSpecificRootCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
    companion object {
        const val PREFIX: @NonNls String = CMD_PREFIX + "assertOpenedKotlinFileInRoot"
    }

    override suspend fun doExecute(context: PlaybackContext) {
        withContext(Dispatchers.EDT) {
            val project = context.project
            val filePath = text.replace(PREFIX, "").trim()
            val file = findFile(filePath, project) ?: error(PerformanceTestingBundle.message("command.file.not.found", filePath))
            val psiFIle = file.findPsiFile(project) ?: error("Fail to find psi file $filePath")
            val ktModule = ProjectStructureProvider.getModule(project, psiFIle , null)
            if (ktModule !is KtSourceModule) {
                throw IllegalStateException("File $file ($ktModule) not in kt source root module")
            }
        }
    }
}