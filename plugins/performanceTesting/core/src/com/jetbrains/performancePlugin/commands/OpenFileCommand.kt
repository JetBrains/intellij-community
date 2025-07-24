// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.fileEditor.impl.waitForFullyCompleted
import com.intellij.openapi.options.advanced.AdvancedSettingType
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import com.jetbrains.performancePlugin.utils.DaemonCodeAnalyzerListener
import com.jetbrains.performancePlugin.utils.HighlightingTestUtil
import com.sampullara.cli.Args
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.SystemIndependent
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Command opens file.
 * Example: %openFile -file <filename from the root of the project> [-suppressErrors] [-timeout <in seconds>] [WARMUP] [-disableCodeAnalysis(-dsa) by default false]
 */
class OpenFileCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME: @NonNls String = "openFile"
    const val PREFIX: @NonNls String = "$CMD_PREFIX$NAME"
    const val OPEN_FILE_SPAN_NAME: @NonNls String = "openFile"
    const val FIRST_CODE_ANALYSIS_SPAN_NAME: @NonNls String = "firstCodeAnalysis"

    @JvmStatic
    fun findFile(filePath: String, project: Project): VirtualFile? {
      return when {
        filePath.contains(JarFileSystem.JAR_SEPARATOR) -> JarFileSystem.getInstance().findFileByPath(filePath)
        FileUtil.isAbsolute(filePath) -> LocalFileSystem.getInstance().findFileByPath(filePath)
        else -> project.getBaseDirectories().firstNotNullOfOrNull { it.findFileByRelativePath(filePath) }
      }
    }

    fun getOptions(arguments: String): OpenFileCommandOptions? {
      return runCatching {
        OpenFileCommandOptions().apply { Args.parse(this, arguments.split(" ").toTypedArray(), false) }
      }.getOrNull()
    }
  }

  override fun getName(): String = NAME

  override suspend fun doExecute(context: PlaybackContext) {
    val arguments = extractCommandArgument(PREFIX)
    val options = getOptions(arguments)
    val filePath = (options?.file ?: text.split(' ', limit = 4)[1]).replace("SPACE_SYMBOL", " ")
    val timeout = options?.timeout ?: 0
    val suppressErrors = options?.suppressErrors == true
    if (options?.forbidDownloadingSourcesOnNavigation == true) {
      AdvancedSettings.getInstance().setSetting("gradle.download.sources.automatically", false, AdvancedSettingType.Bool)
    }

    val project = context.project
    val file = findFile(filePath, project) ?: error(PerformanceTestingBundle.message("command.file.not.found", filePath))
    val connection = project.messageBus.simpleConnect()
    val spanRef = Ref<Span>()
    val projectPath = project.basePath
    val job = if (useWaitForCodeAnalysisCode(options)) {
      null
    }
    else {
      val listenJob = DaemonCodeAnalyzerListener.listen(connection, spanRef, timeout)
      if (suppressErrors) {
        listenJob.suppressErrors()
      }
      listenJob
    }

    spanRef.set(startSpan(FIRST_CODE_ANALYSIS_SPAN_NAME))
    setFilePath(projectPath = projectPath, span = spanRef.get(), file = file)

    // focus window
    withContext(Dispatchers.EDT) {
      ProjectUtil.focusProjectWindow(project, stealFocusIfAppInactive = true)
    }

    var openFileSpan: Span? = null
    if (useWaitForCodeAnalysisCode(options)) {
      openFileSpan = startSpan(OPEN_FILE_SPAN_NAME).setAttribute("path", file.path)
    }

    val fileEditor = (project.serviceAsync<FileEditorManager>() as FileEditorManagerEx)
      .openFile(file = file, options = FileEditorOpenOptions(requestFocus = true))

    if (useWaitForCodeAnalysisCode(options)) {
      HighlightingTestUtil.waitForAnalysisWithNewApproach(project, spanRef, timeout, suppressErrors)
      openFileSpan!!.end()
      return
    }

    if (options != null && !options.disableCodeAnalysis) {
      waitForFullyCompleted(fileEditor)
    }

    job!!.onError {
      spanRef.get()?.setAttribute("timeout", "true")
    }
    job.withErrorMessage("Timeout on open file ${file.path} more than $timeout seconds")

    if (options != null && !options.disableCodeAnalysis) {
      job.waitForComplete()
    }
  }

  private fun useWaitForCodeAnalysisCode(options: OpenFileCommandOptions?): Boolean = options != null
                                                                                      && !options.disableCodeAnalysis
                                                                                      && options.useNewWaitForCodeAnalysisCode

  private fun setFilePath(projectPath: @SystemIndependent @NonNls String?, span: Span, file: VirtualFile) {
    if (projectPath != null) {
      span.setAttribute("filePath", file.path.replaceFirst(projectPath, ""))
    }
    else {
      span.setAttribute("filePath", file.path)
    }
  }

  private suspend fun waitForAnalysisWithNewApproach(project: Project, spanRef: Ref<Span>, timeout: Long, suppressErrors: Boolean) {
    val timeoutDuration = if (timeout == 0L) 5.minutes else timeout.seconds
    try {
      project.service<CodeAnalysisStateListener>().waitAnalysisToFinish(timeoutDuration, !suppressErrors)
    }
    catch (e: TimeoutException) {
      spanRef.get()?.setAttribute("timeout", "true")
    }
    finally {
      spanRef.get()?.end()
    }
  }
}