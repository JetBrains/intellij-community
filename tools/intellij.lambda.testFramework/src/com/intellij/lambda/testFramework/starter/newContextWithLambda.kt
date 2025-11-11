package com.intellij.lambda.testFramework.starter

import com.intellij.ide.starter.runner.AdditionalModulesForDevBuildServer
import com.intellij.ide.starter.ide.IDERemDevTestContext
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.runner.Starter

fun Starter.newContextWithLambda(testName: String, testCase: TestCase<*>, vararg additionalPluginModules: String): IDETestContext {
  try {
    AdditionalModulesForDevBuildServer.addAdditionalModules(*additionalPluginModules)

    return newTestContainer().newContext(testName = testName, testCase = testCase, preserveSystemDir = false).apply {
      val contextToApplyHeadless = if (this is IDERemDevTestContext) frontendIDEContext else this
      //backend can't be started in headless mode, would fail
      contextToApplyHeadless.applyVMOptionsPatch {
        inHeadlessMode()
      }
    }
  }
  finally {
    AdditionalModulesForDevBuildServer.removeAdditionalModules(*additionalPluginModules)
  }
}