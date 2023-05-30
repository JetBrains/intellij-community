package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.ui.ImageUtil
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.awt.AWTException
import java.awt.Robot
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.imageio.ImageIO

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
class TakeScreenshotCommand(text: String, line: Int) : AbstractCommand(text, line) {
  companion object {
    const val PREFIX = CMD_PREFIX + "takeScreenshot"
  }

  public override fun _execute(context: PlaybackContext): Promise<Any> {
    val result = AsyncPromise<Any>()
    val arguments = extractCommandArgument(PREFIX)
    val fullPathToFile = if (!arguments.isEmpty()) arguments
    else Paths.get(PathManager.getLogPath()).resolve("screenshot_before_exit.png").toString()
    ApplicationManager.getApplication().executeOnPooledThread { takeScreenshotOfFrame(fullPathToFile) }
    result.setResult(null)
    return result
  }
}

private fun takeScreenshotWithAwtRobot(fullPathToFile: String) {
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

private fun takeScreenshotOfFrame(fileName: String) {
  val projects = ProjectManager.getInstance().openProjects
  for (project in projects) {
    val result = CompletableFuture<Boolean>()
    ApplicationManager.getApplication().invokeLater {
      val frame = WindowManager.getInstance().getIdeFrame(project)
      if (frame != null) {
        val component = frame.component
        val img = ImageUtil.createImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB)
        component.printAll(img.createGraphics())
        val prefix = if (projects.size == 1) "" else project.name + "_"
        ApplicationManager.getApplication().executeOnPooledThread {
          try {
            result.complete(ImageIO.write(img, "png", File(prefix + fileName)))
          }
          catch (e: IOException) {
            LOG.info(e)
          }
        }
      }
      else {
        LOG.info("Frame was empty when takeScreenshot was called")
      }
    }
    try {
      val fileCreated = result[30, TimeUnit.SECONDS]
      if (fileCreated) {
        LOG.info("Screenshot is saved at: $fileName")
      }
      else {
        LOG.info("No writers are found for screenshot")
      }
    }
    catch (e: InterruptedException) {
      LOG.info(e)
    }
    catch (e: ExecutionException) {
      LOG.info(e)
    }
    catch (e: TimeoutException) {
      LOG.info(e)
    }
  }
}
