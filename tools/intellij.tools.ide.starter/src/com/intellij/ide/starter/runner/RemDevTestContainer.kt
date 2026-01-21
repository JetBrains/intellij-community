package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ide.IDERemDevTestContext
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.frontendTestCase
import com.intellij.ide.starter.models.TestCase
import com.intellij.tools.ide.util.common.logOutput

class RemDevTestContainer : TestContainer<RemDevTestContainer> {
  override fun newContext(testName: String, testCase: TestCase<*>, preserveSystemDir: Boolean): IDETestContext {
    val container = TestContainer.newInstance<TestContainerImpl>()

    logOutput("Creating backend context")
    val backendContext = container.newContext(testName, testCase, preserveSystemDir)

    logOutput("Creating frontend context")
    val frontendTestCase = backendContext.frontendTestCase
    val frontendContext = container.createFromExisting(testName, frontendTestCase, preserveSystemDir, backendContext)

    return IDERemDevTestContext.from(backendContext, frontendContext)
  }
}