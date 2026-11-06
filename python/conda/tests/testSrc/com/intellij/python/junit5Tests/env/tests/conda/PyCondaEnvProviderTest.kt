package com.intellij.python.junit5Tests.env.tests.conda

import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.ProcessInteractiveHandler
import com.intellij.python.community.execService.asBinToExec
import com.intellij.python.community.junit5Tests.framework.conda.CondaEnv
import com.intellij.python.community.junit5Tests.framework.conda.PyEnvTestCaseWithConda
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.conda.execution.models.CondaEnvInfo
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

private val FAILURE = PyResult.localizedError("Test error")

@PyEnvTestCaseWithConda
internal class PyCondaEnvProviderTest {
  private lateinit var conda: BinaryToExec
  private lateinit var condaEnvInfo: CondaEnvInfo
  private lateinit var execService: TestExecService
  private lateinit var provider: PyCondaEnvProvider

  @BeforeEach
  fun setUp(@CondaEnv condaEnv: PyCondaEnv) {
    val condaPath = Path.of(condaEnv.fullCondaPathOnTarget)
    conda = condaPath.asBinToExec()
    condaEnvInfo = CondaEnvInfo(listOf(condaEnv.envIdentity.userReadableName), condaPrefix = condaPath.parent.parent.toString())
    execService = TestExecService()
    provider = PyCondaEnvProvider(
      refreshInterval = 1.minutes,
      ttlAfterWrite = 1.minutes,
      execService = execService,
    )
  }

  @Test
  fun checkOnlyOneCacheLoad(@CondaEnv condaEnv: PyCondaEnv): Unit = timeoutRunBlocking(60.seconds) {
    execService.env = condaEnvInfo
    (1..100).map {
      launch {
        val randomDelay = Random.nextInt(2000).milliseconds
        delay(randomDelay)
        provider.getEnvs(conda, false)
      }
    }.joinAll()
    val result = provider.getEnvs(conda, false)
    Assertions.assertIterableEquals(listOf(condaEnv), result.successOrNull)
    Assertions.assertEquals(0, execService.failureHits)
    Assertions.assertEquals(1, execService.successHits)
  }

  @Test
  fun checkCacheRefresh(@CondaEnv condaEnv: PyCondaEnv): Unit = timeoutRunBlocking(60.seconds) {
    execService.env = condaEnvInfo
    provider.getEnvs(conda, false)

    execService.env = null
    var result = provider.getEnvs(conda, false)
    Assertions.assertIterableEquals(listOf(condaEnv), result.successOrNull)
    Assertions.assertEquals(0, execService.failureHits)
    Assertions.assertEquals(1, execService.successHits)

    result = provider.getEnvs(conda, true)
    Assertions.assertEquals(FAILURE, result)
    Assertions.assertEquals(1, execService.failureHits)
  }

  @Test
  fun checkRefreshAfterFailure(): Unit = timeoutRunBlocking(60.seconds) {
    var result = provider.getEnvs(conda, false)
    Assertions.assertEquals(FAILURE, result)
    Assertions.assertEquals(1, execService.failureHits)

    result = provider.getEnvs(conda, false)
    Assertions.assertEquals(FAILURE, result)
    Assertions.assertEquals(2, execService.failureHits)
  }

  @Test
  fun checkRefreshAfterTimeout(): Unit = timeoutRunBlocking(60.seconds) {
    provider = PyCondaEnvProvider(
      refreshInterval = 1.nanoseconds,
      ttlAfterWrite = 1.minutes,
      execService = execService,
    )
    execService.env = condaEnvInfo
    provider.getEnvs(conda, false)

    execService.env = null

    waitUntilAssertSucceeds(timeout = 5.seconds) {
      Assertions.assertEquals(FAILURE, provider.getEnvs(conda, false))
    }
  }

  @Test
  fun checkPreviousValueWhileComputing(@CondaEnv condaEnv: PyCondaEnv): Unit = timeoutRunBlocking(60.seconds) {
    provider = PyCondaEnvProvider(
      refreshInterval = 1.nanoseconds,
      ttlAfterWrite = 1.minutes,
      execService = execService,
    )
    execService.env = condaEnvInfo
    provider.getEnvs(conda, false)

    execService.env = null
    execService.delay = 5.seconds

    repeat(5) {
      val result = provider.getEnvs(conda, false)
      Assertions.assertIterableEquals(listOf(condaEnv), result.successOrNull)
      Assertions.assertEquals(1, execService.successHits)
      Assertions.assertEquals(0, execService.failureHits)
    }
  }

  @Test
  fun checkStaleValueNotReturned(): Unit = timeoutRunBlocking(60.seconds) {
    provider = PyCondaEnvProvider(
      refreshInterval = 1.minutes,
      ttlAfterWrite = 100.milliseconds,
      execService = execService,
    )
    execService.env = condaEnvInfo
    provider.getEnvs(conda, false)

    delay(2.seconds)
    execService.env = null

    Assertions.assertEquals(FAILURE, provider.getEnvs(conda, false))
  }
}

private class TestExecService : ExecService by ExecService() {

  var env: CondaEnvInfo? = null
  var failureHits = 0
    private set
  var successHits = 0
    private set
  var delay: Duration = Duration.ZERO

  override suspend fun <T> executeAdvanced(
    binary: BinaryToExec,
    args: Args,
    options: ExecOptions,
    processInteractiveHandler: ProcessInteractiveHandler<T>,
  ): PyResult<T> {
    delay(delay)

    return if (env == null) {
      failureHits++
      FAILURE
    }
    else {
      successHits++
      @Suppress("UNCHECKED_CAST") // The fake answers each call with the conda env info.
      PyResult.success(env) as PyResult<T>
    }
  }
}
