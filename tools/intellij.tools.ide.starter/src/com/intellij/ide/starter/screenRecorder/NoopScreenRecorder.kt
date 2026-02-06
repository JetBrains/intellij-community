package com.intellij.ide.starter.screenRecorder

import java.nio.file.Path

internal class NoopScreenRecorder(recordingPath: Path, recordingFilePrefix: String): IDEScreenRecorder(recordingPath, recordingFilePrefix) {
  override fun start() = Unit
  override fun stop() = Unit
  override fun isStarted(): Boolean = false
}
