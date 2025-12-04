package com.intellij.lambda.testFramework.starter

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.runner.IDERunContext

data class IdeStartConfig(
  val testCase: TestCase<*> = UltimateTestCases.JpsEmptyProject,
  val configureTestContext: (IDETestContext.() -> Unit) = defaultTestContextConfig,
  val configureRunContext: (IDERunContext.() -> Unit) = defaultRunContextConfig,
) {
  companion object {
    private val defaultTestContextConfig: (IDETestContext.() -> Unit) = {}
    private val defaultRunContextConfig: (IDERunContext.() -> Unit) = {}

    val default: IdeStartConfig = IdeStartConfig()

    var current: IdeStartConfig = default
  }
}