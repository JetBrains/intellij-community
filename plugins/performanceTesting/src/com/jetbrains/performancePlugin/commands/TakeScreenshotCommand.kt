package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.util.ui.ImageUtil
import kotlinx.coroutines.*
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.seconds

private val LOG: Logger
  get() = logger<TakeScreenshotCommand>()

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

fun takeScreenshotWithAwtRobot(fullPathToFile: String) {
  val rectangle = Rectangle(Toolkit.getDefaultToolkit().screenSize)
  var robot: Robot? = null
  try {
    robot = Robot()
  }
  catch (e: AWTException) {
    LOG.info("Exceptions occurs at attempt to create Robot for taking screenshot")
    LOG.info(e)
  }
  assert(robot != null)
  val img = robot!!.createScreenCapture(rectangle)
  try {
    val screenshotFile = File(fullPathToFile)
    ImageIO.write(img, "jpg", screenshotFile)
    if (screenshotFile.exists()) {
      LOG.info("Screenshot saved:$fullPathToFile")
    }
  }
  catch (e: IOException) {
    LOG.info("Exceptions occurs at attempt to write screenshot to file")
    LOG.info(e)
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
  var counter = 0
  var folder = base

  while (folder.exists()) {
    counter++
    val name = "${base.name}_$counter"
    folder = File(base.parentFile, name)
  }

  folder.mkdirs()
  return folder
}

internal suspend fun takeScreenshotOfAllWindows(childFolder: String? = null) {
  val projects = ProjectManager.getInstance().openProjects
  var screenshotPath = File(PathManager.getLogPath() + "/screenshots/" + (childFolder ?: "default"))
  screenshotPath = getNextFolder(screenshotPath)
  for (project in projects) {
    try {
      withTimeout(30.seconds) {
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          val prefix = if (projects.size == 1) "" else "${project.name}_"
          Window.getWindows().forEach {
            LOG.info("Capturing screenshot of ${it.javaClass}")
            captureComponent(it, File(screenshotPath, prefix + it.name + ".png"))
          }
        }
      }
    }
    catch (e: TimeoutCancellationException) {
      LOG.info(e)
    }
  }
}
