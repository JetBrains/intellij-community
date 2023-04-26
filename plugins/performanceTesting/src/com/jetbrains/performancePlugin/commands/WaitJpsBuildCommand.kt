package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.ActionCallback
import com.intellij.workspaceModel.ide.JpsProjectLoadedListener
import com.intellij.workspaceModel.ide.JpsProjectLoadedListener.Companion.LOADED
import com.intellij.workspaceModel.ide.JpsProjectLoadingManager.Companion.getInstance
import com.intellij.workspaceModel.ide.impl.JpsProjectLoadingManagerImpl
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
Command waits for loading the real state of the project after loading from cache
This command is common for jps, gradle and maven build systems
 */
class WaitJpsBuildCommand(text: String, line: Int) : AbstractCommand(text, line) {

  companion object {
    const val PREFIX = CMD_PREFIX + "waitJpsBuild"

    fun waitJpsProjectLoaded(project: Project, waitTimeout: Int, timeUnit: TimeUnit, actionCallback: ActionCallback) {
      val jpsLoadingManager = getInstance(project)
      if (jpsLoadingManager is JpsProjectLoadingManagerImpl &&
          !jpsLoadingManager.isProjectLoaded()) {
        val isLoaded = CountDownLatch(1)
        project.getMessageBus().connect().subscribe<JpsProjectLoadedListener>(LOADED, object : JpsProjectLoadedListener {
          override fun loaded() {
            isLoaded.countDown()
          }
        })
        try {
          if (!isLoaded.await(waitTimeout.toLong(), timeUnit)) {
            actionCallback.reject("Jps project wasn't loaded in $waitTimeout ${timeUnit.name}")
          }
        }
        catch (e: Throwable) {
          actionCallback.reject("While execution of 'waitJpsProjectLoaded' exception occurred ${e.message}")
        }
      }
      else {
        actionCallback.reject("${jpsLoadingManager.javaClass.name} is not supported")
      }
    }
  }

  override fun _execute(context: PlaybackContext): Promise<Any> {
    val (timeout, timeunit) = extractCommandList(PREFIX, ",")
    waitJpsProjectLoaded(context.project, timeout.toInt(), TimeUnit.valueOf(timeunit.uppercase()), ActionCallbackProfilerStopper())
    return resolvedPromise()
  }

}