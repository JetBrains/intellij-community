package com.intellij.ide.starter.driver.reused

import com.intellij.driver.client.Driver
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.reused.recovery.IdeStateRecovery
import com.intellij.ide.starter.driver.reused.recovery.ReusedSessionBaseline
import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import com.intellij.ide.starter.testContextCache.utils.testContextDirName
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.InvocationInterceptor.Invocation
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolutionException
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import java.lang.reflect.Method

/**
 * JUnit 5 extension behind [ReusedRun].
 *
 * It starts and closes the reused IDE, restores state between tests, injects [Driver], and runs every lifecycle
 * method and test body in the active Driver context.
 */
class ReusedExtension : BeforeAllCallback,
                        BeforeEachCallback,
                        AfterEachCallback,
                        AfterAllCallback,
                        ParameterResolver,
                        InvocationInterceptor {
  override fun beforeAll(extensionContext: ExtensionContext) {
    forbidNestedClass(extensionContext)
    forbidOrderedExecution(extensionContext)
    // Resolved here for their failures: a class that declares no project, or a strategy that cannot be built,
    // reads as an error of the class rather than of its first test.
    ReusedExtensionStore.projectConfiguration(extensionContext)
    ReusedExtensionStore.sessionStrategy(extensionContext)
  }

  override fun beforeEach(extensionContext: ExtensionContext) {
    val session = ReusedIdeSession.getOrStart(extensionContext) { startReusedIdeSession(extensionContext) }
    session.recoverForNextTest()
    session.beforeTestOnIde(extensionContext)
  }

  override fun afterEach(extensionContext: ExtensionContext) {
    val session = ReusedIdeSession.of(extensionContext) ?: return
    try {
      session.afterTestOnIde(extensionContext)
    }
    finally {
      session.markRecoveryNeeded()
    }
  }

  override fun afterAll(extensionContext: ExtensionContext) {
    val closeFailure = ReusedIdeSession.closeSessionsOf(extensionContext)
    if (closeFailure != null && !extensionContext.executionException.isPresent) {
      throw closeFailure
    }
  }

  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
    val parameterType = parameterContext.parameter.type
    if (!isReusedIdeParameter(parameterType, extensionContext.requiredTestClass)) {
      return false
    }
    if (parameterContext.isBeforeAllMethodParameter()) {
      throw ParameterResolutionException(
        "@BeforeAll methods in @${ReusedRun::class.simpleName} tests cannot declare ${parameterType.simpleName} parameters. " +
        "Move reused project setup to ${CachedProjectConfiguration::class.simpleName}.setup or live IDE setup to @BeforeEach because the IDE may be restarted between tests.",
      )
    }
    return true
  }

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
    val bgRun = ReusedIdeSession.of(extensionContext)?.currentRun
                ?: error("IDE is not started for ${extensionContext.displayName}")
    return when (parameterContext.parameter.type) {
      Driver::class.java -> bgRun.driver
      // Self-tests only, see isTestFrameworkTestPackage. It is not test-facing API: it exposes the process id and liveness.
      BackgroundRun::class.java -> bgRun
      else -> error("Unsupported parameter type ${parameterContext.parameter.type.name}")
    }
  }

  // Every lifecycle method and every test body of a @ReusedRun class runs in the Driver context of its session.
  override fun interceptBeforeEachMethod(
    invocation: Invocation<Void?>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext
  ): Unit = invokeInDriverContext(invocation, extensionContext)

  override fun interceptTestMethod(
    invocation: Invocation<Void?>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext
  ): Unit = invokeInDriverContext(invocation, extensionContext)

  override fun interceptTestTemplateMethod(
    invocation: Invocation<Void?>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext
  ): Unit = invokeInDriverContext(invocation, extensionContext)

  override fun interceptAfterEachMethod(
    invocation: Invocation<Void?>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext
  ): Unit = invokeInDriverContext(invocation, extensionContext)

  override fun interceptAfterAllMethod(
    invocation: Invocation<Void?>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext
  ): Unit = invokeInDriverContext(invocation, extensionContext)

  private fun invokeInDriverContext(invocation: Invocation<Void?>, extensionContext: ExtensionContext) {
    val session = ReusedIdeSession.of(extensionContext)
    if (session == null) {
      invocation.proceed()
      return
    }
    session.currentRun.driver.withContext { invocation.proceed() }
  }

  /** Starts the IDE of [extensionContext]'s session. A start that fails after the process came up closes that process. */
  private fun startReusedIdeSession(extensionContext: ExtensionContext): ReusedIdeSession {
    val strategy = ReusedExtensionStore.sessionStrategy(extensionContext)
    val cachedTestContext = strategy.getOrCreateCachedTestContext(extensionContext, ReusedExtensionStore.projectConfiguration(extensionContext))
    val contextDirName = testContextDirName(strategy.currentRunMode(extensionContext), extensionContext.requiredTestClass.simpleName)
    val recovery = IdeStateRecovery(
      strategy = strategy,
      extensionContext = extensionContext,
      testContext = cachedTestContext.createCurrentTestContext(contextDirName).applyVMOptionsPatch {
        // Recovery waits until the IDE loads the project and global model files, which by default starts 5 s late.
        addSystemProperty("ide.background.post.startup.activity.delay", 0)
        addSystemProperty("ide.global.workspace.model.delayed.load.delay", 0)
      },
      runTimeout = ideRunTimeout(extensionContext.requiredTestClass),
      baseline = ReusedSessionBaseline.of(cachedTestContext),
    )
    return ReusedIdeSession(ReusedExtensionStore.sessionStrategy(extensionContext), recovery, recovery.startIde())
  }

}
