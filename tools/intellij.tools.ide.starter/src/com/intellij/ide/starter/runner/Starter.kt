package com.intellij.ide.starter.runner

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.TestCase
import com.intellij.tools.ide.starter.bus.EventsBus
import org.kodein.di.provider

object Starter {
  fun newTestContainer(vararg setupHooks: IDETestContext.() -> Unit): TestContainer {
    val testContainer: () -> TestContainer by di.provider()
    return (testContainer.invoke().also { testContainer ->
      if (setupHooks.isNotEmpty()) {
        EventsBus.subscribeForTestContextInitializedEvent("setupHooks", testContainer) { event ->
          setupHooks.forEach { it.invoke(event.testContext) }
        }
      }
    })
  }

  fun newContext(testName: String, testCase: TestCase<*>, preserveSystemDir: Boolean = false): IDETestContext =
    newTestContainer().newContext(testName = testName, testCase = testCase, preserveSystemDir = preserveSystemDir)
}