package com.intellij.lambda.testFramework.starter

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.asRemDevContext
import com.intellij.ide.starter.ide.isRemDevContext
import com.intellij.ide.starter.runner.AdditionalModulesForDevBuildServer
import com.intellij.ide.starter.runner.Starter
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder

fun Starter.newContextWithLambda(testName: String, config: IdeStartConfig): IDETestContext {
  try {
    AdditionalModulesForDevBuildServer.addAdditionalModules(*LambdaTestPluginHolder.additionalPluginIds().toTypedArray())

    return newTestContainer().newContext(testName = testName, testCase = config.testCase, preserveSystemDir = false).apply {
      val contextToApplyHeadless = if (this.isRemDevContext()) this.asRemDevContext().frontendIDEContext else this
      //backend can't be started in headless mode, would fail
      contextToApplyHeadless.applyVMOptionsPatch {
        inHeadlessMode()
      }
      config.configureTestContext(this)
    }
  }
  finally {
    AdditionalModulesForDevBuildServer.removeAdditionalModules(*LambdaTestPluginHolder.additionalPluginIds().toTypedArray())
  }
}