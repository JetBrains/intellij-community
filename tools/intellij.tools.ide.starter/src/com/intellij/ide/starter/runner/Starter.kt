package com.intellij.ide.starter.runner

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.TestCase
import org.kodein.di.provider

object Starter {
  fun newTestContainer(): TestContainer<*> {
    val testContainer: () -> TestContainer<*> by di.provider()
    return testContainer.invoke()
  }

  fun newContext(testName: String, testCase: TestCase<*>, preserveSystemDir: Boolean = false): IDETestContext =
    newTestContainer().newContext(testName = testName, testCase = testCase, preserveSystemDir = preserveSystemDir)
}