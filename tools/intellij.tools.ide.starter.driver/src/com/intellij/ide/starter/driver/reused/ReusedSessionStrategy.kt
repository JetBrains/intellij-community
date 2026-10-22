package com.intellij.ide.starter.driver.reused

import com.intellij.driver.client.Driver
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeRunMode
import com.intellij.ide.starter.testContextCache.CachedTestContext
import com.intellij.ide.starter.testContextCache.TestContextCache
import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Customizes reused IDE session mechanics for [ReusedRun].
 *
 * Most tests use the default and declare only a companion val annotated with [ReusedProject]. Override this when
 * a framework needs its own context caching, run mode, session key, or IDE start and close. A restart closes the run
 * with [closeIde] and starts the replacement with the [BackgroundRun.restarter] that [startIde] set.
 */
interface ReusedSessionStrategy {
  companion object {
    class DefaultReusedSessionStrategy : ReusedSessionStrategy

    fun createReusedSessionStrategy(
      strategyClass: KClass<out ReusedSessionStrategy> = DefaultReusedSessionStrategy::class,
    ): ReusedSessionStrategy {
      return strategyClass.java.getDeclaredConstructor().let { constructor ->
        constructor.isAccessible = true
        constructor.newInstance()
      }
    }
  }

  fun getOrCreateCachedTestContext(context: ExtensionContext, projectConfiguration: CachedProjectConfiguration): CachedTestContext =
    TestContextCache.getOrCreate(projectConfiguration)

  fun currentRunMode(context: ExtensionContext): IdeRunMode = IdeRunMode.current()

  /** The parameter types that the framework injects into a test method. The display name of the test leaves them out. */
  fun injectedParameterTypes(): Set<Class<*>> = setOf(BackgroundRun::class.java, Driver::class.java)

  /**
   * Key for the reused IDE session owned by this invocation.
   *
   * The default names the test class, so each class gets its own IDE. Override when one class must run several
   * sessions, for example one per IDE run mode.
   */
  fun reusedIdeSessionKey(context: ExtensionContext): Any = context.requiredTestClass

  /** The default pauses each Driver call while the IDE shows an indicator, for up to 5 minutes. Recovery runs without the pause. */
  fun startIde(context: ExtensionContext, testContext: IDETestContext, ideRunTimeout: Duration): BackgroundRun =
    testContext.runIdeWithDriver(runTimeout = ideRunTimeout, pauseOnIndicators = 5.minutes)

  @Suppress("UNUSED_PARAMETER")
  fun closeIde(context: ExtensionContext, bgRun: BackgroundRun) {
    bgRun.closeIdeAndWait()
  }

  /**
   * Runs the IDE-side `beforeAll` of [context]'s test class on [bgRun].
   *
   * The session calls it once for each IDE, before the first test on that IDE. Recovery can replace the IDE
   * between two tests, and the replacement gets the call too. The default does nothing, because a Driver IDE has
   * no IDE-side lifecycle.
   */
  fun beforeAllOnIde(context: ExtensionContext, bgRun: BackgroundRun) {}

  /** Runs the IDE-side `beforeEach` of [context]'s test on [bgRun], after the recovery for that test. */
  fun beforeEachOnIde(context: ExtensionContext, bgRun: BackgroundRun) {}

  /** Runs the IDE-side `afterEach` of [context]'s test on [bgRun]. The session calls it only after [beforeEachOnIde] succeeded. */
  fun afterEachOnIde(context: ExtensionContext, bgRun: BackgroundRun) {}

  /**
   * Runs the IDE-side `afterAll` of [context]'s test class on [bgRun]. The session calls it only after [beforeAllOnIde] succeeded.
   *
   * For a `@ClassTemplate` class, JUnit runs `afterAll` once, after all invocations of the template. A framework that
   * stops the session at the end of each invocation therefore never gets this call. A lambda IDE is such a case.
   */
  fun afterAllOnIde(context: ExtensionContext, bgRun: BackgroundRun) {}
}
