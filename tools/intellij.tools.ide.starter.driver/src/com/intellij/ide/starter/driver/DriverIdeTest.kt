package com.intellij.ide.starter.driver

import com.intellij.driver.client.Driver
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.runner.IDERunContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Runs an IDE driver test with the specified configuration
 *
 * @param T The return type of the test function
 * @param testName The name of the test to be executed
 * @param configure Function to configure the IDE run context before test execution
 * @param timeout Maximum duration allowed for test execution
 * @param beforeTest Function to execute before running the main test
 * @param afterTest Function to execute after running the main test
 * @param onTestComplete Callback function invoked when test completes successfully
 * @param onTestFailure Callback function invoked if test fails with an error
 * @param test The actual test function to execute that returns type T
 */
fun <T> IDETestContext.runIdeTest(
  testName: String,
  configure: IDERunContext.() -> Unit = {},
  timeout: Duration = 5.minutes,
  beforeTest: Driver.() -> Unit = {},
  afterTest: Driver.() -> Unit = {},
  onTestComplete: (IDEStartResult) -> Unit = {},
  onTestFailure: (IDETestContext, error: Throwable) -> Unit = { _, _ -> },
  test: Driver.() -> T,
) {
  val result = this.runIdeWithDriver(launchName = testName, runTimeout = timeout) {
    configure(this)
  }
  try {
    onTestComplete.invoke(result.useDriverAndCloseIde {
      beforeTest()
      val testResult = runCatching {
        test(this)
      }
      val afterTestResult = runCatching {
        afterTest()
      }
      val exceptions: List<Throwable> = listOfNotNull(testResult.exceptionOrNull(), afterTestResult.exceptionOrNull())
      val error: Throwable? = exceptions.firstOrNull()
      exceptions.drop(1).forEach { error?.addSuppressed(it) }
      error?.let { throw it }
    })
  }
  catch (e: Throwable) {
    onTestFailure(this, e)
    throw e
  }
}