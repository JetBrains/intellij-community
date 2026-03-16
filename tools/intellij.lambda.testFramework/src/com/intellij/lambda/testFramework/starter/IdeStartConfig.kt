package com.intellij.lambda.testFramework.starter

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.ide.starter.runner.IDERunContext

data class IdeStartConfig(
  val testCase: TestCase<*> = (object : TestCaseTemplate(IdeProductProvider.IU) {}).withProject(NoProject),
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