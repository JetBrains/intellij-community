package com.intellij.ide.starter.screenRecorder

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
    fun create(
      runContext: IDERunContext,
      recordingDir: Path = runContext.logsDir.resolve("screenRecording"),
      recordingFilePrefix: String = "ScreenRecording ${SimpleDateFormat("yyyy-MM-dd 'at' HH.mm.ss").format(Date())}",
    ): IDEScreenRecorder {
      val options = runContext.calculateVmOptions()
      return when {
        StartupUiUtil.isWayland -> NoopScreenRecorder(recordingDir, recordingFilePrefix).also {
          logOutput("Screen recording is disabled because on Wayland it triggers system dialog about granting permissions each time, and it can't be disabled.")
        }
        options.hasHeadlessMode() -> NoopScreenRecorder(recordingDir, recordingFilePrefix).also {
          logOutput("Screen recording is disabled because IDE is started in headless mode.")
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