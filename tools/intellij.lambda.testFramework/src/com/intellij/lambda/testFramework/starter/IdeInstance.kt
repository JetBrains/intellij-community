package com.intellij.lambda.testFramework.starter

import com.intellij.ide.starter.ide.IdeRunMode
import com.intellij.ide.starter.ide.isRemDevContext
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.ide.starter.utils.catchAll
import com.intellij.lambda.testFramework.utils.IdeWithLambda
import com.intellij.lambda.testFramework.utils.runIdeWithLambda
import com.intellij.openapi.diagnostic.logger
import com.intellij.tools.ide.starter.bus.EventsBus

data class RunContext(var frontendContext: IDERunContext, var backendContext: IDERunContext? = null)

object IdeInstance {

  private val LOG by lazy { logger<IdeInstance>() }

  private var _ide: IdeWithLambda? = null

  /**
   * Relaunches this JVM has performed. Never reset, including across contexts: its only job is to make every
   * launch name unique, and a name reused is a previous run's report and log overwritten.
   */
  private var recycles: Int = 0

  val ide: IdeWithLambda
    get() = _ide ?: throw IllegalStateException("IDE is not started yet")

  lateinit var currentIdeMode: IdeRunMode
    private set

  private lateinit var currentIdeConfig: IdeStartConfig
  lateinit var runContext: RunContext
    private set

  fun isStarted(): Boolean = _ide != null

  /**
   * Whether [recycleIde] has a context to relaunch, whether or not an IDE is currently alive on it.
   *
   * A separate question from [isStarted], and the difference is the whole recovery story of a suite set that
   * shares one IDE. A relaunch that fails leaves no IDE behind ([_ide] is cleared before the launch), and the
   * caller then has to choose between relaunching the surviving context again and asking [startIde] for a
   * fresh one. Those are not equivalent: a fresh context is built by `Starter.newContextWithLambda`, which
   * wipes the config, system and project directories the sharing suites chain state through, and re-resolves
   * the installation — which for a prebuilt distribution can fail outright, turning one lost IDE into a failed
   * start for every suite that comes after it.
   */
  fun canRecycle(): Boolean =
    this::currentIdeMode.isInitialized && this::currentIdeConfig.isInitialized && this::runContext.isInitialized

  fun startIde(runMode: IdeRunMode): IdeWithLambda = synchronized(this) {
    // Allow IDE building to access test modules
    System.setProperty("idea.build.pack.test.source.enabled", "true")

    try {
      if (isStarted() && currentIdeMode == runMode && IdeStartConfig.current == currentIdeConfig) {
        LOG.info("IDE is already running in mode: $runMode with config '${currentIdeConfig.key}'. Reusing the current instance of IDE.")
        return ide
      }
      else {
        LOG.info("Starting IDE in mode: $runMode (${restartReason(runMode)})")
      }

      stopIde()
      currentIdeMode = runMode
      currentIdeConfig = IdeStartConfig.current
      currentIdeMode.applyToConfiguration()

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
      })

      return ide
    }
    catch (e: Throwable) {
      LOG.error("Problems when starting IDE", e)
      throw e
    }
  }

  /**
   * Why the running IDE could not be answered with. Every start after the first one costs a suite a cold
   * launch and a re-index, so a run that expected to reuse and did not has to be able to say what changed.
   */
  private fun restartReason(runMode: IdeRunMode): String = when {
    !isStarted() -> "no IDE is running"
    currentIdeMode != runMode -> "run mode changed from $currentIdeMode"
    else -> "config key changed from '${currentIdeConfig.key}' to '${IdeStartConfig.current.key}'"
  }

  /**
   * Quits the running IDE and starts it again **on the same test context**, for a caller that needs a clean IDE
   * rather than a fresh installation.
   *
   * This is not [startIde] with the same config: that one builds a new context through
   * `Starter.newContextWithLambda`, which calls `newContext(..., preserveSystemDir = false)` and so wipes the
   * config, system and project directories. Relaunching the context we already have keeps all three, which is
   * what a caller that only wants to shed in-memory state is asking for, and what keeps a suite that
   * deliberately chains install state across its tests working.
   *
   * Two things make the relaunch go through [runIdeWithLambda] rather than through the run itself:
   *
   * - the RD lambda session cannot be reused. Its protocol lifetime is terminated by the `IdeAfterLaunchEvent`
   *   of this very context and the subscription is one-shot (`IdeLambdaStarter.setUpRdTestSession`), so the
   *   session that served the previous run is dead and only a new `runIdeWithLambda` creates its replacement.
   * - re-patching the same context's VM options is safe. `VMOptions.addSystemProperty` replaces an existing
   *   `-Dkey=` line rather than appending a second one, so the new `LAMBDA_TESTING_PORT` supersedes the old
   *   one instead of leaving the IDE to pick whichever it reads first.
   *
   * Each relaunch gets its own launch name, because the launch name is what separates the runs in the report:
   * it picks the reporting directory and `-Didea.log.path`, so two runs sharing one name means the recycled IDE
   * overwrites the `idea.log` of the run whose leftovers caused the recycle — the one log a reader needs.
   *
   * [betweenRuns] runs after the old IDE is gone and before the new one starts — the window in which a caller
   * can reap helper processes the dying IDE leaked, which would otherwise fail the launch that follows.
   *
   * [_ide] is reassigned rather than left alone because `IdeWithLambdaParameterResolver` hands
   * [IdeInstance.ide] to the next test class: a recycle that did not publish its replacement would give that
   * class a handle to the IDE this one killed. A relaunch that *fails* leaves it null, and that is a state
   * this call can be made from again: the precondition is [canRecycle], not [isStarted]. It used to be
   * [isStarted], on the reading that the next [startIde] would build a fresh context instead — which is the
   * one remedy that is not equivalent, because a fresh context wipes the directories a sharing suite set
   * chains state through and re-resolves the installation. Measured on the AIR UI lane: one relaunch whose
   * replacement never answered its RD session left 19 later suites reporting a failed cold start, each
   * against its own innocent subject, while relaunching this context again was still possible.
   *
   * One honesty note about [runContext]: it is only ever assigned from the `IdeLaunchEvent` subscription
   * [startIde] registers, and `EventsBus.unsubscribeAll()` between tests can leave the relaunch's event with no
   * subscriber — so after a recycle [runContext] may still describe the previous run. Only `testContext` is read
   * from it here, and that object is the same across runs of one context, which is why it is safe; nothing else
   * should read [runContext] expecting it to track a relaunch.
   */
  fun recycleIde(betweenRuns: () -> Unit = {}): IdeWithLambda = synchronized(this) {
    check(canRecycle()) { "no IDE has been launched in this JVM; there is no context to relaunch" }
    val context = when (currentIdeMode) {
      IdeRunMode.MONOLITH -> runContext.frontendContext.testContext
      // A split run has a frontend and a backend context, and relaunching one of them leaves the other pointing
      // at an IDE that no longer exists. Guessing at that pair is worse than saying it is not implemented.
      else -> error("recycling a $currentIdeMode IDE is not implemented; only ${IdeRunMode.MONOLITH} is supported")
    }

    recycles++
    LOG.info("Recycling IDE (#$recycles) in mode $currentIdeMode on the existing test context")
    catchAll { _ide?.backgroundRun?.closeIdeAndWait() }
    _ide = null
    betweenRuns()
    try {
      // Configured by the config that defines this instance's identity, not by whatever `current` now holds:
      // `startIde` decides reuse by comparing against `currentIdeConfig`, so a relaunch configured from
      // anything else would produce an IDE that no longer matches the key it is reused under.
      _ide = context.runIdeWithLambda(
        launchName = "recycle-$recycles",
        configure = { currentIdeConfig.configureRunContext(this) },
      )
    }
    catch (e: Throwable) {
      LOG.error("Problems when recycling IDE", e)
      throw e
    }
    return ide
  }

  fun stopIde(): Unit = synchronized(this) {
    if (isStarted()) {
      LOG.info("Killing IDE with current ide mode: $currentIdeMode")
      if (currentIdeConfig.forceKill) {
        catchAll { _ide?.backgroundRun?.forceKill() }
      } else {
        catchAll { _ide?.backgroundRun?.closeIdeAndWait() }
      }
      _ide = null
    }
    else {
      LOG.info("IDE wasn't started. Skipping killing it.")
    }
  }
}