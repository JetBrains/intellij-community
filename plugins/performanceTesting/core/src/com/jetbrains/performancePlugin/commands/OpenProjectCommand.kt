// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.processPerProjectSupport
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

/**
 * Command to open a new project.
 *
 * To open a project in the current frame closing the current one:
 * Example: %openProject C:\Users\username\intellij
 *
 * To open a project in a new frame and don't close the current one:
 * Example: %openProject C:\Users\username\intellij false
 *
 * If you do the following:
 * %openProject /tmp/a false
 * %openProject /tmp/b false
 * %openProject /tmp/a false
 *
 * In the end, the same project a will be active and there will be 2 window frames.
 */
class OpenProjectCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "openProject"

    private val LOG = logger<OpenProjectCommand>()

    fun shouldOpenInSmartMode(project: Project): Boolean {
      return (!SystemProperties.getBooleanProperty("performance.execute.script.right.after.ide.opened", false)
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
        writeIntentReadAction {
          WindowManager.getInstance().updateDefaultFrameInfoOnProjectClose(project)

          // for backward compatibility with older code
          if (closeProjectBeforeOpening) {
            // prevent the script from stopping on project close
            context.setProject(null)

            ProjectManager.getInstance().closeAndDispose(project)
          }
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
      val projectStoreBaseDir = Path.of(projectPath)
      newProject = ProjectManagerEx.getInstanceEx().openProjectAsync(projectStoreBaseDir, OpenProjectTask(forceOpenInNewFrame = true))
      if (newProject == null) {
        // Don't stop if project was opened in a new instance
        if (!processPerProjectSupport().canBeOpenedInThisProcess(projectStoreBaseDir)) {
          return
        }

        throw IllegalStateException("Failed to open the project: $projectPath")
      }

      if (shouldOpenInSmartMode(newProject)) {
        val job = CompletableDeferred<Any?>()
        DumbService.getInstance(newProject).smartInvokeLater {
          job.complete(null)
        }
        job.join()
      }
    }
    context.setProject(newProject)
  }
}