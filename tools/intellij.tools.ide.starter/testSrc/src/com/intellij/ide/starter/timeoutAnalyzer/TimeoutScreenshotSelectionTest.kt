// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.timeoutAnalyzer

import com.intellij.ide.starter.report.TimeoutAnalyzer
import com.intellij.util.io.PreemptiveSafeFileOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class TimeoutScreenshotSelectionTest {
  @TempDir
  lateinit var logs: Path

  @Test
  fun `uses the previous heartbeat until the latest image is published`() {
    val previous = Files.createFile(heartbeat("heartbeat-001").resolve("frame0.png"))
    val current = heartbeat("heartbeat-002").resolve("frame0.png")

    PreemptiveSafeFileOutputStream(current).use { output ->
      output.write(1)
      assertEquals(listOf(previous), TimeoutAnalyzer.getLastScreenshots(logs))
    }

    assertEquals(listOf(current), TimeoutAnalyzer.getLastScreenshots(logs))
  }

  @Test
  fun `selects all published images from the latest heartbeat only`() {
    Files.createFile(heartbeat("heartbeat-001").resolve("frame0.png"))
    val directory = heartbeat("heartbeat-002")
    val screenshots = listOf(
      Files.createFile(directory.resolve("frame0.png")),
      Files.createFile(directory.resolve("frame1.png")),
    )
    Files.createFile(directory.resolve("frame2.png.tmp"))
    Files.createFile(directory.resolve("frame3.png~"))
    Files.createDirectory(directory.resolve("nested"))

    assertEquals(screenshots.sorted(), TimeoutAnalyzer.getLastScreenshots(logs).sorted())
  }

  @Test
  fun `reports no screenshot when only temporary outputs and backups exist`() {
    for (folder in listOf("screenshots/heartbeat-001", "beforeIdeClosed")) {
      val directory = Files.createDirectories(logs.resolve(folder))
      Files.createFile(directory.resolve("frame0.png.tmp"))
      Files.createFile(directory.resolve("frame0.png~"))

      assertEquals(emptyList<Path>(), TimeoutAnalyzer.getLastScreenshots(logs), folder)
    }
  }

  @Test
  fun `uses a heartbeat until the before-close image is published`() {
    val heartbeat = Files.createFile(heartbeat("heartbeat-001").resolve("frame0.png"))
    val beforeClose = Files.createDirectories(logs.resolve("beforeIdeClosed")).resolve("frame0.png")

    PreemptiveSafeFileOutputStream(beforeClose).use { output ->
      output.write(1)
      assertEquals(listOf(heartbeat), TimeoutAnalyzer.getLastScreenshots(logs))
    }

    assertEquals(listOf(beforeClose), TimeoutAnalyzer.getLastScreenshots(logs))
  }

  private fun heartbeat(name: String): Path = Files.createDirectories(logs.resolve("screenshots").resolve(name))
}
