package com.intellij.lambda.testFramework.utils

import com.intellij.ide.starter.driver.driver.remoteDev.RemDevDriverRunner
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.IBackgroundRun
import com.intellij.ide.starter.driver.engine.LocalDriverRunner
import com.intellij.ide.starter.ide.IDERemDevTestContext
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.events.IdeAfterLaunchEvent
import com.intellij.remoteDev.tests.LambdaBackendContext
import com.intellij.remoteDev.tests.LambdaFrontendContext
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.LambdaTestsConstants
import com.intellij.remoteDev.tests.impl.LambdaTestHost.Companion.NamedLambda
import com.intellij.remoteDev.tests.impl.LambdaTestHost.Companion.TEST_MODULE_ID_PROPERTY_NAME
import com.intellij.remoteDev.tests.impl.utils.SerializedLambda
import com.intellij.remoteDev.tests.modelGenerated.*
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdIdeType.*
import com.intellij.remoteDev.util.executeSyncNonNullable
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.util.io.copyRecursively
import com.intellij.util.io.createDirectories
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.util.lifetime.EternalLifetime
import com.jetbrains.rd.util.threading.SynchronousScheduler
import kotlin.io.path.exists
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes


object IdeLambdaStarter {
  const val LAMBDA_TEST_MODULE: String = "intellij.lambda.tests._test"
  const val ADDITIONAL_LAMBDA_TEST_PLUGIN: String = "intellij.additional.lambda.tests.plugin"
  private const val ADDITIONAL_LAMBDA_DIR_NAME = "additional-lambda-tests-plugin"


  internal fun Map<String, String>.toLambdaParams(): List<LambdaRdKeyValueEntry> = map { LambdaRdKeyValueEntry(it.key, it.value) }

  class BackgroundRunWithLambda(delegate: BackgroundRun, val rdSession: LambdaRdTestSession, val backendRdSession: LambdaRdTestSession?) : IBackgroundRun by delegate {
    internal suspend fun LambdaRdTestSession.runLambda(namedLambdaClass: KClass<out NamedLambda<*>>, params: Map<String, String> = emptyMap()) {
      runLambda.startSuspending(rdSession.protocol!!.lifetime,
                                LambdaRdTestActionParameters(namedLambdaClass.java.canonicalName, params.toLambdaParams()))
    }

    suspend fun runLambda(namedLambdaClass: KClass<out NamedLambda<*>>, params: Map<String, String> = emptyMap()) {
      return rdSession.runLambda(namedLambdaClass, params)
    }

    suspend fun runLambdaInBackend(namedLambdaClass: KClass<out NamedLambda<*>>, params: Map<String, String> = emptyMap()) {
      return (backendRdSession ?: rdSession).runLambda(namedLambdaClass, params)
    }


    suspend inline fun <T : LambdaIdeContext> LambdaRdTestSession.runSerializedLambda(crossinline lambda: suspend T.() -> Unit) {
      val exec = SerializedLambda.fromLambdaWithCoroutineScope(lambda)
      runSerializedLambda.startSuspending(rdSession.protocol!!.lifetime, LambdaRdSerializedLambda(exec.clazzName, exec.methodName, exec.serializedDataBase64, exec.classPath.map { it.canonicalPath }))
    }

    suspend inline fun runSerializedLambda(crossinline lambda: suspend LambdaFrontendContext.() -> Unit) {
      return rdSession.runSerializedLambda(lambda)
    }

    suspend inline fun runSerializedLambdaInBackend(crossinline lambda: suspend LambdaBackendContext.() -> Unit) {
      return (backendRdSession ?: rdSession).runSerializedLambda(lambda)
    }
  }

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
      addCustomFrontendPlugin(ADDITIONAL_LAMBDA_DIR_NAME)

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
      addSystemProperty(TEST_MODULE_ID_PROPERTY_NAME, LAMBDA_TEST_MODULE)
    }

    val rdSession = LambdaRdTestSession(lambdaRdIdeType)
    scheduler.queue {
      model.session.value = rdSession
    }
    return rdSession
  }

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