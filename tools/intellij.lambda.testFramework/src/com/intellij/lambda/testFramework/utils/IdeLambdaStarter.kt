package com.intellij.lambda.testFramework.utils

import com.intellij.ide.starter.driver.driver.remoteDev.RemoteDevBackgroundRun
import com.intellij.ide.starter.driver.engine.LocalDriverRunner
import com.intellij.ide.starter.driver.engine.selectedDriverRunner
import com.intellij.ide.starter.ide.IDERemDevTestContext
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.onRemDevContext
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.events.IdeAfterLaunchEvent
import com.intellij.ide.starter.utils.catchAll
import com.intellij.lambda.testFramework.testApi.waitForProject
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder.LoadingInSplitMode.All
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder.LoadingInSplitMode.OnlyFrontend
import com.intellij.openapi.application.ApplicationManager
import com.intellij.remoteDev.tests.LambdaTestsConstants
import com.intellij.remoteDev.tests.impl.LambdaTestHost.Companion.TEST_MODULE_ID_PROPERTY_NAME
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdIdeType
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdIdeType.BACKEND
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdIdeType.FRONTEND
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdIdeType.MONOLITH
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestSession
import com.intellij.remoteDev.tests.modelGenerated.lambdaTestModel
import com.intellij.remoteDev.util.executeSyncNonNullable
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.util.io.copyRecursively
import com.intellij.util.io.createDirectories
import com.jetbrains.rd.framework.IdKind
import com.jetbrains.rd.framework.Identities
import com.jetbrains.rd.framework.Protocol
import com.jetbrains.rd.framework.Serializers
import com.jetbrains.rd.framework.SocketWire
import com.jetbrains.rd.util.lifetime.EternalLifetime
import com.jetbrains.rd.util.threading.SynchronousScheduler
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.exists
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal fun IDETestContext.runIdeWithLambda(
  runTimeout: Duration = 10.minutes,
  launchName: String = "",
  expectedKill: Boolean = false,
  expectedExitCode: Int = 0,
  collectNativeThreads: Boolean = false,
  configure: IDERunContext.() -> Unit = {},
): IdeWithLambda {
  onRemDevContext {
    return@runIdeWithLambda runIdeWithLambda(runTimeout, launchName, expectedKill, expectedExitCode, collectNativeThreads, configure)
  }

  val driverRunner = LocalDriverRunner()
  val monolithRdSession = setUpLambdaTestSession(MONOLITH)
  val backgroundRun = driverRunner.runIdeWithDriver(this,
                                                    determineDefaultCommandLineArguments(),
                                                    emptyList(),
                                                    runTimeout,
                                                    useStartupScript = true,
                                                    launchName,
                                                    expectedKill,
                                                    expectedExitCode,
                                                    collectNativeThreads = collectNativeThreads,
                                                    configure = configure)
  // Killed rather than leaked when the session never answers. Nothing else holds this run — the caller only
  // ever sees the `IdeWithLambda` below — so an IDE left behind here is a process no one can address and no one
  // can stop, which then contends with the relaunch that follows for the remote driver's fixed ports. The kill
  // is forced because an IDE whose lambda channel never came up is not reliably able to quit gracefully.
  try {
    monolithRdSession.awaitLambdaSessionReady()
  }
  catch (notReady: Throwable) {
    catchAll("Killing the IDE whose lambda session never became ready") { backgroundRun.forceKill() }
    throw notReady
  }
  return IdeWithLambda(backgroundRun, monolithRdSession, null)
}

internal fun IDERemDevTestContext.runIdeWithLambda(
  runTimeout: Duration = 10.minutes,
  launchName: String = "",
  expectedKill: Boolean = false,
  expectedExitCode: Int = 0,
  collectNativeThreads: Boolean = false,
  configure: IDERunContext.() -> Unit = {},
): IdeWithLambda {
  // the split-mode runner, unless a launch mode was selected through the DriverRunner binding - same choice runIdeWithDriver makes
  val driverRunner = selectedDriverRunner()
  LambdaTestPluginHolder.additionalPluginDirNames(OnlyFrontend, All)
    .forEach { addCustomFrontendPlugin(it) }
  val backendRdSession = setUpLambdaTestSession(BACKEND)
  val frontendRdSession = frontendIDEContext.setUpLambdaTestSession(FRONTEND)

  val backgroundRun = driverRunner.runIdeWithDriver(this,
                                                    determineDefaultCommandLineArguments(),
                                                    emptyList(),
                                                    runTimeout,
                                                    useStartupScript = true,
                                                    launchName,
                                                    expectedKill,
                                                    expectedExitCode,
                                                    collectNativeThreads = collectNativeThreads,
                                                    pauseOnIndexing = null,
                                                    configure = configure)
  // Killed rather than leaked, for the reason the monolith launch above states.
  try {
    listOf(backendRdSession, frontendRdSession)
      .forEach { it.awaitLambdaSessionReady(if (this.frontendIDEContext.ide.vmOptions.hasHeadlessMode()) 15.seconds else 30.seconds) }
  }
  catch (notReady: Throwable) {
    catchAll("Killing the IDEs whose lambda sessions never became ready") { backgroundRun.forceKill() }
    throw notReady
  }
  return IdeWithLambda(backgroundRun,
                       rdSession = frontendRdSession,
                       backendIdeWithLambda = if (backgroundRun is RemoteDevBackgroundRun)
                         IdeWithLambda(backgroundRun.backendRun, backendRdSession, null)
                       else null
  ).also {
    if (testCase.projectInfo != NoProject) {
      @Suppress("RAW_RUN_BLOCKING")
      runBlocking {
        it.runInFrontend("Wait for the project", globalTestScope = true) {
          waitForProject(if (!ApplicationManager.getApplication().isHeadlessEnvironment) 30.seconds else 20.seconds)
        }
      }
    }
  }
}

/**
 * Waits until the IDE has connected back to the session's wire and published itself as ready.
 *
 * Worth failing here rather than on the first lambda: a session that never became ready means the IDE did not
 * read the port or did not load the lambda test plugin, and that reads far better at the launch than as an
 * unexplained timeout inside whatever in-IDE call happened to be first.
 */
@ApiStatus.Internal
fun LambdaRdTestSession.awaitLambdaSessionReady(timeout: Duration = 20.seconds) {
  val timeStarted = System.currentTimeMillis()
  while (ready.value != true && timeStarted + timeout.inWholeMilliseconds > System.currentTimeMillis()) {
    Thread.sleep(500)
  }
  if (ready.value != true) {
    error("Lambda test session '${this}' is not ready after $timeout")
  }
}

/**
 * Opens the in-IDE lambda channel for a launch that has not happened yet.
 *
 * Call order is part of the contract: the wire's port reaches the IDE only as `LAMBDA_TESTING_PORT` in this
 * context's VM options, which are read once at startup, so the session has to exist **before** the launch.
 * Setting a context up again for a later run is safe - `VMOptions.addSystemProperty` replaces an existing
 * `-Dkey=` line instead of appending a second one, so the new port supersedes the old one rather than leaving
 * the IDE to pick whichever it reads first.
 *
 * One session per *run*, never one per context: the protocol lifetime is nested under [EternalLifetime] and
 * terminated by this context's [IdeAfterLaunchEvent] through a one-shot subscription, so the session that
 * served a run is dead as soon as that run's IDE is. A relaunch needs a fresh one.
 *
 * The caller owns the [IdeWithLambda] built around the returned session, because only the caller knows which
 * run it launched. [runIdeWithLambda] is one such caller; a host that starts the IDE through IDE Starter
 * itself is the other, and goes through this function rather than around it so there is one channel setup.
 */
private val sessionsSetUp = AtomicInteger()

@ApiStatus.Internal
fun IDETestContext.setUpLambdaTestSession(lambdaRdIdeType: LambdaRdIdeType): LambdaRdTestSession {
  val testProtocolLifetimeDef = EternalLifetime.createNested()
  // Unique per session, not per IDE type: `LocalEventsFlow.subscribe` drops a duplicate key with an info log,
  // so a key shared with a subscription that is still live would leave this session's protocol lifetime
  // unterminated - and with it its `SocketWire.Server`. That is reachable as soon as two hosts open sessions of
  // the same type in one JVM.
  val eventSubscriber = "testProtocolLifetimeDef-${lambdaRdIdeType.name}-${sessionsSetUp.incrementAndGet()}"
  EventsBus.subscribe(eventSubscriber) { event: IdeAfterLaunchEvent ->
    if (event.runContext.testContext === this) {
      testProtocolLifetimeDef.terminate()
      EventsBus.unsubscribe<IdeAfterLaunchEvent>(eventSubscriber)
    }
  }

  val scheduler = SynchronousScheduler
  val protocolName = LambdaTestsConstants.protocolName + "-" + lambdaRdIdeType.name.lowercase()
  // allow remote connections for docker hosts/clients
  val wire = SocketWire.Server(testProtocolLifetimeDef, scheduler, null, protocolName, true)
  val protocol = Protocol(protocolName, Serializers(), Identities(IdKind.Server), scheduler, wire, testProtocolLifetimeDef)

  val (model, testProtocolPort) = scheduler.executeSyncNonNullable(logErrors = false) {
    protocol.lambdaTestModel to wire.port
  }

  applyVMOptionsPatch {
    addSystemProperty(LambdaTestsConstants.protocolPortPropertyName, testProtocolPort)
    LambdaTestPluginHolder.testModuleId()?.let {
      addSystemProperty(TEST_MODULE_ID_PROPERTY_NAME, it)
    }
  }

  val rdSession = LambdaRdTestSession(lambdaRdIdeType)
  scheduler.queue {
    model.session.value = rdSession
  }
  return rdSession
}

//todo
private fun IDERemDevTestContext.addCustomFrontendPlugin(additionalFrontendPluginModuleName: String) {
  val frontendCustomPluginsDir = frontendIDEContext.paths.pluginsDir
  if (!frontendCustomPluginsDir.exists()) {
    frontendCustomPluginsDir.createDirectories()
  }
  frontendIDEContext.ide.installationPath
    .resolve("plugins").resolve(additionalFrontendPluginModuleName)
    .copyRecursively(frontendCustomPluginsDir.resolve(additionalFrontendPluginModuleName))
}
