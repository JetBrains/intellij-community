// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.internal.performance.latencyMap
import com.intellij.internal.retype.RetypeSession
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.platform.diagnostic.telemetry.helpers.use
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import kotlin.coroutines.resume

/**
 * Retypes the specified file for performance testing.
 * Syntax: %retypeFile &lt;file-path&gt; [&lt;delay-ms&gt;]
 * Example: %retypeFile src/main/kotlin/Foo.kt 400
 */
class RetypeFileCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME: @NonNls String = "retypeFile"
    const val PREFIX: @NonNls String = "$CMD_PREFIX$NAME"
  }

  override fun getName(): String = NAME

  override suspend fun doExecute(context: PlaybackContext) {
    val args = extractCommandArgument(PREFIX).trim().split(" ")
    val filePath = args[0]
    val delayMs = args.getOrNull(1)?.toIntOrNull() ?: 400

    val project = context.project
    val file = project.getBaseDirectories().firstNotNullOfOrNull { it.findFileByRelativePath(filePath) }
               ?: error("File not found: $filePath")

    latencyMap.clear()

    startSpan(NAME).use { span ->
      withContext(Dispatchers.EDT) {
        val editor = FileEditorManager.getInstance(project)
                       .openTextEditor(OpenFileDescriptor(project, file, 0), true) as? EditorImpl
                     ?: error("Cannot open text editor for: $filePath")

        var typedChars = 0
        var completedChars = 0
        suspendCancellableCoroutine { continuation ->
          val session = RetypeSession(
            project = project,
            editor = editor,
            delayMillis = delayMs,
            scriptBuilder = null,
            threadDumpDelay = 100,
            restoreText = true,
          )
          session.startNextCallback = {
            typedChars = session.typedChars
            completedChars = session.completedChars
            continuation.resume(Unit)
          }
          session.start()
        }
        check(typedChars + completedChars > 0) {
          "Retype of '$filePath' completed but typed 0 characters — the editor likely had no focus"
        }
        span.setAttribute("typedChars", typedChars.toLong())
        span.setAttribute("completedChars", completedChars.toLong())
      }
    }
  }
}
