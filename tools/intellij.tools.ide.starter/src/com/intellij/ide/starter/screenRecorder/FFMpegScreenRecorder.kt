package com.intellij.ide.starter.screenRecorder

import com.intellij.ide.starter.coroutine.CommonScope.testSuiteSupervisorScope
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.pathString
import kotlin.time.Duration

class FFMpegScreenRecorder(recordingPath: Path, recordingFilePrefix: String, private val display: String, private val timeout: Duration) :
  IDEScreenRecorder(recordingPath, recordingFilePrefix) {
  private var ffmpegProcessJob: Job? = null

  override fun start() {
    check(!isStarted()) { "FFMpeg screen recorder is already started" }

    logOutput("FFMpeg screen recorder: starting")
    ffmpegProcessJob = testSuiteSupervisorScope.launch(Dispatchers.IO + CoroutineName("FFMpeg recording")) { startFFMpegRecording() }
  }

  override fun stop() {
    if (!isStarted()) {
      logOutput("FFMpeg screen recorder was not started")
    }
    else {
      logOutput("FFMpeg screen recorder: stopping")
      runBlocking {
        ffmpegProcessJob?.cancelAndJoin()
      }
    }
  }

  override fun isStarted(): Boolean = ffmpegProcessJob?.isActive == true

  private fun getDisplaySize(displayWithColumn: String, defaultValue: Pair<Int, Int> = 1920 to 1080): Pair<Int, Int> {
    try {
      val commandName = "xdpyinfo"
      logOutput("Getting a size for a display $displayWithColumn")
      val stdout = ExecOutputRedirect.ToString()
      ProcessExecutor(
        presentableName = "$commandName -display $displayWithColumn",
        args = listOf(commandName, "-display", displayWithColumn),
        workDir = null,
        expectedExitCode = 0,
        stdoutRedirect = stdout,
        stderrRedirect = ExecOutputRedirect.ToStdOut("[$commandName-err]"),
      ).start()

      val screenDataOutput = stdout.read().trim()
      val regex = """dimensions:\s*(\d+)x(\d+)\s*pixels""".toRegex()
      val matchResult = regex.find(screenDataOutput)
      val (width, height) = matchResult?.groupValues?.let { Pair(it[1].toInt(), it[2].toInt()) } ?: error("Could not determine screen data")
      logOutput("Getting a size for a display $displayWithColumn finished with $width x $height")
      return width to height
    }
    catch (e: Exception) {
      logOutput("Failed to get a size for a display $displayWithColumn: ${e.message}")
      return defaultValue
    }
  }

  private suspend fun startFFMpegRecording() {
    ensureRecordingDirExists()

    val recordingFile = recordingDir / "$recordingFilePrefix.mkv"
    val ffmpegLogFile =
      (recordingDir / "ffmpeg-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH_mm_ss_SSS"))}.log").createFile()
    val args = listOf("/usr/bin/ffmpeg",
                      "-f",
                      "x11grab",
                      "-video_size",
                      getDisplaySize(display).let { "${it.first}x${it.second}" },
                      "-framerate",
                      "24",
                      "-i",
                      display,
                      "-codec:v",
                      "libx264",
                      "-preset",
                      "superfast",
                      recordingFile.pathString)
    logOutput("Start screen recording to $recordingFile\nArgs: ${args.joinToString(" ")}")
    try {
      ProcessExecutor(
        presentableName = args.joinToString(" "),
        args = args,
        environmentVariables = mapOf("DISPLAY" to display),
        workDir = null,
        expectedExitCode = 0,
        stdoutRedirect = ExecOutputRedirect.ToFile(ffmpegLogFile.toFile()),
        stderrRedirect = ExecOutputRedirect.ToFile(ffmpegLogFile.toFile()),
        timeout = timeout,
      ).startCancellable()
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      logOutput("Failed to start ffmpeg recording: ${e.message}")
    }
  }
}
