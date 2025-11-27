package com.intellij.lambda.testFramework.utils

import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.IBackgroundRun
import com.intellij.remoteDev.tests.LambdaBackendContext
import com.intellij.remoteDev.tests.LambdaFrontendContext
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.impl.utils.SerializedLambda
import com.intellij.remoteDev.tests.impl.utils.runLogged
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdSerializedLambda
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestSession
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BackgroundRunWithLambda(delegate: BackgroundRun, val rdSession: LambdaRdTestSession, val backendRdSession: LambdaRdTestSession?) : IBackgroundRun by delegate {
  suspend inline fun <T : LambdaIdeContext> LambdaRdTestSession.run(name: String? = null, crossinline lambda: suspend T.() -> Unit) {
    val protocol = this@run.protocol
                   ?: error("RD Protocol is not initialized for session. Make sure the IDE connection is established before running tests.")
    val exec = SerializedLambda.fromLambdaWithCoroutineScope(lambda)
    val lambda = LambdaRdSerializedLambda("${protocol.name}: ${name ?: ("Step " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")))}",
                                          exec.serializedDataBase64,
                                          exec.classPath.map { it.canonicalPath })
    runLogged(lambda.stepName) {
      runSerializedLambda.startSuspending(protocol.lifetime, lambda)
    }
  }

  suspend inline fun run(name: String? = null, crossinline lambda: suspend LambdaFrontendContext.() -> Unit) {
    return rdSession.run(name, lambda)
  }

  suspend inline fun runInBackend(name: String? = null, crossinline lambda: suspend LambdaBackendContext.() -> Unit) {
    return (backendRdSession ?: rdSession).run(name, lambda)
  }
}