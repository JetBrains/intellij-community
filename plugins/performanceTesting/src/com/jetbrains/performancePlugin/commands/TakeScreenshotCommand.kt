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
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.ui.ImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.awt.AWTException
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.seconds

private val LOG: Logger
  get() = logger<TakeScreenshotCommand>()

/**
 * Command takes screenshot.
 * Takes JPG screenshot of current screen to specified file
 * (or if empty parameter is specified, the file will be stored under the LOG dir).
 *
 *
 * Syntax: %takeScreenshot <fullPathToFile>
 * Example: %takeScreenshot ./myScreenshot.jpg
</fullPathToFile> */
class TakeScreenshotCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "takeScreenshot"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    takeScreenshotOfFrame(extractCommandArgument(PREFIX).ifEmpty {
      Path.of(PathManager.getLogPath()).resolve("screenshot_before_exit.png").toString()
    })
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

internal suspend fun takeScreenshotOfFrame(fileName: String) {
  val projects = ProjectManager.getInstance().openProjects
  for (project in projects) {
    try {
      withTimeout(30.seconds) {
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          val frame = WindowManager.getInstance().getIdeFrame(project)
          if (frame == null) {
            LOG.info("Frame was empty when takeScreenshot was called")
          }
          else {
            val component = frame.component
            val img = ImageUtil.createImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB)
            component.printAll(img.createGraphics())
            val prefix = if (projects.size == 1) "" else project.name + "_"
            withContext(Dispatchers.IO) {
              try {
                ImageIO.write(img, "png", File(prefix + fileName))
                LOG.info("Screenshot is saved at: $fileName")
              }
              catch (e: IOException) {
                LOG.info(e)
              }
            }
          }
        }
      }
    }
    catch (e: TimeoutCancellationException) {
      LOG.info(e)
    }
  }
}
