package com.intellij.ide.starter.screenRecorder

import com.intellij.ide.starter.coroutine.testSuiteSupervisorScope
import com.intellij.ide.starter.ide.DEFAULT_DISPLAY_ID
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.OS
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.monte.media.Format
import org.monte.media.FormatKeys.MediaType
import org.monte.media.Registry
import org.monte.media.VideoFormatKeys.*
import org.monte.media.math.Rational
import org.monte.screenrecorder.ScreenRecorder
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.pathString

class IDEScreenRecorder(private val runContext: IDERunContext) {

  companion object {
    fun getScreenRecorder(movieFolder: File, filePrefix: String? = null) =
      object : ScreenRecorder(
        /* cfg = */
        GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration,

        /* captureArea = */
        Rectangle(0, 0, Toolkit.getDefaultToolkit().screenSize.width, Toolkit.getDefaultToolkit().screenSize.height),

        /* fileFormat = */
        Format(MediaTypeKey, MediaType.FILE, MimeTypeKey, MIME_AVI),

        /* screenFormat = */
        Format(MediaTypeKey, MediaType.VIDEO,
               EncodingKey, ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE,
               CompressorNameKey, ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE,
               DepthKey, 24,
               FrameRateKey, Rational.valueOf(15.0),
               QualityKey, 1.0f,
               KeyFrameIntervalKey, 15 * 60),

        /* mouseFormat = */
        Format(MediaTypeKey, MediaType.VIDEO,
               EncodingKey, "black",
               FrameRateKey, Rational.valueOf(30.0)),


        /* audioFormat = */
        null,

        /* movieFolder = */
        movieFolder) {
        override fun createMovieFile(fileFormat: Format): File {
          if (filePrefix == null) {
            return super.createMovieFile(fileFormat)
          }
          else {
            return movieFolder.resolve(getMovieName(fileFormat, filePrefix))
          }
        }
      }

    private fun getMovieName(fileFormat: Format?, filePrefix: String?): String {
      val dateFormat = SimpleDateFormat("yyyy-MM-dd 'at' HH.mm.ss")
      val prefix = filePrefix ?: "ScreenRecording"
      val extension = Registry.getInstance().getExtension(fileFormat)
      val timestamp = dateFormat.format(Date())

      return "$prefix ${timestamp}.$extension"
    }
  }

  val javaScreenRecorder: ScreenRecorder? by lazy {
    if (OS.CURRENT != OS.Linux) {
      runCatching { getScreenRecorder((runContext.logsDir / "screenRecording").toFile()) }.getOrLogException { logOutput("Can't create screen recorder: ${it.stackTraceToString()}") }
    }
    else null
  }
  var ffmpegProcessJob: Job? = null

  fun start() {
    if (StartupUiUtil.isWayland) {
      logOutput("Screen recording is disabled because on Wayland it triggers system dialog about granting permissions each time, and it can't be disabled.")
      return
    }
    if (runContext.calculateVmOptions().hasHeadlessMode()) {
      logOutput("Screen recording is disabled because IDE is started in headless mode.")
      return
    }

    logOutput("Screen recorder: starting")
    synchronized(this) {
      if (javaScreenRecorder != null) {
        javaScreenRecorder?.start()
      }
      else if (OS.CURRENT == OS.Linux) {
        if (ffmpegProcessJob == null) {
          ffmpegProcessJob = testSuiteSupervisorScope.launch(Dispatchers.IO) { startFFMpegRecording(runContext) }
        }
      }
    }
  }

  suspend fun stop() {
    if (javaScreenRecorder == null && ffmpegProcessJob == null) {
      logOutput("Screen recorder was not started")
    }
    logOutput("Screen recorder: stopping")
    javaScreenRecorder?.stop()
    ffmpegProcessJob?.cancelAndJoin()
  }

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

  private suspend fun startFFMpegRecording(ideRunContext: IDERunContext) {
    val processVmOptions = ideRunContext.calculateVmOptions()
    val processDisplay = processVmOptions.environmentVariables["DISPLAY"] ?: System.getenv("DISPLAY") ?: ":$DEFAULT_DISPLAY_ID"
    val recordingFile = ideRunContext.logsDir / "screen-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH_mm_ss_SSS"))}.mkv"
    val ffmpegLogFile = (ideRunContext.logsDir / "ffmpeg-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH_mm_ss_SSS"))}.log").also { it.createFile() }
    val args = listOf("/usr/bin/ffmpeg", "-f", "x11grab", "-video_size", getDisplaySize(processDisplay).let { "${it.first}x${it.second}" }, "-framerate", "24", "-i",
                      processDisplay,
                      "-codec:v", "libx264", "-preset", "superfast", recordingFile.pathString)
    logOutput("Start screen recording to $recordingFile\nArgs: ${args.joinToString(" ")}")
    try {
      ProcessExecutor(
        presentableName = args.joinToString(" "),
        args = args,
        environmentVariables = mapOf("DISPLAY" to processDisplay),
        workDir = null,
        expectedExitCode = 0,
        stdoutRedirect = ExecOutputRedirect.ToFile(ffmpegLogFile.toFile()),
        stderrRedirect = ExecOutputRedirect.ToFile(ffmpegLogFile.toFile()),
        timeout = ideRunContext.runTimeout,
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