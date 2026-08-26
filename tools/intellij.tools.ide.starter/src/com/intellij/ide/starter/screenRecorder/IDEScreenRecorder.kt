package com.intellij.ide.starter.screenRecorder

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.useDockerContainer
import com.intellij.ide.starter.ide.DEFAULT_DISPLAY_ID
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.OS
import com.intellij.util.ui.StartupUiUtil
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.io.path.createDirectories

abstract class IDEScreenRecorder(protected val recordingDir: Path, protected val recordingFilePrefix: String) {

  companion object {
    /** The directory a recording of a run goes into, wherever it was made, so that every run keeps it in the same place. */
    fun recordingDirIn(logsDir: Path): Path = logsDir.resolve("screenRecording")

    /** The name of a recording, timestamped because one directory can end up holding the recordings of several launches. */
    fun recordingFilePrefix(): String = "ScreenRecording ${SimpleDateFormat("yyyy-MM-dd 'at' HH.mm.ss").format(Date())}"

    /**
     * Picks a recorder for [runContext] among the ways *this* machine can grab *its own* screen.
     *
     * That is the whole of the decision made here, and every recorder below is local for that reason. A run whose IDE
     * draws somewhere else answers none of these questions: a dockerized one draws on an `Xvfb` inside its container,
     * whose mount and network namespaces this process does not share, so no display it could name is the one on screen.
     * Such a run records itself, from the inside, where both the display and `ffmpeg` are - and gets a
     * [NoopScreenRecorder] here, so that nothing on this side writes a file it cannot fill.
     */
    fun create(
      runContext: IDERunContext,
      recordingDir: Path = recordingDirIn(runContext.lastIdeReportingData.logsDir),
      recordingFilePrefix: String = recordingFilePrefix(),
    ): IDEScreenRecorder {
      val options = runContext.calculateVmOptions()
      if (options.hasHeadlessMode()) {
        logOutput("Screen recording is disabled because IDE is started in headless mode.")
        return NoopScreenRecorder(recordingDir, recordingFilePrefix)
      }
      if (ConfigurationStorage.useDockerContainer()) {
        logOutput("Screen recording is left to the container: the display the IDE draws on is inside it, not on this machine.")
        return NoopScreenRecorder(recordingDir, recordingFilePrefix)
      }
      return when {
        StartupUiUtil.isWayland -> NoopScreenRecorder(recordingDir, recordingFilePrefix).also {
          logOutput("Screen recording is disabled because on Wayland it triggers system dialog about granting permissions each time, and it can't be disabled.")
        }
        OS.CURRENT == OS.Linux -> FFMpegScreenRecorder(recordingDir,
                                                       recordingFilePrefix,
                                                       options.environmentVariables["DISPLAY"] ?: System.getenv("DISPLAY") ?: ":$DEFAULT_DISPLAY_ID",
                                                       runContext.runTimeout)
        else -> JavaScreenRecorder(recordingDir, recordingFilePrefix)
      }
    }
  }

  abstract fun start()
  abstract fun stop()
  abstract fun isStarted(): Boolean
  protected fun ensureRecordingDirExists() = recordingDir.createDirectories()
}