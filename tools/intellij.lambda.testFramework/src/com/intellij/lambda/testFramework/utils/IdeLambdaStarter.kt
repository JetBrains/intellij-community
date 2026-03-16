package com.intellij.lambda.testFramework.utils

import com.intellij.ide.starter.driver.driver.remoteDev.RemDevDriverRunner
import com.intellij.ide.starter.driver.engine.LocalDriverRunner
import com.intellij.ide.starter.ide.IDERemDevTestContext
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.onRemDevContext
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.events.IdeAfterLaunchEvent
import com.intellij.lambda.testFramework.testApi.waitForProject
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
  val monolithRdSession = setUpRdTestSession(MONOLITH)
  val backgroundRun = driverRunner.runIdeWithDriver(this,
                                                    determineDefaultCommandLineArguments(),
                                                    emptyList(),
                                                    runTimeout,
                                                    useStartupScript = true,
                                                    launchName,
                                                    expectedKill,
                                                    expectedExitCode,
                                                    collectNativeThreads,
                                                    configure)
  monolithRdSession.awaitSessionReady()
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
  val driverRunner = RemDevDriverRunner()
  LambdaTestPluginHolder.additionalPluginDirNames().forEach { addCustomFrontendPlugin(it) }
  val backendRdSession = setUpRdTestSession(BACKEND)
  val frontendRdSession = frontendIDEContext.setUpRdTestSession(FRONTEND)

  val backgroundRun = driverRunner.runIdeWithDriver(this,
                                                    determineDefaultCommandLineArguments(),
                                                    emptyList(),
                                                    runTimeout,
                                                    useStartupScript = true,
                                                    launchName,
                                                    expectedKill,
                                                    expectedExitCode,
                                                    collectNativeThreads,
                                                    configure)
  listOf(backendRdSession, frontendRdSession)
    .forEach { it.awaitSessionReady(if (this.frontendIDEContext.ide.vmOptions.hasHeadlessMode()) 15.seconds else 30.seconds) }
  return IdeWithLambda(backgroundRun, rdSession = frontendRdSession, backendRdSession = backendRdSession).also {
    if (testCase.projectInfo != NoProject) {
      @Suppress("RAW_RUN_BLOCKING")
      runBlocking {
        it.runInFrontend("Wait for the project") {
          waitForProject(if (!ApplicationManager.getApplication().isHeadlessEnvironment) 30.seconds else 20.seconds)
        }
      }
    }
  }
}

private fun LambdaRdTestSession.awaitSessionReady(timeout: Duration = 15.seconds) {
  val timeStarted = System.currentTimeMillis()
  while (ready.value != true && timeStarted + timeout.inWholeMilliseconds > System.currentTimeMillis()) {
    Thread.sleep(500)
  }
  if (ready.value != true) {
    error("Lambda test session '${this}' is not ready after $timeout")
  }
}

private fun IDETestContext.setUpRdTestSession(lambdaRdIdeType: LambdaRdIdeType): LambdaRdTestSession {
  val testProtocolLifetimeDef = EternalLifetime.createNested()
  EventsBus.subscribe("testProtocolLifetimeDef-${lambdaRdIdeType.name}") { _: IdeAfterLaunchEvent ->
    testProtocolLifetimeDef.terminate()
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