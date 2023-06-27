package com.jetbrains.performancePlugin.commands

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonFusReporter
import com.intellij.codeInsight.daemon.impl.FileStatusMap
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.ConcurrencyUtil
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import kotlin.io.path.readLines


class WaitForFinishedCodeAnalysis(text: String, line: Int) : AbstractCommand(text, line) {
  companion object {
    const val PREFIX = CMD_PREFIX + "waitForFinishedCodeAnalysis"
    private val LOG = Logger.getInstance(WaitForFinishedCodeAnalysis::class.java)
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback = ActionCallbackProfilerStopper()
    val project = context.project
    val connection = project.messageBus.simpleConnect()
    val dateTimeWhenCodeAnalysisFinished = Ref<LocalDateTime>()
    LOG.info("Subscribing")
    val wasEntireFileHighlighted = Ref<Boolean>(false)
    connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonFusReporter(project) {
      override fun daemonFinished(fileEditors: Collection<FileEditor>) {
        val editor = fileEditors.filterIsInstance<TextEditor>().firstOrNull()?.editor
        val document = editor?.document

        if (document == null) return
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
        if (psiFile == null) return
        val dirtyRange = FileStatusMap.getDirtyTextRange(document, psiFile, Pass.UPDATE_ALL)

        if (!canceled) {
          wasEntireFileHighlighted.set(dirtyRange == null)
        }
        if (wasEntireFileHighlighted.get()) {
          dateTimeWhenCodeAnalysisFinished.set(LocalDateTime.now())
        }
      }
    })
    ApplicationManager.getApplication().executeOnPooledThread(Runnable {
      checkCondition(BooleanSupplier { wasEntireFileHighlighted.get() }).await(20, TimeUnit.MINUTES)
      connection.disconnect()
      val rowFirstDateTimeFromLog = Paths.get(PathManager.getLogPath(), "idea.log")
        .readLines()
        .first()
        .substringBefore("[")
        .replace(",", ".")
        .trim()
      val dateTimeWhenAppStarted = LocalDateTime.parse(rowFirstDateTimeFromLog, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
      LOG.info("Total opening time is : ${ChronoUnit.MILLIS.between(dateTimeWhenAppStarted, dateTimeWhenCodeAnalysisFinished.get())}")
      actionCallback.setDone()
    })

    return actionCallback.toPromise()
  }

  fun checkCondition(function: BooleanSupplier): CountDownLatch {
    val latch = CountDownLatch(1)
    val executor: ScheduledExecutorService = ConcurrencyUtil.newSingleScheduledThreadExecutor("Performance plugin waiter")
    executor.scheduleWithFixedDelay({
                                      if (function.asBoolean) {
                                        latch.countDown()
                                        executor.shutdown()
                                      }
                                    }, 0, 5, TimeUnit.SECONDS)
    return latch
  }
}