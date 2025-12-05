package com.intellij.lambda.testFramework.utils

import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.IBackgroundRun
import com.intellij.remoteDev.tests.LambdaBackendContext
import com.intellij.remoteDev.tests.LambdaFrontendContext
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.impl.utils.SerializedLambda
import com.intellij.remoteDev.tests.impl.utils.SerializedLambdaLoader
import com.intellij.remoteDev.tests.impl.utils.runLogged
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdSerializedLambda
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestSession
import java.io.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class IdeWithLambda(delegate: BackgroundRun, val rdSession: LambdaRdTestSession, val backendRdSession: LambdaRdTestSession?) :
  IBackgroundRun by delegate {
  suspend inline fun <T : LambdaIdeContext> LambdaRdTestSession.run(
    name: String? = null,
    parameters: List<Serializable> = emptyList(),
    crossinline lambda: suspend T.(List<Serializable>) -> Serializable,
  ): Serializable {
    val protocol = this@run.protocol
                   ?: error("RD Protocol is not initialized for session. Make sure the IDE connection is established before running tests.")
    val exec = SerializedLambda.fromLambdaWithCoroutineScope(name, lambda)
    val parametersBase64 = parameters.map { SerializedLambdaLoader().save(name, it) }
    val lambda = LambdaRdSerializedLambda("${protocol.name}: ${
      name ?: ("Step " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")))
    }",
                                          exec.serializedDataBase64,
                                          exec.classPath.map { it.canonicalPath },
                                          parametersBase64)
    return runLogged(lambda.stepName) {
      val base64 = runSerializedLambda.startSuspending(protocol.lifetime, lambda)
      SerializedLambdaLoader().loadObject(base64)
    }
  }

  suspend inline fun runGetResult(
    name: String? = null,
    parameters: List<Serializable> = emptyList(),
    crossinline lambda: suspend LambdaFrontendContext.(List<Serializable>) -> Serializable,
  ): Serializable {
    return rdSession.run(name, parameters, lambda)
  }

  suspend inline fun run(
    name: String? = null,
    parameters: List<Serializable> = emptyList(),
    crossinline lambda: suspend LambdaFrontendContext.(List<Serializable>) -> Unit,
  ) {
    runGetResult(name, parameters) {
      lambda(it)
      true
    }
  }

  suspend inline fun runInBackendGetResult(
    name: String? = null,
    parameters: List<Serializable> = emptyList(),
    crossinline lambda: suspend LambdaBackendContext.(List<Serializable>) -> Serializable,
  ): Serializable {
    return (backendRdSession ?: rdSession).run(name, parameters, lambda)
  }

  suspend inline fun runInBackend(
    name: String? = null,
    parameters: List<Serializable> = emptyList(),
    crossinline lambda: suspend LambdaBackendContext.(List<Serializable>) -> Unit,
  ) {
    runInBackendGetResult(name, parameters) {
      lambda(it)
      true
    }
  }

  suspend inline fun cleanUp() {
    listOfNotNull(rdSession, backendRdSession).forEach { it.cleanUp.startSuspending(Unit) }
  }
}