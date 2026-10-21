package com.intellij.ide.starter.driver.reused

import com.intellij.driver.sdk.step
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.reused.recovery.IdeStateRecovery
import org.jetbrains.annotations.ApiStatus
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * One reused IDE process, the [IdeStateRecovery] that restores it between tests, and how it is stopped.
 *
 * [ReusedExtensionStore] answers which store holds a session. The companion answers where a session lies in it.
 */
internal class ReusedIdeSession(
  private val strategy: ReusedSessionStrategy,
  private val recovery: IdeStateRecovery,
  initialRun: BackgroundRun,
) : AutoCloseable {
  /** The IDE this session runs on right now. Written only here, so no copy of it can go stale. */
  var currentRun: BackgroundRun = initialRun
    private set

  private var recoveryNeeded = false

  /** The IDE that got the IDE-side `beforeAll` of the class. Recovery can replace [currentRun] with an IDE that did not. */
  private var beforeAllRun: BackgroundRun? = null

  private var beforeEachDelivered = false

  /** Restores what the previous test left. A class with one test has no next test, and [close] runs instead. */
  fun recoverForNextTest() {
    if (!recoveryNeeded) {
      return
    }
    currentRun = step("Recovering IDE state...") { recovery.recoverForNextTest(currentRun) }
    recoveryNeeded = false
  }

  fun markRecoveryNeeded() {
    recoveryNeeded = true
  }

  /** Runs the IDE-side `beforeAll` on [currentRun] when it did not get it yet, and then the IDE-side `beforeEach`. */
  fun beforeTestOnIde(context: ExtensionContext) {
    if (beforeAllRun !== currentRun) {
      strategy.beforeAllOnIde(context, currentRun)
      beforeAllRun = currentRun
    }
    strategy.beforeEachOnIde(context, currentRun)
    beforeEachDelivered = true
  }

  /**
   * Restarts [currentRun] in the middle of a test, and runs the IDE-side `beforeAll` and `beforeEach` on the new IDE.
   *
   * @param context the context of the running test
   * @param close closes the current IDE before the new one starts
   * @return the new IDE, which is now [currentRun]
   */
  fun restartInTest(context: ExtensionContext, close: BackgroundRun.() -> Unit): BackgroundRun {
    currentRun = currentRun.restart(close = close)
    beforeTestOnIde(context)
    return currentRun
  }

  /** Runs the IDE-side `afterEach` on [currentRun] when the IDE-side `beforeEach` of the test succeeded. */
  fun afterTestOnIde(context: ExtensionContext) {
    if (!beforeEachDelivered) {
      return
    }
    beforeEachDelivered = false
    strategy.afterEachOnIde(context, currentRun)
  }

  /** Runs the IDE-side `afterAll` on [currentRun] when an IDE of this session got the IDE-side `beforeAll`. */
  private fun afterAllOnIde(context: ExtensionContext) {
    if (beforeAllRun != null) {
      strategy.afterAllOnIde(context, currentRun)
    }
  }

  override fun close() {
    recovery.closeIde(currentRun)
  }

  companion object {
    /** The session of [context]'s class, or `null` until its first test starts one. */
    fun of(context: ExtensionContext): ReusedIdeSession? = sessions(context)[sessionKey(context)]

    /** The session of [context]'s class, started by [start] when this class is the first to ask for it. */
    fun getOrStart(context: ExtensionContext, start: () -> ReusedIdeSession): ReusedIdeSession =
      sessions(context).getOrPut(sessionKey(context), start)

    /**
     * Takes the session of [context]'s class out of its store and answers with it.
     *
     * Removed and not only closed: a closed session left in the store is a handle to a dead process that the
     * next test of the same key would try to recover state on.
     */
    fun remove(context: ExtensionContext): ReusedIdeSession? = sessions(context).remove(sessionKey(context))

    /**
     * Runs the IDE-side `afterAll` and closes every session of [context]'s test class, and answers with what
     * they threw.
     *
     * The failure is returned because only the caller knows whether the class already holds one that this
     * would replace.
     */
    fun closeSessionsOf(context: ExtensionContext): Throwable? {
      var closeFailure: Throwable? = null
      val sessions = sessions(context)
      sessions.values.forEach { session ->
        runCatching { session.afterAllOnIde(context) }.onFailure { closeFailure = closeFailure.addOrSuppress(it) }
        runCatching { session.close() }.onFailure { closeFailure = closeFailure.addOrSuppress(it) }
      }
      // A class template runs one class twice and both invocations share these sessions, so drop what is closed.
      sessions.clear()
      return closeFailure
    }

    private fun sessionKey(context: ExtensionContext): Any =
      ReusedExtensionStore.sessionStrategy(context).reusedIdeSessionKey(context)

    private fun sessions(context: ExtensionContext): ReusedIdeSessions =
      ReusedExtensionStore.getClassStore(context).computeIfAbsent(SESSIONS_KEY, { ReusedIdeSessions() }, ReusedIdeSessions::class.java)

    private const val SESSIONS_KEY = "reusedIdeSessions"
  }
}

/**
 * The sessions of one test class, by session key.
 *
 * A key is not always the test class: a strategy may run one class against several sessions, one per IDE run mode.
 * The store closes the sessions that are still open when the class ends.
 */
private class ReusedIdeSessions : LinkedHashMap<Any, ReusedIdeSession>(), AutoCloseable {
  override fun close() {
    values.forEach { it.close() }
  }
}

private fun Throwable?.addOrSuppress(failure: Throwable): Throwable =
  this?.also { it.addSuppressed(failure) } ?: failure

/**
 * Ends the reused IDE session of this context's test class before the store that owns it closes.
 *
 * A test framework built on this one calls it, and a test never does.
 *
 * The session is removed, not just closed, so that no handle to a killed process is left in the store.
 */
@ApiStatus.Internal
fun ExtensionContext.stopReusedIde() {
  ReusedIdeSession.remove(this)?.close()
}

/**
 * Restarts the reused IDE of this context's test class in the middle of a test. The new IDE gets the IDE-side
 * `beforeAll` and `beforeEach` of the test, and the session runs on it until the class ends.
 *
 * A test framework built on this one calls it, and a test never does.
 *
 * @param close closes the current IDE before the new one starts. It can also change the files that the new IDE reads.
 * @return the new IDE
 */
@ApiStatus.Internal
fun ExtensionContext.restartReusedIde(close: BackgroundRun.() -> Unit = { closeIdeAndWait() }): BackgroundRun {
  val session = ReusedIdeSession.of(this) ?: error("No reused IDE session is started for ${requiredTestClass.name}")
  return session.restartInTest(this, close)
}
