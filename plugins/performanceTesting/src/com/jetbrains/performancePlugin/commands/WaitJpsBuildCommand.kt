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
  private suspend fun waitJpsProjectLoaded(project: Project, waitTimeout: Int, chronoUnit: ChronoUnit) {
    val jpsLoadingManager = JpsProjectLoadingManager.Companion.getInstance(project)
    if (jpsLoadingManager is JpsProjectLoadingManagerImpl) {
      if (!jpsLoadingManager.isProjectLoaded()) {
        val completableDeferred = CompletableDeferred<Boolean>()
        Disposer.newDisposable("waitJpsProjectLoaded").use { disposable ->
          //disposable will help to make a disconnect on dispose
          //Example - MessageBusTest::disconnectOnDisposeForImmediateDeliveryTopic
          project.messageBus.connect(disposable).subscribe<JpsProjectLoadedListener>(LOADED, object : JpsProjectLoadedListener {
            override fun loaded() {
              completableDeferred.complete(true)
            }
          })

          withTimeoutOrNull(Duration.of(waitTimeout.toLong(), chronoUnit)) {
            return@withTimeoutOrNull completableDeferred.await()
          } ?: throw RuntimeException("Jps project wasn't loaded in $waitTimeout ${chronoUnit.name}")
        }
      }
    }
    else {
      throw RuntimeException("${jpsLoadingManager.javaClass.name} is not supported")
    }
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val (_, timeout, timeunit) = ARGS_PATTERN.find(extractCommandArgument(PREFIX))?.groupValues
                                 ?: throw RuntimeException("Wrong command format\nExample of usage '%waitJpsBuild 4ms'")
    waitJpsProjectLoaded(context.project, timeout.toInt(), POSSIBLE_VALUES[timeunit]
                                                           ?: throw RuntimeException("Following timeunit supported - 'ms','s','m'"))
  }

  override fun getName(): String {
    return NAME
  }

}