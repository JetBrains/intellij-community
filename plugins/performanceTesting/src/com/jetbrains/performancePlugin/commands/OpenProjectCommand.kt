package com.jetbrains.performancePlugin.commands

import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.util.BitUtil
import com.intellij.util.SystemProperties
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import java.awt.Frame
import java.nio.file.Path

class OpenProjectCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "openProject"

    fun shouldOpenInSmartMode(project: Project): Boolean {
      return (!SystemProperties.getBooleanProperty("performance.execute.script.after.project.opened", false)
              && !LightEdit.owns(project)
              && !SystemProperties.getBooleanProperty("performance.execute.script.after.scanning", false))
    }
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val arguments = text.split("\\s+".toRegex(), limit = 3).toTypedArray()
    val projectToOpen = if (arguments.size > 1) arguments[1] else ""
    val closeProjectBeforeOpening = arguments.size < 3 || arguments[2].toBoolean()
    val project = context.project
    if (projectToOpen.isEmpty() && project.isDefault) {
      throw IllegalArgumentException("Path to project to open required")
    }

    if (!project.isDefault) {
      withContext(Dispatchers.EDT) {
        WindowManager.getInstance().updateDefaultFrameInfoOnProjectClose(project)

        // prevent the script from stopping on project close
        context.setProject(null)

        // for backward compatibility with older code
        if (closeProjectBeforeOpening) {
          ProjectManager.getInstance().closeAndDispose(project)
        }
      }
      RecentProjectsManager.getInstance().updateLastProjectPath()
      WelcomeFrame.showIfNoProjectOpened()
    }
    val projectPath = projectToOpen.ifEmpty { project.basePath!! }
    var newProject = ProjectManagerEx.getOpenProjects().find { it.basePath == projectPath }
    if (newProject != null) {
      val projectFrame = WindowManager.getInstance().getFrame(newProject) ?: return
      val frameState = projectFrame.extendedState
      if (BitUtil.isSet(frameState, Frame.ICONIFIED)) {
        projectFrame.extendedState = BitUtil.set(frameState, Frame.ICONIFIED, false)
      }
      projectFrame.toFront()
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
        val mostRecentFocusOwner = projectFrame.mostRecentFocusOwner
        if (mostRecentFocusOwner != null) {
          IdeFocusManager.getGlobalInstance().requestFocus(mostRecentFocusOwner, true)
        }
      }
    }
    else {
      newProject = ProjectManagerEx.getInstanceEx().openProjectAsync(Path.of(projectPath), OpenProjectTask(forceOpenInNewFrame = true))
                   ?: throw IllegalStateException("Failed to open the project: $projectPath")
      if (shouldOpenInSmartMode(newProject)) {
        val job = CompletableDeferred<Any?>()
        DumbService.getInstance(newProject).smartInvokeLater { job.complete(null) }
        job.join()
      }
    }
    context.setProject(newProject)
  }
}