package com.intellij.lambda.testFramework.utils

import com.intellij.ide.starter.driver.driver.remoteDev.RemDevDriverRunner
import com.intellij.ide.starter.driver.engine.LocalDriverRunner
import com.intellij.ide.starter.ide.IDERemDevTestContext
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.events.IdeAfterLaunchEvent
import com.intellij.remoteDev.tests.LambdaTestsConstants
import com.intellij.remoteDev.tests.impl.LambdaTestHost.Companion.TEST_MODULE_ID_PROPERTY_NAME
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdIdeType
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdIdeType.*
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdKeyValueEntry
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestSession
import com.intellij.remoteDev.tests.modelGenerated.lambdaTestModel
import com.intellij.remoteDev.util.executeSyncNonNullable
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.util.io.copyRecursively
import com.intellij.util.io.createDirectories
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.util.lifetime.EternalLifetime
import com.jetbrains.rd.util.threading.SynchronousScheduler
import kotlin.io.path.exists
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

object IdeLambdaStarter {
  internal fun Map<String, String>.toLambdaParams(): List<LambdaRdKeyValueEntry> = map { LambdaRdKeyValueEntry(it.key, it.value) }

  fun IDETestContext.runIdeWithLambda(
    runTimeout: Duration = 10.minutes,
    launchName: String = "",
    expectedKill: Boolean = false,
    expectedExitCode: Int = 0,
    collectNativeThreads: Boolean = false,
    configure: IDERunContext.() -> Unit = {},
  ): BackgroundRunWithLambda {
    if (this is IDERemDevTestContext) {
      val driverRunner = RemDevDriverRunner()
      LambdaTestPluginHolder.additionalPluginDirNames().forEach { addCustomFrontendPlugin(it) }
      val backendRdSession = setUpRdTestSession(BACKEND)
      val frontendRdSession = frontendIDEContext.setUpRdTestSession(FRONTEND)

      val backgroundRun = driverRunner.runIdeWithDriver(this, determineDefaultCommandLineArguments(), emptyList(), runTimeout, useStartupScript = true, launchName, expectedKill, expectedExitCode, collectNativeThreads, configure)
      return BackgroundRunWithLambda(backgroundRun, rdSession = frontendRdSession, backendRdSession = backendRdSession)
    }

    val driverRunner = LocalDriverRunner()
    val monolithRdSession = setUpRdTestSession(MONOLITH)
    val backgroundRun = driverRunner.runIdeWithDriver(this, determineDefaultCommandLineArguments(), emptyList(), runTimeout, useStartupScript = true, launchName, expectedKill, expectedExitCode, collectNativeThreads, configure)
    return BackgroundRunWithLambda(backgroundRun, monolithRdSession, null)
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
      addSystemProperty(TEST_MODULE_ID_PROPERTY_NAME, LambdaTestPluginHolder.testModuleId())
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
}