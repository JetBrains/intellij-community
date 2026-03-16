@file:Suppress("IO_FILE_USAGE")

package com.intellij.ide.starter.screenRecorder

import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.tools.ide.util.common.logOutput
import org.monte.media.Format
import org.monte.media.FormatKeys.MediaType
import org.monte.media.Registry
import org.monte.media.VideoFormatKeys.CompressorNameKey
import org.monte.media.VideoFormatKeys.DepthKey
import org.monte.media.VideoFormatKeys.ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE
import org.monte.media.VideoFormatKeys.EncodingKey
import org.monte.media.VideoFormatKeys.FrameRateKey
import org.monte.media.VideoFormatKeys.KeyFrameIntervalKey
import org.monte.media.VideoFormatKeys.MIME_AVI
import org.monte.media.VideoFormatKeys.MediaTypeKey
import org.monte.media.VideoFormatKeys.MimeTypeKey
import org.monte.media.VideoFormatKeys.QualityKey
import org.monte.media.math.Rational
import org.monte.screenrecorder.ScreenRecorder
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.io.File
import java.nio.file.Path

class JavaScreenRecorder(
  recordingPath: Path,
  recordingFilePrefix: String,
) : IDEScreenRecorder(recordingPath, recordingFilePrefix) {

  companion object {
    private fun createMonteScreenRecorder(recordingPath: Path, filePrefix: String): ScreenRecorder =
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
        recordingPath.toFile()) {

        override fun createMovieFile(fileFormat: Format): File {
          return movieFolder.resolve("$filePrefix.${Registry.getInstance().getExtension(fileFormat)}")
        }
      }
  }

  private val monteRecorder: ScreenRecorder? by lazy {
    runCatching {
      createMonteScreenRecorder(recordingPath, recordingFilePrefix)
    }.getOrHandleException { logOutput("Can't create screen recorder: ${it.stackTraceToString()}") }
  }

  override fun start() {
    check(!isStarted()) { "Java screen recorder is already started" }

    logOutput("Java screen recorder: starting")
    ensureRecordingDirExists()
    monteRecorder?.start()
  }

  override fun stop() {
    if (!isStarted()) {
      logOutput("Java screen recorder was not started")
    }
    else {
      logOutput("Java screen recorder: stopping")
      monteRecorder?.stop()
    }
  }

  override fun isStarted(): Boolean = monteRecorder?.state == ScreenRecorder.State.RECORDING
}
