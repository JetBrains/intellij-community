// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.ide.DataManager
import com.intellij.ide.impl.ProjectUtil
import com.intellij.internal.performance.LatencyRecord
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.actionSystem.LatencyListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.util.Ref
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.utils.DaemonCodeAnalyzerListener
import com.jetbrains.performancePlugin.utils.DaemonCodeAnalyzerResult
import com.jetbrains.performancePlugin.utils.HighlightingTestUtil
import com.jetbrains.performancePlugin.utils.findTypingTarget
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import kotlinx.coroutines.*
import java.awt.KeyboardFocusManager
import javax.swing.JComponent
import kotlin.time.Duration.Companion.seconds
import com.intellij.openapi.util.Pair

/**
 * Command types text with some delay between typing.
 * Text and delay are being set as parameters.
 * Syntax: %delayType <delay in ms>|<Text to type>[|<calculate analyzes time>[|<disable write protection>]]
 * Example: %delayType 150|Sample text for typing scenario[|true[|true]]
 */
class DelayTypeCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {

  companion object {
    const val PREFIX: String = CMD_PREFIX + "delayType"
    const val SPAN_NAME: String = "typing"
    const val CODE_ANALYSIS_SPAN_NAME: String = "typingCodeAnalyzing"
  }

  @Suppress("SSBasedInspection")
  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val argumentList = extractCommandArgument(PREFIX)
      .split("\\|".toRegex()).dropLastWhile { it.isEmpty() }

    if (argumentList.size < 2) {
      throw IllegalArgumentException("Missing arguments, the command should be: %delayType <delay in ms>|<Text to type>")
    }

    val delay = argumentList[0].toLong()
    val text = argumentList[1]
    val calculateAnalyzesTime = argumentList.size > 2 && argumentList[2].toBoolean()
    val disableWriteProtection = argumentList.size > 3 && argumentList[3].toBoolean()

    val latencyRecorder = LatencyRecord()
    val applicationConnection = ApplicationManager.getApplication().messageBus.connect()
    applicationConnection.subscribe(LatencyListener.TOPIC, LatencyListener { _, _, latencyMs ->
      latencyRecorder.update(latencyMs.toInt())
    })

    withContext(Dispatchers.EDT) {
      ProjectUtil.focusProjectWindow(project, true)
    }

    runBlocking {
      withTimeout(10.seconds) {
        while (findTypingTarget(project) == null) {
          delay(1.seconds)
        }
      }
    }

    val codeAnalysisJob = Ref<DaemonCodeAnalyzerResult>()
    val projectConnection = project.messageBus.simpleConnect()

    PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME).setParent(PerformanceTestSpan.getContext()).useWithScope { span ->
      coroutineScope {
        List(text.length) { i ->
          launch {
            delay(i * delay)
            withContext(Dispatchers.EDT) {
              span.addEvent("Calling find target second time in DelayTypeCommand")
              val typingTarget = findTypingTarget(project)
              if (typingTarget == null) {
                throw Exception("Focus was lost during typing. Current focus is in: " +
                                (KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner?.javaClass ?: "null"))
              }
              if (disableWriteProtection) {
                val editor = DataManager.getInstance().getDataContext(typingTarget as? JComponent).getData(CommonDataKeys.EDITOR)
                if (editor == null) {
                  throw Exception("Cannot find Editor")
                }
                NonProjectFileWritingAccessProvider.allowWriting(listOf(editor.virtualFile))
              }
              span.addEvent("Typing ${text[i]}")
              writeIntentReadAction {
                typingTarget.type(text[i].toString())
              }
            }
          }
        }

        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        HighlightingTestUtil.storeProcessFinishedTime(
          scopeName = "delayTyping",
          spanName = "typing_target_${editor?.virtualFile?.name}",
          additionalAttributes = arrayOf(Pair("typed_text", text)))
      }
      if (calculateAnalyzesTime) {
        val spanRef = Ref<Span>(PerformanceTestSpan.TRACER.spanBuilder(CODE_ANALYSIS_SPAN_NAME).setParent(Context.current().with(span)).startSpan())
        codeAnalysisJob.set(DaemonCodeAnalyzerListener.listen(projectConnection, spanRef, 0, null))
        launch {
          while (!codeAnalysisJob.get().isDone()) {
            CloseLookupCommand.closeLookup(project)
            delay(1.seconds)
          }
        }
      }

      if (!latencyRecorder.samples.isEmpty) {
        span.setAttribute("latency#max", latencyRecorder.maxLatency.toLong())
        span.setAttribute("latency#p90", latencyRecorder.percentile(90).toLong())
        span.setAttribute("latency#mean_value", latencyRecorder.averageLatency)
      }
    }

    if (calculateAnalyzesTime) {
      codeAnalysisJob.get().blockingWaitForComplete()
    }
    projectConnection.disconnect()
    applicationConnection.disconnect()
  }

}
