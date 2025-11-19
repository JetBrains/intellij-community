package com.intellij.lambda.sampleTestsWithPlugin.util

import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder.defaultLambdaPlugin
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

class SetLambdaPluginCallback : BeforeAllCallback, AfterAllCallback {
  val localCustomLambdaPlugin = LambdaTestPluginHolder.AdditionalLambdaPlugin("intellij.lambda.sampleTestsWithPlugin._test",
                                                                              "intellij.lambda.sampleTestsWithPlugin.plugin",
                                                                              "lambda-sampleTestsWithPlugin-plugin")

  override fun beforeAll(context: ExtensionContext) {
    LambdaTestPluginHolder.setupAdditionalLambdaPlugins(localCustomLambdaPlugin.moduleID, listOf(localCustomLambdaPlugin, defaultLambdaPlugin))
  }
  override fun afterAll(context: ExtensionContext) {
    LambdaTestPluginHolder.cleanUpAdditionalLambdaPlugin()
  }
}