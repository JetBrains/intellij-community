package com.intellij.lambda.testFramework.junit

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.splitMode
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.utils.catchAll
import com.intellij.lambda.testFramework.starter.newContextWithLambda
import com.intellij.lambda.testFramework.utils.BackgroundRunWithLambda
import com.intellij.lambda.testFramework.utils.IdeLambdaStarter.runIdeWithLambda
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput

object IdeInstance {
  internal lateinit var ideBackgroundRun: BackgroundRunWithLambda
    private set
  lateinit var currentIdeMode: IdeRunMode
    private set
  private lateinit var runContext: IDERunContext

  fun startIde(runMode: IdeRunMode): BackgroundRunWithLambda = synchronized(this) {
    try {
      logOutput("Starting IDE in mode: $runMode")

      if (this::ideBackgroundRun.isInitialized && currentIdeMode == runMode) {
        logOutput("IDE is already running in mode: $runMode. Reusing the current instance of IDE.")
        return ideBackgroundRun
      }

      stopIde()
      currentIdeMode = runMode
      ConfigurationStorage.splitMode(currentIdeMode == IdeRunMode.SPLIT)

      val testContext = Starter.newContextWithLambda(runMode.name,
                                                     UltimateTestCases.JpsEmptyProject,
                                                     *LambdaTestPluginHolder.additionalPluginIds().toTypedArray())
      ideBackgroundRun = testContext.runIdeWithLambda(configure = { runContext = this })

      return ideBackgroundRun
    }
    catch (e: Throwable) {
      logError("Problems when starting IDE", e)
      throw e
    }
  }

  fun stopIde(): Unit = synchronized(this) {
    if (!this::ideBackgroundRun.isInitialized) return

    logOutput("Stopping IDE that is running in mode: $currentIdeMode")
    catchAll { ideBackgroundRun.forceKill() }
  }

  fun publishArtifacts(): Unit = synchronized(this) {
    runContext.publishArtifacts()
  }
}