package com.intellij.lambda.sampleTests

import com.intellij.lambda.testFramework.junit.ExecuteInMonolithAndSplitMode
import com.intellij.lambda.testFramework.utils.BackgroundRunWithLambda
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestTemplate
import java.time.Instant
import java.util.*

@ExecuteInMonolithAndSplitMode
class MixedExampleTest {

  private var testName: String = ""

  @BeforeEach
  fun setUp(info: TestInfo) {
    testName = info.displayName
  }

  @TestTemplate
  fun testTemplateTest(ide: BackgroundRunWithLambda) = runBlocking {
    ide.runNamedLambdaInBackend(SampleTest.Companion.HelloBackendOnlyLambda::class)
    ide.runNamedLambda(SampleTest.Companion.HelloFrontendOnlyLambda::class)
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