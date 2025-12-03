package com.intellij.lambda.testFramework.starter

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.splitMode
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.utils.catchAll
import com.intellij.lambda.testFramework.junit.IdeRunMode
import com.intellij.lambda.testFramework.utils.BackgroundRunWithLambda
import com.intellij.lambda.testFramework.utils.IdeLambdaStarter.runIdeWithLambda
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput

object IdeInstance {
  internal lateinit var ide: BackgroundRunWithLambda
    private set
  lateinit var currentIdeMode: IdeRunMode
    private set
  private lateinit var runContext: IDERunContext

  /**
   * Returns the current `BackgroundRunWithLambda` if the IDE was started in this process,
   * or `null` otherwise. Useful for JUnit lifecycle callbacks that should tolerate missing IDE.
   */
  fun ideOrNull(): BackgroundRunWithLambda? =
    if (this::ide.isInitialized) ide else null

  fun startIde(runMode: IdeRunMode): BackgroundRunWithLambda = synchronized(this) {
    try {
      logOutput("Starting IDE in mode: $runMode")

      if (this::ide.isInitialized && currentIdeMode == runMode) {
        logOutput("IDE is already running in mode: $runMode. Reusing the current instance of IDE.")
        return ide
      }

      stopIde()
      currentIdeMode = runMode
      ConfigurationStorage.splitMode(currentIdeMode == IdeRunMode.SPLIT)

      val testContext = Starter.newContextWithLambda(runMode.name,
                                                     UltimateTestCases.JpsEmptyProject,
                                                     *LambdaTestPluginHolder.additionalPluginIds().toTypedArray())
      ide = testContext.runIdeWithLambda(configure = { runContext = this })

      return ide
    }
    catch (e: Throwable) {
      logError("Problems when starting IDE", e)
      throw e
    }
  }

  fun stopIde(): Unit = synchronized(this) {
    if (!this::ide.isInitialized) return

    logOutput("Stopping IDE that is running in mode: $currentIdeMode")
    catchAll { ide.forceKill() }
  }

  fun publishArtifacts(): Unit = synchronized(this) {
    runContext.publishArtifacts()
  }
}