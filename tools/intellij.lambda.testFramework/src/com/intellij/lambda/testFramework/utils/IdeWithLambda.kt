package com.intellij.lambda.testFramework.utils

import com.intellij.driver.client.Driver
import com.intellij.ide.starter.driver.driver.remoteDev.RemoteDevBackgroundRun
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.reused.restartReusedIde
import com.intellij.lambda.testFramework.starter.LambdaIdeSession
import com.intellij.remoteDev.tests.LambdaBackendContext
import com.intellij.remoteDev.tests.LambdaFrontendContext
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.impl.utils.SerializedLambdaWithIdeContextHelper
import com.intellij.remoteDev.tests.impl.utils.runLogged
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdIdeType
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdSerialized
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestSession
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class IdeWithLambda(internal val backgroundRun: BackgroundRun, val rdSession: LambdaRdTestSession, val backendIdeWithLambda: IdeWithLambda?) {
  init {
      if (backgroundRun is RemoteDevBackgroundRun) {
        checkNotNull(backendIdeWithLambda) { "Remote dev background run should be not null" }
      } else {
        check(backendIdeWithLambda == null) { "Remote dev background run should be null" }
      }
  }
  fun defaultStepName(): String = "Step " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
  val driver: Driver = backgroundRun.driver
  val isRemoteDev: Boolean = backgroundRun is RemoteDevBackgroundRun
  val defaultTimeout: Duration = 1.minutes

  /** The context of the test that got this IDE as a parameter. [restart] restarts the session of that test. */
  internal var testContext: ExtensionContext? = null

  /**
   * Restarts this IDE in the middle of a test. The new IDE keeps the config and the system directories, and gets the
   * IDE-side `beforeAll` and `beforeEach` of the test again. The test goes on with the returned IDE, because this one
   * is closed.
   *
   * @param betweenRuns runs while no IDE is running, for example to change a file that the new IDE reads at its start
   * @return the new IDE
   */
  fun restart(betweenRuns: () -> Unit = {}): IdeWithLambda {
    val context = checkNotNull(testContext) { "Only an IDE that a test got as a parameter can restart" }
    context.restartReusedIde {
      closeIdeAndWait()
      betweenRuns()
    }
    return LambdaIdeSession.current(context).also { it.testContext = context }
  }

  fun LambdaRdTestSession.getRdIdeTypePrefix(): String = if (rdIdeType != LambdaRdIdeType.MONOLITH) "${rdIdeType}: " else ""

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
      return runLogged(this@runGetResult.getRdIdeTypePrefix() + lambdaRdSerialized.stepName, timeout) {
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
    return rdSession.runGetResult(name,
                                  parameters = parameters,
                                  lambdaConsumer = lambdaConsumer,
                                  timeout = timeout,
                                  globalTestScope = globalTestScope)
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
    return (backendIdeWithLambda?: this).rdSession.runGetResult(name,
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

  suspend inline operator fun invoke(block: suspend IdeWithLambda.() -> Unit) {
    block()
  }
}