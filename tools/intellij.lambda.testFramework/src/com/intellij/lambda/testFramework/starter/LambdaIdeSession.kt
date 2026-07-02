package com.intellij.lambda.testFramework.starter

import com.intellij.ide.starter.ide.IdeRunMode
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.reused.ReusedExtensionStore
import com.intellij.ide.starter.driver.reused.stopReusedIde
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import com.intellij.ide.starter.testContextCache.TestContextCache
import com.intellij.ide.starter.testContextCache.CachedTestContext
import com.intellij.ide.starter.runner.Starter
import com.intellij.lambda.testFramework.junit.getCurrentRunMode
import com.intellij.lambda.testFramework.utils.IdeWithLambda
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder
import com.intellij.lambda.testFramework.utils.runIdeWithLambda
import com.intellij.openapi.diagnostic.logger
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.time.Duration

private val lambdaIdeSessionLog = logger<LambdaIdeSession>()

internal object LambdaIdeSession {
  fun getOrCreateCachedTestContext(
    context: ExtensionContext,
    projectConfiguration: CachedProjectConfiguration,
  ): CachedTestContext {
    val mode = getCurrentRunMode(context)
    System.setProperty("idea.build.pack.test.source.enabled", "true")

    val setup = lambdaTestSetup(context)
    lambdaIdeSessionLog.info(
      "Creating Lambda IDE context in mode: $mode for class ${context.requiredTestClass.name}"
    )
    return withAppliedRunMode(mode) {
      TestContextCache.getOrCreate(
        projectConfiguration,
        setup,
      ) { contextName, configuration ->
        Starter.newContextWithLambda(
          contextName,
          setup,
          configuration,
          ideDataPathsProvider = TestContextCache.cachedContextDataPathsProvider(),
        )
      }
    }
  }

  /** Starts this session's IDE on [testContext]. [BackgroundRun.restart] of the run starts the next IDE of the session. */
  fun startIde(
    context: ExtensionContext,
    testContext: IDETestContext,
    ideRunTimeout: Duration,
    launchName: String = "",
  ): BackgroundRun {
    val mode = getCurrentRunMode(context)
    val setup = lambdaTestSetup(context)
    val ide = withAppliedRunMode(mode) {
      runCatching {
        testContext.runIdeWithLambda(setup, runTimeout = ideRunTimeout, launchName = launchName)
      }.onFailure {
        lambdaIdeSessionLog.warn(it)
        ideStore(context).remove(ideKey(context))
      }.getOrThrow()
    }
    ideStore(context).put(ideKey(context), ide)
    ide.backgroundRun.restarter = { restartLaunchName -> startIde(context, testContext, ideRunTimeout, restartLaunchName) }
    return ide.backgroundRun
  }

  fun current(context: ExtensionContext): IdeWithLambda {
    return currentOrNull(context) ?: error("IDE is not started yet")
  }

  /** The IDE of this context's session, or null when nothing has started one yet. */
  fun currentOrNull(context: ExtensionContext): IdeWithLambda? {
    return ideStore(context).get(ideKey(context), IdeWithLambda::class.java)
  }

  /**
   * Stops this context's IDE now, rather than when the store that owns it closes.
   *
   * This object only forgets its RD handle. The reusable IDE session owns the process, so the strategy stops it once.
   */
  fun stop(context: ExtensionContext) {
    ideStore(context).remove(ideKey(context))
    context.stopReusedIde()
  }

  /**
   * The plugin set this context's IDE is built with, as its strategy declares it.
   *
   * Asked of the strategy rather than of the test class directly, because a product with generated test classes
   * has no entry in [LambdaTestPluginHolder.setupFor] and declares its own — and because a setup that reached
   * the launch through process-wide state would be readable by the next host in this JVM as well.
   */
  private fun lambdaTestSetup(context: ExtensionContext): LambdaTestPluginHolder.LambdaTestSetup {
    val strategy = ReusedExtensionStore.sessionStrategy(context)
    return (strategy as? LambdaReusedSessionStrategy)?.lambdaTestSetup(context)
           ?: error(
             "${context.requiredTestClass.name} runs a lambda IDE, so its @ReusedRun strategy has to be a " +
             "${LambdaReusedSessionStrategy::class.simpleName}, not ${strategy::class.java.name}"
           )
  }

  private fun ideStore(context: ExtensionContext): ExtensionContext.Store = ReusedExtensionStore.getClassStore(context)

  /** The key of this context's lambda IDE. It holds the session key of the strategy, so the IDE and its session always agree. */
  private fun ideKey(context: ExtensionContext): Any =
    IDE_KEY to ReusedExtensionStore.sessionStrategy(context).reusedIdeSessionKey(context)

  private fun <T> withAppliedRunMode(runMode: IdeRunMode, action: () -> T): T {
    val previousRunMode = IdeRunMode.current()
    runMode.applyToConfiguration()
    return try {
      action()
    }
    finally {
      previousRunMode.applyToConfiguration()
    }
  }

  private const val IDE_KEY = "lambdaIde"
}
