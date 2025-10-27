package com.intellij.lambda.tests

import com.intellij.lambda.testFramework.junit.ExecuteInMonolithAndSplitMode
import com.intellij.lambda.testFramework.utils.BackgroundRunWithLambda
import com.intellij.lambda.tests.SampleTests.Companion.HelloBackendOnlyLambda
import com.intellij.lambda.tests.SampleTests.Companion.HelloFrontendOnlyLambda
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.TestTemplate

@ExecuteInMonolithAndSplitMode
class FrontAndBackExampleTest {

  @TestTemplate
  fun testTemplateTest(ide: BackgroundRunWithLambda) = runBlocking {
    ide.runLambdaInBackend(HelloBackendOnlyLambda::class)
    ide.runLambda(HelloFrontendOnlyLambda::class)
  }

  @TestTemplate
  fun simpleUnitTest() {
    ApplicationManager.getApplication().invokeAndWait { println("Test template test : badums") }
  }
}