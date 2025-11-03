package com.intellij.lambda.tests

import com.intellij.lambda.testFramework.junit.ExecuteInMonolithAndSplitMode
import com.intellij.lambda.testFramework.utils.BackgroundRunWithLambda
import com.intellij.lambda.tests.SampleTests.Companion.HelloBackendOnlyLambda
import com.intellij.lambda.tests.SampleTests.Companion.HelloFrontendOnlyLambda
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestTemplate
import java.time.Instant
import java.util.*

@ExecuteInMonolithAndSplitMode
class FrontAndBackExampleTest {

  private var testName: String = ""

  @BeforeEach
  fun setUp(info: TestInfo) {
    testName = info.displayName
  }

  @Disabled
  @TestTemplate
  fun testTemplateTest(ide: BackgroundRunWithLambda) = runBlocking {
    ide.runLambdaInBackend(HelloBackendOnlyLambda::class)
    ide.runLambda(HelloFrontendOnlyLambda::class)
  }

  @TestTemplate
  fun simpleUnitTest() {
    println("${Date.from(Instant.now())} Hello from simpleUnitTest! ${testName}")
  }

  @TestTemplate
  fun anotherTest() {
    println("${Date.from(Instant.now())} Hello from anotherTest! ${testName}")
  }
}