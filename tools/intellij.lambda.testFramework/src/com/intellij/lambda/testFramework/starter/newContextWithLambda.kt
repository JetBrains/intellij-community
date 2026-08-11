package com.intellij.lambda.testFramework.starter

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.asRemDevContext
import com.intellij.ide.starter.ide.isRemDevContext
import com.intellij.ide.starter.runner.AdditionalModulesForDevBuildServer
import com.intellij.ide.starter.runner.Starter
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder.LoadingInSplitMode.All
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder.LoadingInSplitMode.Monolith
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder.LoadingInSplitMode.OnlyBackend
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder.LoadingInSplitMode.OnlyFrontend

fun Starter.newContextWithLambda(testName: String, config: IdeStartConfig): IDETestContext {
  try {
    AdditionalModulesForDevBuildServer.addAdditionalModules(
      *LambdaTestPluginHolder.additionalDevBuildModuleIds(OnlyFrontend).toTypedArray(),
      target = AdditionalModulesForDevBuildServer.IdeTarget.FRONTEND
    )
    AdditionalModulesForDevBuildServer.addAdditionalModules(
      *LambdaTestPluginHolder.additionalDevBuildModuleIds(OnlyBackend).toTypedArray(),
      target = AdditionalModulesForDevBuildServer.IdeTarget.BACKEND
    )
    AdditionalModulesForDevBuildServer.addAdditionalModules(
      *LambdaTestPluginHolder.additionalDevBuildModuleIds(All).toTypedArray(),
      target = AdditionalModulesForDevBuildServer.IdeTarget.ANY
    )
    AdditionalModulesForDevBuildServer.addAdditionalModules(
      *LambdaTestPluginHolder.additionalDevBuildModuleIds(Monolith).toTypedArray(),
      target = AdditionalModulesForDevBuildServer.IdeTarget.MONOLITH
    )

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
    AdditionalModulesForDevBuildServer.removeAdditionalModules(
      *LambdaTestPluginHolder.additionalDevBuildModuleIds(OnlyFrontend).toTypedArray(),
      target = AdditionalModulesForDevBuildServer.IdeTarget.FRONTEND
    )
    AdditionalModulesForDevBuildServer.removeAdditionalModules(
      *LambdaTestPluginHolder.additionalDevBuildModuleIds(OnlyBackend).toTypedArray(),
      target = AdditionalModulesForDevBuildServer.IdeTarget.BACKEND
    )
    AdditionalModulesForDevBuildServer.removeAdditionalModules(
      *LambdaTestPluginHolder.additionalDevBuildModuleIds(All).toTypedArray(),
      target = AdditionalModulesForDevBuildServer.IdeTarget.ANY
    )
    AdditionalModulesForDevBuildServer.removeAdditionalModules(
      *LambdaTestPluginHolder.additionalDevBuildModuleIds(Monolith).toTypedArray(),
      target = AdditionalModulesForDevBuildServer.IdeTarget.MONOLITH
    )
  }
}
