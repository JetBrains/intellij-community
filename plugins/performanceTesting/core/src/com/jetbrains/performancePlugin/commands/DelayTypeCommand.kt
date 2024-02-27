package com.jetbrains.performancePlugin.commands

import com.intellij.internal.performance.LatencyRecord
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.actionSystem.LatencyListener
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.KeyCodeTypeCommand
import com.intellij.openapi.util.Ref
import com.intellij.platform.diagnostic.telemetry.helpers.TraceUtil
import com.intellij.util.ConcurrencyUtil
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.commands.Waiter.checkCondition
import com.jetbrains.performancePlugin.utils.DaemonCodeAnalyzerListener.listen
import com.jetbrains.performancePlugin.utils.DaemonCodeAnalyzerResult
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.awt.KeyboardFocusManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Command types text with some delay between typing.
 * Text and delay are being set as parameters.
 * Syntax: %delayType <delay in ms>|<Text to type>
 * Example: %delayType 150|Sample text for typing scenario
</Text></delay> */
class DelayTypeCommand(text: String, line: Int) : KeyCodeTypeCommand(text, line) {
  private val myExecutor: ScheduledExecutorService = ConcurrencyUtil.newSingleScheduledThreadExecutor("Performance plugin delayed type")

  override fun _execute(context: PlaybackContext): Promise<Any> {
    val result = AsyncPromise<Any>()

    val input = extractCommandArgument(PREFIX)
    val delayText = input.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    val delay = delayText[0].toLong()
    val text = delayText[1] + END_CHAR
    val calculateAnalyzesTime = delayText.size > 2 && delayText[2].toBoolean()
    val spanRef = Ref<Span>()
    val projectConnection = context.project.messageBus.simpleConnect()
    val applicationConnection = ApplicationManager.getApplication().messageBus.connect()

    val latencyRecorder = LatencyRecord()
    applicationConnection.subscribe(LatencyListener.TOPIC, LatencyListener { _, _, latencyMs ->
      latencyRecorder.update(latencyMs.toInt())
    })

    val job = Ref<DaemonCodeAnalyzerResult>()
    ApplicationManager.getApplication().executeOnPooledThread(Context.current().wrap(
      Runnable {
        TraceUtil.runWithSpanThrows<RuntimeException>(PerformanceTestSpan.TRACER, SPAN_NAME) { span: Span ->
          span.addEvent("Finding typing target")
          try {
            checkCondition { findTarget(context) != null }.await(1, TimeUnit.MINUTES)
          }
          catch (e: InterruptedException) {
            span.recordException(e)
            result.setError(e)
            return@runWithSpanThrows
          }

          val allScheduled = CountDownLatch(1)
          for (i in text.indices) {
            val currentChar = text[i]
            val nextCharIsTheLast = ((i + 1) < text.length) && (text[i + 1] == END_CHAR)
            myExecutor.schedule({
                                  ApplicationManager.getApplication().invokeAndWait(Context.current().wrap(
                                    Runnable {
                                      if (nextCharIsTheLast && calculateAnalyzesTime) {
                                        job.set(listen(projectConnection, spanRef, 0, null))
                                        val spanBuilder = PerformanceTestSpan.TRACER.spanBuilder(CODE_ANALYSIS_SPAN_NAME).setParent(
                                          Context.current().with(span))
                                        spanRef.set(spanBuilder.startSpan())
                                      }
                                      if (currentChar == END_CHAR) {
                                        allScheduled.countDown()
                                        myExecutor.shutdown()
                                      }
                                      else {
                                        span.addEvent("Calling find target second time in DelayTypeCommand")
                                        val typingTarget = findTarget(context)
                                        if (typingTarget != null) {
                                          span.addEvent("Typing $currentChar")
                                          typingTarget.type(currentChar.toString())
                                        }
                                        else {
                                          span.addEvent("Focus was lost")
                                          result.setError(
                                            "Focus was lost during typing. Current focus is in: " + KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner.javaClass)
                                        }
                                      }
                                    }))
                                }, i * delay, TimeUnit.MILLISECONDS)
          }
          try {
            allScheduled.await()
            myExecutor.awaitTermination(1, TimeUnit.MINUTES)

            if (!latencyRecorder.samples.isEmpty) {
              span.setAttribute("latency#max", latencyRecorder.maxLatency.toLong())
              span.setAttribute("latency#p90", latencyRecorder.percentile(90).toLong())
              span.setAttribute("latency#mean_value", latencyRecorder.averageLatency)
            }
          }
          catch (e: InterruptedException) {
            result.setError(e)
          }
        }
        if (calculateAnalyzesTime) {
          job.get().blockingWaitForComplete()
        }
        applicationConnection.disconnect()
        result.setResult(null)
      }))

    return result
  }

  companion object {
    const val PREFIX: String = CMD_PREFIX + "delayType"
    const val END_CHAR: Char = '#'
    const val SPAN_NAME: String = "typing"
    const val CODE_ANALYSIS_SPAN_NAME: String = "typingCodeAnalyzing"
  }
}
