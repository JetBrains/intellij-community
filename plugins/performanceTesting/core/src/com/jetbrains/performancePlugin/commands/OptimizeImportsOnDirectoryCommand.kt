// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.jetbrains.performancePlugin.PerformanceTestSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls


class OptimizeImportsOnDirectoryCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "optimizeImportsOnDirectory"
    const val SPAN_NAME: @NonNls String = "optimizeImportsOnDirectory"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val argument = extractCommandArgument(PREFIX).trim()

    val basePath = project.basePath ?: throw IllegalArgumentException("Project has no base path")
    val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath)
                  ?: throw IllegalArgumentException("Cannot find base directory: $basePath")
    val targetVFile = if (argument.isNotEmpty())
      baseDir.findFileByRelativePath(argument)
      ?: throw IllegalArgumentException("Directory not found: $argument")
    else
      baseDir

    val psiDir = readAction { PsiManager.getInstance(project).findDirectory(targetVFile) }
                 ?: throw IllegalArgumentException("Cannot find PSI directory for: ${targetVFile.path}")

    val span = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME).setParent(PerformanceTestSpan.getContext())
    withContext(Dispatchers.EDT) {
      val activeSpan = span.startSpan()
      val processor = OptimizeImportsProcessor(project, psiDir, true, false)
      processor.setPostRunnable { activeSpan.end() }
      withContext(Dispatchers.EDT) {
        processor.run()
      }

    }
  }
}
