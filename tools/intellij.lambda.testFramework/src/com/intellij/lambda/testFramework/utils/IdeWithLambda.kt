package com.intellij.lambda.testFramework.utils

import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.IBackgroundRun
import com.intellij.lambda.testFramework.starter.IdeInstance.runContext
import com.intellij.remoteDev.tests.LambdaBackendContext
import com.intellij.remoteDev.tests.LambdaFrontendContext
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.impl.utils.SerializedLambdaWithIdeContextHelper
import com.intellij.remoteDev.tests.impl.utils.runLogged
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdIdeType
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdSerialized
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestSession
import java.io.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class IdeWithLambda(delegate: BackgroundRun, val rdSession: LambdaRdTestSession, val backendRdSession: LambdaRdTestSession?) :
  IBackgroundRun by delegate {
  fun defaultStepName(): String = "Step " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
  val isRemoteDev: Boolean = rdSession.rdIdeType == LambdaRdIdeType.FRONTEND
  val defaultTimeout = 1.minutes

  suspend inline fun <T : LambdaIdeContext, R : Serializable> LambdaRdTestSession.runGetResult(
    name: String,
    timeout: Duration = defaultTimeout,
    parameters: List<Serializable> = emptyList(),
    globalTestScope: Boolean = false,
    lambdaConsumer: SerializedLambdaWithIdeContextHelper.SuspendingSerializableConsumer<T, R>,
  ): R? {
    val protocol = this@runGetResult.protocol
                   ?: error("RD Protocol is not initialized for session. Make sure the IDE connection is established before running tests.")
    SerializedLambdaWithIdeContextHelper().let { loader ->
      val serializedLambda = try {
        loader.getSerializedLambda(parameters, lambdaConsumer)
      }
      catch (t: Throwable) {
        throw IllegalStateException("Failed to serialize lambda '$name'", t)
      }
      val lambdaRdSerialized =
        LambdaRdSerialized(name,
                           serializedLambda.serializedDataBase64,
                           serializedLambda.classPath.map { it.canonicalPath },
                           serializedLambda.parametersBase64,
                           globalTestScope)

      return runLogged(lambdaRdSerialized.stepName, timeout) {
        val returnValueBase64 = runSerializedLambda.startSuspending(protocol.lifetime, lambdaRdSerialized)
        loader.decodeObject(returnValueBase64)
      }
    }
  }

  suspend inline fun runInFrontendGetResult(
    name: String = defaultStepName(),
    parameters: List<Serializable> = emptyList(),
    globalTestScope: Boolean = false,
    timeout: Duration = defaultTimeout,
    lambdaConsumer: SerializedLambdaWithIdeContextHelper.SuspendingSerializableConsumer<LambdaFrontendContext, Serializable>,
  ): Serializable {
    return rdSession.runGetResult(name, parameters = parameters, lambdaConsumer = lambdaConsumer, timeout = timeout, globalTestScope = globalTestScope)
           ?: error("Run hasn't returned a Serializable result")
  }

  suspend inline fun runInFrontend(
    name: String = defaultStepName(),
    parameters: List<Serializable> = emptyList(),
    globalTestScope: Boolean = false,
    timeout: Duration = defaultTimeout,
    lambdaConsumer: SerializedLambdaWithIdeContextHelper.SuspendingSerializableConsumer<LambdaFrontendContext, Any?>,
  ) {
    runInFrontendGetResult(name, parameters, globalTestScope, timeout) { parameters ->
      with(lambdaConsumer) {
        runSerializedLambda(parameters)
      }
      true
    }
  }

  suspend inline fun runInBackendGetResult(
    name: String = defaultStepName(),
    parameters: List<Serializable> = emptyList(),
    globalTestScope: Boolean = false,
    timeout: Duration = defaultTimeout,
    lambdaConsumer: SerializedLambdaWithIdeContextHelper.SuspendingSerializableConsumer<LambdaBackendContext, Serializable>,
  ): Serializable {
    return (backendRdSession ?: rdSession).runGetResult(name,
                                                        parameters = parameters,
                                                        lambdaConsumer = lambdaConsumer,
                                                        globalTestScope = globalTestScope,
                                                        timeout = timeout)
           ?: error("Run hasn't returned a Serializable result")
  }

  suspend inline fun runInBackend(
    name: String = defaultStepName(),
    parameters: List<Serializable> = emptyList(),
    globalTestScope: Boolean = false,
    timeout: Duration = defaultTimeout,
    lambdaConsumer: SerializedLambdaWithIdeContextHelper.SuspendingSerializableConsumer<LambdaBackendContext, Any?>,
  ) {
    runInBackendGetResult(name, parameters, globalTestScope, timeout) { parameters ->
      with(lambdaConsumer) {
        runSerializedLambda(parameters)
      }
      true
    }
  }

  suspend inline fun forEachSession(
    stepNamePrefix: String,
    crossinline action: suspend (LambdaRdTestSession) -> Unit,
  ) {
    val inDebug = runContext.frontendContext.calculateVmOptions().isUnderDebug()
    listOfNotNull(rdSession, backendRdSession).forEach { session ->
      runLogged("$stepNamePrefix for ${session.rdIdeType}", if (!inDebug) 30.seconds else 10.minutes) {
        action(session)
      }
    }
  }

  suspend inline fun beforeAll(testName: String) {
    forEachSession("Before each container") { it.beforeAll.startSuspending(testName) }
  }

  suspend inline fun beforeEach(testClassName: String) {
    forEachSession("Before each") { it.beforeEach.startSuspending(testClassName) }
  }

  suspend inline fun afterEach(testName: String) {
    forEachSession("After each") { it.afterEach.startSuspending(testName) }
  }

  suspend inline fun afterAll(testClassName: String) {
    forEachSession("After each container") { it.afterAll.startSuspending(testClassName) }
  }

  suspend inline operator fun invoke(block: suspend IdeWithLambda.() -> Unit) {
    block()
  }
}