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

  companion object {
    /** Where `ffmpeg` is on a machine that runs the tests; a container image has it on the `PATH` instead. */
    const val DEFAULT_EXECUTABLE: String = "/usr/bin/ffmpeg"

    private const val FRAMERATE: String = "24"

    /**
     * The command this class runs, for whoever has to run it somewhere this process cannot reach.
     *
     * A run whose IDE draws on a display of its own - inside a container, on another host - deserves the same recording as
     * any other: same input, same timestamp overlay, same codec, same file format. Only the starting of it differs, so
     * that is all a caller has to bring. [fontFile] is for images too slim for fontconfig to answer, since `drawtext`
     * fails the whole recording when it resolves no font; a machine with fonts installed needs nothing here.
     */
    fun recordingArgs(
      display: String,
      videoSize: String,
      outputFile: String,
      executable: String = DEFAULT_EXECUTABLE,
      fontFile: String? = null,
    ): List<String> = listOf(executable, "-f", "x11grab", "-video_size", videoSize, "-framerate", FRAMERATE, "-i", display) +
                      listOf("-vf", timestampFilter(fontFile)) +
                      listOf("-codec:v", "libx264", "-preset", "superfast", outputFile)

    private fun timestampFilter(fontFile: String?): String = buildString {
      append("drawtext=")
      if (fontFile != null) append("fontfile=$fontFile:")
      append("text='%{localtime\\:%F %T}':")
      append("fontcolor=white:")
      append("fontsize=20:")
      append("box=1:boxcolor=black@0.6:boxborderw=6:")
      append("x=10:y=10")
    }
  }

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
    val args = recordingArgs(display = display,
                             videoSize = getDisplaySize(display).let { "${it.first}x${it.second}" },
                             outputFile = recordingFile.pathString)
    logOutput("Start screen recording to $recordingFile\nArgs: ${args.joinToString(" ")}")
    try {
      ProcessExecutor(
        presentableName = args.joinToString(" "),
        args = args,
        environmentVariables = mapOf("DISPLAY" to display),
        workDir = null,
        expectedExitCode = 0,
        stdoutRedirect = ExecOutputRedirect.ToFile(ffmpegLogFile),
        stderrRedirect = ExecOutputRedirect.ToFile(ffmpegLogFile),
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
