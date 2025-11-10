package com.intellij.lambda.testFramework.utils

import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.IBackgroundRun
import com.intellij.lambda.testFramework.utils.IdeLambdaStarter.toLambdaParams
import com.intellij.remoteDev.tests.LambdaBackendContext
import com.intellij.remoteDev.tests.LambdaFrontendContext
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.impl.LambdaTestHost
import com.intellij.remoteDev.tests.impl.utils.SerializedLambda
import com.intellij.remoteDev.tests.impl.utils.runLogged
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdSerializedLambda
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestActionParameters
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestSession
import kotlin.reflect.KClass

class BackgroundRunWithLambda(delegate: BackgroundRun, val rdSession: LambdaRdTestSession, val backendRdSession: LambdaRdTestSession?) : IBackgroundRun by delegate {
  @Deprecated("Use runSerializedLambda instead")
  internal suspend fun LambdaRdTestSession.runLambda(namedLambdaClass: KClass<out LambdaTestHost.Companion.NamedLambda<*>>, params: Map<String, String> = emptyMap()) {
    runLambda.startSuspending(rdSession.protocol!!.lifetime,
                              LambdaRdTestActionParameters(namedLambdaClass.java.canonicalName, params.toLambdaParams()))
  }

  @Deprecated("Use runSerializedLambda instead")
  suspend fun runLambda(namedLambdaClass: KClass<out LambdaTestHost.Companion.NamedLambda<*>>, params: Map<String, String> = emptyMap()) {
    return rdSession.runLambda(namedLambdaClass, params)
  }

  @Deprecated("Use runSerializedLambdaInBackend instead")
  suspend fun runLambdaInBackend(namedLambdaClass: KClass<out LambdaTestHost.Companion.NamedLambda<*>>, params: Map<String, String> = emptyMap()) {
    return (backendRdSession ?: rdSession).runLambda(namedLambdaClass, params)
  }


  suspend inline fun <T : LambdaIdeContext> LambdaRdTestSession.runSerializedLambda(name: String? = null, crossinline lambda: suspend T.() -> Unit) {
    val exec = SerializedLambda.fromLambdaWithCoroutineScope(lambda)
    runLogged(name ?: ("Step-" + System.currentTimeMillis())) {
      runSerializedLambda.startSuspending(rdSession.protocol!!.lifetime,
                                          LambdaRdSerializedLambda(exec.clazzName, exec.methodName, exec.serializedDataBase64,
                                                                   exec.classPath.map { it.canonicalPath }))
    }
  }

  suspend inline fun runSerializedLambda(name: String? = null, crossinline lambda: suspend LambdaFrontendContext.() -> Unit) {
    return rdSession.runSerializedLambda(name, lambda)
  }

  suspend inline fun runSerializedLambdaInBackend(name: String? = null, crossinline lambda: suspend LambdaBackendContext.() -> Unit) {
    return (backendRdSession ?: rdSession).runSerializedLambda(name, lambda)
  }
}