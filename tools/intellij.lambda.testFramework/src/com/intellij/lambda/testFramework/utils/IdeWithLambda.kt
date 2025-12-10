package com.intellij.lambda.testFramework.utils

import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.IBackgroundRun
import com.intellij.remoteDev.tests.LambdaBackendContext
import com.intellij.remoteDev.tests.LambdaFrontendContext
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.impl.utils.SerializedLambda
import com.intellij.remoteDev.tests.impl.utils.SerializedLambdaLoader
import com.intellij.remoteDev.tests.impl.utils.SuspendingSerializableConsumer
import com.intellij.remoteDev.tests.impl.utils.runLogged
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdSerializedLambda
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestSession
import java.io.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class IdeWithLambda(delegate: BackgroundRun, val rdSession: LambdaRdTestSession, val backendRdSession: LambdaRdTestSession?) :
  IBackgroundRun by delegate {
  suspend inline fun <T : LambdaIdeContext, R: Any> LambdaRdTestSession.run(
    name: String? = null,
    timeout: Duration = 1.minutes,
    parameters: List<Serializable> = emptyList(),
    lambdaConsumer: SuspendingSerializableConsumer<T, R>,
  ): Serializable? {
    val protocol = this@run.protocol
                   ?: error("RD Protocol is not initialized for session. Make sure the IDE connection is established before running tests.")
    val exec = SerializedLambda.fromSuspendingSerializableConsumer(name, lambdaConsumer)
    val parametersBase64 = parameters.map { SerializedLambdaLoader().save(name, it) }
    val lambda = LambdaRdSerializedLambda("${protocol.name}: ${
      name ?: ("Step " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")))
    }",
                                          exec.serializedDataBase64,
                                          exec.classPath.map { it.canonicalPath },
                                          parametersBase64)
    return runLogged(lambda.stepName, timeout) {
      val base64 = runSerializedLambda.startSuspending(protocol.lifetime, lambda)
      SerializedLambdaLoader().loadObjectAsSerializable(base64)
    }
  }

  suspend inline fun runInFrontendGetResult(
    name: String? = null,
    parameters: List<Serializable> = emptyList(),
    lambdaConsumer: SuspendingSerializableConsumer<LambdaFrontendContext, Serializable>,
  ): Serializable {
    return rdSession.run(name, parameters = parameters, lambdaConsumer = lambdaConsumer)
           ?: error("Run hasn't returned a Serializable result")
  }

  suspend inline fun runInFrontend(
    name: String? = null,
    parameters: List<Serializable> = emptyList(),
    lambdaConsumer: SuspendingSerializableConsumer<LambdaFrontendContext, Any>,
  ) {
    runInFrontendGetResult(name, parameters) { parameters ->
      with(lambdaConsumer) {
        runSerializedLambda(parameters)
      }
      true
    }
  }

  suspend inline fun runInBackendGetResult(
    name: String? = null,
    parameters: List<Serializable> = emptyList(),
    lambdaConsumer: SuspendingSerializableConsumer<LambdaBackendContext, Serializable>,
  ): Serializable {
    return (backendRdSession ?: rdSession).run(name, parameters = parameters, lambdaConsumer = lambdaConsumer)
           ?: error("Run hasn't returned a Serializable result")
  }

  suspend inline fun runInBackend(
    name: String? = null,
    parameters: List<Serializable> = emptyList(),
    lambdaConsumer: SuspendingSerializableConsumer<LambdaBackendContext, Any>,
  ) {
    runInBackendGetResult(name, parameters) { parameters ->
      with(lambdaConsumer) {
        runSerializedLambda(parameters)
      }
      true
    }
  }

  suspend inline fun cleanUp() {
    listOfNotNull(rdSession, backendRdSession).forEach { it.cleanUp.startSuspending(Unit) }
  }
}