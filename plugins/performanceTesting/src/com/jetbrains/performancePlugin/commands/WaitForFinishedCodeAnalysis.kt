package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.Ref
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
    connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonCodeAnalyzer.DaemonListener {
      override fun daemonFinished() {
        dateTimeWhenCodeAnalysisFinished.set(LocalDateTime.now())
      }
    })
    ApplicationManager.getApplication().executeOnPooledThread(Runnable {
      checkCondition(BooleanSupplier { CoreProgressManager.getCurrentIndicators().size == 0 && !dateTimeWhenCodeAnalysisFinished.isNull }).await(20, TimeUnit.MINUTES)
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