// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.awt.image.Raster
import java.awt.image.WritableRaster
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

internal class ScreenshotPublicationTest {
  @TempDir
  lateinit var directory: Path

  @ParameterizedTest
  @ValueSource(strings = ["png", "jpg", "jpeg", "bmp", "gif", "tif", "tiff", "wbmp"])
  fun `publishes the final image only after encoding finishes`(format: String) {
    val file = directory.resolve("heartbeat.$format").toFile()
    var pixelsRead = false
    // A collector can inspect the final path while the IDE still encodes the image.
    // ObservedImage intercepts the raster accessors that these ImageIO encoders call.
    // Run the collector's visibility check from that callback to reproduce this ordering on one thread, without sleeps.
    val image = ObservedImage {
      pixelsRead = true
      assertFalse(file.exists(), "The collector must not see the image while it is being encoded")
    }

    writeScreenshotAtomically(image, format, file)

    // Require the encoder to reach the hook so this test always checks visibility during the write.
    assertTrue(pixelsRead, "The assertion must run during encoding")
    assertEquals(10, ImageIO.read(file).width)
    Files.list(directory).use { assertEquals(listOf(file.toPath()), it.toList()) }
  }

  @ParameterizedTest
  @ValueSource(strings = ["png", "jpg", "jpeg", "bmp", "gif", "tif", "tiff", "wbmp"])
  fun `aborts the temporary output when encoding fails`(format: String) {
    val file = directory.resolve("heartbeat.$format").toFile()
    // Use the same raster hook to throw while the real ImageIO encoder reads the pixels.
    // This checks failure cleanup with the actual output stream already open.
    val image = ObservedImage { throw IOException("Cannot read the screenshot pixels") }

    assertThrows<IOException> { writeScreenshotAtomically(image, format, file) }

    Files.list(directory).use { assertEquals(emptyList<Path>(), it.toList()) }
  }

  /**
   * Runs the real encoder and a collector on separate threads with a controlled interleaving.
   * The raster callback holds the encoder until the collector checks the visible PNG paths.
   * This schedule exercises collection during encoding without depending on image size, sleeps, or thread timing.
   */
  @Test
  fun `a concurrent collector sees only a completed PNG`() {
    val file = directory.resolve("heartbeat.png")
    val encodingStarted = CountDownLatch(1)
    val allowEncodingToFinish = CountDownLatch(1)
    val image = ObservedImage {
      // The callback controls scheduling only. ImageIO, the output stream, and the filesystem remain real.
      encodingStarted.countDown()
      assertTrue(allowEncodingToFinish.await(10, TimeUnit.SECONDS), "The collector did not release the encoder")
    }
    val encoding = FutureTask { writeScreenshotAtomically(image, "png", file.toFile()) }
    val writer = Thread(encoding, "screenshot-encoder").apply { isDaemon = true }

    try {
      writer.start()
      // This test thread acts as the collector while the worker is inside ImageIO, waiting at the raster hook.
      assertTrue(encodingStarted.await(10, TimeUnit.SECONDS), "The encoder did not reach the raster hook")
      assertEquals(emptyList<Path>(), publishedPngFiles(), "An unfinished PNG must not be visible to the collector")

      allowEncodingToFinish.countDown()
      // Future.get propagates encoder failures to the test thread and bounds the wait for publication.
      encoding.get(10, TimeUnit.SECONDS)
      val screenshots = publishedPngFiles()
      assertEquals(listOf(file), screenshots)
      val decoded = ImageIO.read(screenshots.single().toFile())
      assertEquals(image.width, decoded.width)
      assertEquals(image.height, decoded.height)
    }
    finally {
      // Release and stop the writer even when a collector assertion fails, so the test cannot leave it blocked.
      allowEncodingToFinish.countDown()
      writer.interrupt()
      writer.join(TimeUnit.SECONDS.toMillis(10))
      assertFalse(writer.isAlive, "The screenshot writer did not stop")
    }
  }

  /** Enumerates the final PNG filenames that a screenshot collector would discover, excluding `.tmp` outputs. */
  private fun publishedPngFiles(): List<Path> =
    Files.list(directory).use { files -> files.filter { it.fileName.toString().endsWith(".png") }.toList() }

  /**
   * Calls [onRead] when a real ImageIO encoder accesses the image pixels.
   *
   * Encoders use different raster accessors, so each override invokes the same callback.
   * The callback runs on the encoding thread. Tests can check file visibility or inject a failure without sleeps.
   * The binary color model also supports WBMP.
   *
   * The initialization guard suppresses callbacks during superclass construction, before [onRead] is assigned.
   */
  private class ObservedImage(private val onRead: () -> Unit) : BufferedImage(10, 10, TYPE_BYTE_BINARY) {
    private var initialized = false

    init {
      initialized = true
    }

    override fun getRaster(): WritableRaster {
      if (initialized) onRead()
      return super.getRaster()
    }

    override fun getData(): Raster {
      if (initialized) onRead()
      return super.getData()
    }

    override fun getData(rect: Rectangle): Raster {
      if (initialized) onRead()
      return super.getData(rect)
    }

    override fun getTile(tileX: Int, tileY: Int): Raster {
      if (initialized) onRead()
      return super.getTile(tileX, tileY)
    }
  }
}
