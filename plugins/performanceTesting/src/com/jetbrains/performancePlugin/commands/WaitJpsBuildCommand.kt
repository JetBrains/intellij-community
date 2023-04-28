package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Ref
import com.intellij.workspaceModel.ide.JpsProjectLoadedListener
import com.intellij.workspaceModel.ide.JpsProjectLoadedListener.Companion.LOADED
import com.intellij.workspaceModel.ide.JpsProjectLoadingManager
import com.intellij.workspaceModel.ide.impl.JpsProjectLoadingManagerImpl
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import kotlinx.coroutines.time.withTimeout
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
Command waits for loading the real state of the project after loading from cache
This command is common for jps, gradle and maven build systems
 */
class WaitJpsBuildCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {

  companion object {
    const val PREFIX = CMD_PREFIX + "waitJpsBuild"
    val ARGS_PATTERN = Regex("([0-9])*([ms,s,m])")
    val POSSIBLE_VALUES = mapOf(
      Pair("ms", ChronoUnit.MILLIS),
      Pair("s", ChronoUnit.SECONDS),
      Pair("m", ChronoUnit.MINUTES)
    )

    @Suppress("TestOnlyProblems")
    suspend fun waitJpsProjectLoaded(project: Project, waitTimeout: Int, chronoUnit: ChronoUnit, actionCallback: ActionCallback) {
      val jpsLoadingManager = JpsProjectLoadingManager.Companion.getInstance(project)
      if(jpsLoadingManager is JpsProjectLoadingManagerImpl) {
        if (!jpsLoadingManager.isProjectLoaded()) {
          val ref: Ref<Boolean> = Ref.create(false)
          withTimeout(Duration.of(waitTimeout.toLong(), chronoUnit)) {
            project.messageBus.connect().subscribe<JpsProjectLoadedListener>(LOADED, object : JpsProjectLoadedListener {
              override fun loaded() {
                ref.set(true)
              }
            })
          }
          if (!ref.get()) {
            actionCallback.reject("Jps project wasn't loaded in $waitTimeout ${chronoUnit.name}")
          }
        }
      }
      else {
        actionCallback.reject("${jpsLoadingManager.javaClass.name} is not supported")
      }
    }
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val (initial, timeout, timeunit) = ARGS_PATTERN.find(extractCommandArgument(PREFIX))!!.groupValues
    waitJpsProjectLoaded(context.project, timeout.toInt(), POSSIBLE_VALUES[timeunit]!!, ActionCallbackProfilerStopper())
  }

}