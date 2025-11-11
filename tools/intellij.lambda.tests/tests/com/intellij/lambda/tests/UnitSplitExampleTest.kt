package com.intellij.lambda.tests

import com.intellij.lambda.testFramework.junit.ExecuteInMonolithAndSplitMode
import com.intellij.openapi.application.ApplicationManager
import org.junit.jupiter.api.TestTemplate

@ExecuteInMonolithAndSplitMode
class UnitSplitExampleTest {
  @TestTemplate
  fun testTemplateTest() {
    ApplicationManager.getApplication().invokeAndWait { println("Test template test : badums") }
  }
}

