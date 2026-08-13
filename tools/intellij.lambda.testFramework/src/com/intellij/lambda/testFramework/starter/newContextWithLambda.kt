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
import org.jetbrains.annotations.ApiStatus

fun Starter.newContextWithLambda(testName: String, config: IdeStartConfig): IDETestContext {
  try {
    addLambdaTestPluginsToDevBuild()

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
    removeLambdaTestPluginsFromDevBuild()
  }
}

/**
 * Asks the dev build for the plugins declared in [LambdaTestPluginHolder], for the window in which a context
 * is created.
 *
 * That window is the whole point: the registry is read once, while the installer resolves the IDE
 * (`IdeFromCodeInstaller.install`), and never again at launch time. A host that builds its own
 * [IDETestContext] instead of going through [newContextWithLambda] therefore has to open the same window
 * itself - the lambda channel it sets up afterwards is worthless if the IDE it launches has no lambda test
 * plugin to load the test code with.
 */
@ApiStatus.Internal
fun addLambdaTestPluginsToDevBuild() {
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
}

/**
 * Undoes [addLambdaTestPluginsToDevBuild].
 *
 * [AdditionalModulesForDevBuildServer] is a JVM-global singleton, so a registration left behind is not this
 * context's any more - it silently joins every later dev build in the same JVM.
 */
@ApiStatus.Internal
fun removeLambdaTestPluginsFromDevBuild() {
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
