// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.util.system.OS
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.StartupUiUtil
import com.jetbrains.performancePlugin.LogDirHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.awt.AWTException
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.Window
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.seconds

private val LOG: Logger
  get() = logger<TakeScreenshotCommand>()

private val screenshotSequence = AtomicInteger(1)

/**
 * Command takes screenshot.
 * Takes JPG screenshot of current screen to subfolder
 * (or if empty parameter is specified, the file will be stored under the LOG dir).
 *
 *
 * Syntax: %takeScreenshot <path_to_subfolder_if_needed>
 * Example: %takeScreenshot onExit
</fullPathToFile> */
class TakeScreenshotCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {

  @Suppress("UNUSED") //Needs for Driver
  constructor() : this("", 0)

  companion object {
    const val PREFIX: String = CMD_PREFIX + "takeScreenshot"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    takeScreenshotOfAllWindows(extractCommandArgument(PREFIX).ifEmpty { "beforeExit" })
  }

  @Suppress("UNUSED") //Needs for Driver
  fun takeScreenshot(childFolder: String?) {
    runBlocking { takeScreenshotOfAllWindows(childFolder) }
  }
}

fun takeScreenshotWithAwtRobot(fullPathToFile: String, formatName: String = "jpg") {
  val image = captureScreenWithAwtRobot() ?: return
  writeScreenshot(image, formatName, File(fullPathToFile))
}

private fun captureScreenWithAwtRobot(): BufferedImage? {
  try {
    return Robot().createScreenCapture(Rectangle(Toolkit.getDefaultToolkit().screenSize))
  }
  catch (e: AWTException) {
    LOG.info("Exceptions occurs at attempt to create Robot for taking screenshot")
    LOG.info(e)
    return null
  }
}

private fun writeScreenshot(image: BufferedImage, formatName: String, file: File) {
  try {
    ImageIO.write(image, formatName, file)
    if (file.exists()) {
      LOG.info("Screenshot saved: ${file.absolutePath}")
    }
  }
  catch (e: IOException) {
    LOG.info("Exceptions occurs at attempt to write screenshot to file")
    LOG.info(e)
  }
}

private fun isBlack(image: BufferedImage): Boolean {
  val row = IntArray(image.width)
  for (y in 0 until image.height) {
    image.getRGB(0, y, image.width, 1, row, 0, image.width)
    if (row.any { (it and 0xFFFFFF) != 0 }) return false
  }
  return true
}

// paints each showing window at its place on the screen
private fun paintShowingWindows(width: Int, height: Int): BufferedImage {
  val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
  // on the EDT, invokeAndWait paints at once
  ApplicationManager.getApplication().invokeAndWait({ paintShowingWindowsInto(image) }, ModalityState.any())
  return image
}

private fun paintShowingWindowsInto(image: BufferedImage) {
  val graphics = image.createGraphics()
  try {
    // the order of creation, so that a dialog or a popup is usually painted over its frame
    for (window in Window.getWindows()) {
      if (!window.isShowing || window.width == 0 || window.height == 0) continue
      val location = window.locationOnScreen
      val windowGraphics = graphics.create(location.x, location.y, window.width, window.height)
      try {
        window.paint(windowGraphics)
      }
      finally {
        windowGraphics.dispose()
      }
    }
  }
  finally {
    graphics.dispose()
  }
}

suspend fun captureComponent(component: Component, file: File) {
  if (component.width == 0 || component.height == 0) {
    LOG.info(component.name + " has zero size, skipping")
    LOG.info(component.javaClass.toString())
    return
  }
  val image = ImageUtil.createImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB)
  val g: Graphics2D = image.createGraphics()
  component.paint(g)
  withContext(Dispatchers.IO) {
    try {
      ImageIO.write(image, "png", file)
    }
    catch (e: IOException) {
      LOG.info(e)
    }
  }
  g.dispose()
}

fun getNextFolder(base: File): File {
  val counter = "%03d".format(screenshotSequence.getAndIncrement())
  val name = "${counter}_${base.name}"
  val folder = File(base.parentFile, name)

  folder.mkdirs()
  return folder
}

@Suppress("SSBasedInspection")
internal fun takeScreenshotOfAllWindowsBlocking(childFolder: String? = null) {
  runBlocking { takeScreenshotOfAllWindows(childFolder) }
}

internal fun takeFullScreenshot(childFolder: String? = null): String? {
  // don't try to take a screenshot when IDE in a headless mode
  if (ApplicationManager.getApplication().isHeadlessEnvironment) return null
  // On Wayland it triggers system dialog about granting permissions each time, and it can't be disabled.
  if (StartupUiUtil.isWayland) return null

  var screenshotPath = LogDirHandler.currentLogDir().resolve("screenshots").resolve(childFolder ?: "default").toFile()
  screenshotPath = getNextFolder(screenshotPath)
  val screenshotPathWithFile = screenshotPath.resolve("full_screen.png")
  val screenCapture = captureScreenWithAwtRobot() ?: return null
  // the screen capture is black when the IDE runs on a desktop that is not on the screen, such as a hidden Windows desktop
  val image = if (isBlack(screenCapture)) paintShowingWindows(screenCapture.width, screenCapture.height) else screenCapture
  writeScreenshot(image, "png", screenshotPathWithFile)
  if (screenshotPathWithFile.exists()) {
    return screenshotPathWithFile.absolutePath
  }
  return null
}

internal suspend fun takeScreenshotOfAllWindows(childFolder: String? = null) {
  // don't try to take a screenshot when IDE in a headless mode
  if (ApplicationManager.getApplication().isHeadlessEnvironment) return

  val projects = ProjectManager.getInstance().openProjects.filterNot { it.isDisposed }
  var screenshotPath = LogDirHandler.currentLogDir().resolve("screenshots").resolve(childFolder ?: "default").toFile()
  screenshotPath = getNextFolder(screenshotPath)

  for (project in projects) {
    try {
      withTimeout(30.seconds) {
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          val prefix = if (projects.size == 1) "" else "${project.name}_"
          for (it in Window.getWindows()) {
            LOG.info("Capturing screenshot of ${it.javaClass}")
            val file = File(screenshotPath, prefix + it.name + ".png")

            captureComponent(it, file)

            LOG.warn("Screenshot saved to:\n" + toLoggedImageLink(file))
          }
        }
      }
    }
    catch (e: TimeoutCancellationException) {
      LOG.info(e)
    }
  }
}

private fun toLoggedImageLink(file: File): String {
  // makes is possible to open image from console output
  if (OS.CURRENT == OS.Windows) {
    return "file:///" + file.absolutePath.replace('\\', '/')
  }
  return "file://" + file.absolutePath
}
