package com.intellij.lambda.testFramework.starter

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.splitMode
import com.intellij.ide.starter.ide.isRemDevContext
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.ide.starter.utils.catchAll
import com.intellij.lambda.testFramework.junit.IdeRunMode
import com.intellij.lambda.testFramework.utils.IdeWithLambda
import com.intellij.lambda.testFramework.utils.runIdeWithLambda
import com.intellij.openapi.diagnostic.logger
import com.intellij.tools.ide.starter.bus.EventsBus

data class RunContext(var frontendContext: IDERunContext, var backendContext: IDERunContext? = null)

object IdeInstance {

  private val LOG by lazy { logger<IdeInstance>() }

  private var _ide: IdeWithLambda? = null
  val ide: IdeWithLambda
    get() = _ide ?: throw IllegalStateException("IDE is not started yet")

  lateinit var currentIdeMode: IdeRunMode
    private set

  private lateinit var currentIdeConfig: IdeStartConfig
  lateinit var runContext: RunContext
    private set

  fun isStarted(): Boolean = _ide != null

  fun startIde(runMode: IdeRunMode): IdeWithLambda = synchronized(this) {
    // Allow IDE building to access test modules
    System.setProperty("idea.build.pack.test.source.enabled", "true")

    try {
      if (isStarted() && currentIdeMode == runMode && IdeStartConfig.current == currentIdeConfig) {
        LOG.info("IDE is already running in mode: $runMode and there were no requests to change it's config. Reusing the current instance of IDE.")
        return ide
      }
      else {
        LOG.info("Starting IDE in mode: $runMode")
      }

      stopIde()
      currentIdeMode = runMode
      currentIdeConfig = IdeStartConfig.current
      ConfigurationStorage.splitMode(currentIdeMode == IdeRunMode.SPLIT)

      EventsBus.subscribe(IdeInstance) { event: IdeLaunchEvent ->
        if (event.runContext.testContext.isRemDevContext()) {
          LOG.info("$runMode mode run context hash ${event.runContext.hashCode()} object ${event.runContext}")

          if (this::runContext.isInitialized) {
            runContext = runContext.copy(backendContext = event.runContext)
          }
          else {
            runContext = RunContext(backendContext = event.runContext, frontendContext = event.runContext)
          }
        }
        else {
          val frontendName = if (runMode == IdeRunMode.SPLIT) "Frontend" else "Monolith"
          LOG.info("$frontendName run context hash ${event.runContext.hashCode()} object ${event.runContext}")

          if (this::runContext.isInitialized) {
            runContext = runContext.copy(frontendContext = event.runContext)
          }
          else {
            runContext = RunContext(frontendContext = event.runContext)
          }
        }
      }

      val testContext = Starter.newContextWithLambda(runMode.name, IdeStartConfig.current)
      _ide = testContext.runIdeWithLambda(configure = {
        IdeStartConfig.current.configureRunContext(this)
        // Artifacts will be published after each test by invoking IdeInstance.publishArtifacts
        this.artifactsPublishingEnabled = false
      })

      return ide
    }
    catch (e: Throwable) {
      LOG.error("Problems when starting IDE", e)
      throw e
    }
  }

  fun stopIde(): Unit = synchronized(this) {
    if (isStarted()) {
      LOG.info("Killing IDE with current ide mode: $currentIdeMode")
      catchAll { _ide?.forceKill() }
      _ide = null
    }
    else {
      LOG.info("IDE wasn't started. Skipping killing it.")
    }
  }

  fun publishArtifacts(): Unit = synchronized(this) {
    runContext.frontendContext.publishArtifacts(publish = true)
    runContext.backendContext?.publishArtifacts(publish = true)
  }
}