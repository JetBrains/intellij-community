package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.workspaceModel.ide.JpsProjectLoadedListener
import com.intellij.workspaceModel.ide.JpsProjectLoadedListener.Companion.LOADED
import com.intellij.workspaceModel.ide.JpsProjectLoadingManager
import com.intellij.workspaceModel.ide.impl.JpsProjectLoadingManagerImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.time.withTimeoutOrNull
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * Command waits for loading the real state of the project after loading from cache
 * This command is common for jps, gradle and maven build systems
 * Example: '%waitJpsBuild 4ms' - millis
 * Example: %waitJpsBuild 4s    - seconds
 * Example: %waitJpsBuild 4m    - minutes
 */
class WaitJpsBuildCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME = "waitJpsBuild"
    const val PREFIX = CMD_PREFIX + NAME
    val ARGS_PATTERN = Regex("^([0-9]*)(ms|s|m)\$")
    val POSSIBLE_VALUES = mapOf(
      Pair("ms", ChronoUnit.MILLIS),
      Pair("s", ChronoUnit.SECONDS),
      Pair("m", ChronoUnit.MINUTES)
    )
  }

  @Suppress("TestOnlyProblems")
  suspend fun waitJpsProjectLoaded(project: Project, waitTimeout: Int, chronoUnit: ChronoUnit) {
    val jpsLoadingManager = JpsProjectLoadingManager.Companion.getInstance(project)
    if (jpsLoadingManager is JpsProjectLoadingManagerImpl) {
      if (!jpsLoadingManager.isProjectLoaded()) {
        val completableDeferred = CompletableDeferred<Boolean>()
        Disposer.newDisposable().use { disposable ->
          //disposable will help to make a disconnect on dispose
          //Example - MessageBusTest::
          project.messageBus.connect(disposable).subscribe<JpsProjectLoadedListener>(LOADED, object : JpsProjectLoadedListener {
            override fun loaded() {
              completableDeferred.complete(true)
            }
          })
        }

        withTimeoutOrNull(Duration.of(waitTimeout.toLong(), chronoUnit)) {
          return@withTimeoutOrNull completableDeferred.await()
        } ?: throw RuntimeException("Jps project wasn't loaded in $waitTimeout ${chronoUnit.name}")
      }
    }
    else {
      throw RuntimeException("${jpsLoadingManager.javaClass.name} is not supported")
    }
  }

  override suspend fun doExecute(context: PlaybackContext) {
    //firstGroup - don't delete, due to it collected as first arg after regExp processing
    val (firstGroup, timeout, timeunit) = ARGS_PATTERN.find(extractCommandArgument(PREFIX))!!.groupValues
    waitJpsProjectLoaded(context.project, timeout.toInt(), POSSIBLE_VALUES[timeunit]!!)
  }

  override fun getName(): String {
    return NAME
  }

}