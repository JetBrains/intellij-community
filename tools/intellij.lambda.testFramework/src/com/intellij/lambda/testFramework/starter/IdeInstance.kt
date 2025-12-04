package com.intellij.lambda.testFramework.starter

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.splitMode
import com.intellij.ide.starter.coroutine.testSuiteSupervisorScope
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.utils.catchAll
import com.intellij.lambda.testFramework.junit.IdeRunMode
import com.intellij.lambda.testFramework.utils.BackgroundRunWithLambda
import com.intellij.lambda.testFramework.utils.IdeLambdaStarter.runIdeWithLambda
import com.intellij.tools.ide.util.common.starterLogger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

private val LOG = starterLogger<IdeInstance>()

object IdeInstance {
  private var _ide: BackgroundRunWithLambda? = null
  val ide: BackgroundRunWithLambda
    get() = _ide ?: throw IllegalStateException("IDE is not started yet")

  lateinit var currentIdeMode: IdeRunMode
    private set
  private lateinit var runContext: IDERunContext

  fun isStarted(): Boolean = _ide != null

  fun startIde(runMode: IdeRunMode): BackgroundRunWithLambda = synchronized(this) {
    try {
      if (isStarted() && currentIdeMode == runMode) {
        LOG.info("IDE is already running in mode: $runMode. Reusing the current instance of IDE.")
        return ide
      }
      else {
        LOG.info("Starting IDE in mode: $runMode")
      }

      stopIde()
      currentIdeMode = runMode
      ConfigurationStorage.splitMode(currentIdeMode == IdeRunMode.SPLIT)

      val testContext = Starter.newContextWithLambda(runMode.name, IdeStartConfig.current)
      _ide = testContext.runIdeWithLambda(configure = {
        IdeStartConfig.current.configureRunContext(this)
        runContext = this
      })
      return ide
    }
    catch (e: Throwable) {
      LOG.error("Problems when starting IDE", e)
      throw e
    }
  }

  fun stopIde(): Unit = synchronized(this) {
    if (!isStarted()) return

    LOG.info("Stopping IDE that is running in mode: $currentIdeMode")
    catchAll { _ide?.forceKill() }
    _ide = null
  }

  fun publishArtifacts(): Unit = synchronized(this) {
    runContext.publishArtifacts()
  }

  fun cleanup() = synchronized(this) {
    @Suppress("RAW_RUN_BLOCKING")
    runBlocking(testSuiteSupervisorScope.coroutineContext) {
      withTimeout(5.seconds) {
        catchAll("IDE instance cleanup") {
          ide.cleanUp()
        }
      }
    }
  }
}