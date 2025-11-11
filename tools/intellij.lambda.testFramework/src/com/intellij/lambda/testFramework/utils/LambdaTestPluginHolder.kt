package com.intellij.lambda.testFramework.utils

object LambdaTestPluginHolder {
  private data class AdditionalLambdaPlugin(val moduleID: String, val pluginId: String, val pluginDirName: String)

  private var additionalLambdaPlugin: AdditionalLambdaPlugin? = null
  private val defaultLambdaPlugin: AdditionalLambdaPlugin = AdditionalLambdaPlugin("intellij.lambda.testFramework",
                                                                                   "intellij.lambda.test.plugin",
                                                                                   "lambda-test-plugin")

  fun testModuleId(): String = additionalLambdaPlugin?.moduleID ?: defaultLambdaPlugin.moduleID
  fun additionalPluginIds(): List<String> = listOfNotNull(defaultLambdaPlugin.pluginId, additionalLambdaPlugin?.pluginId)
  fun additionalPluginDirNames(): List<String> = listOfNotNull(defaultLambdaPlugin.pluginDirName, additionalLambdaPlugin?.pluginDirName)

  fun setupAdditionalLambdaPlugin(moduleID: String, pluginId: String, pluginDirName: String) {
    additionalLambdaPlugin = AdditionalLambdaPlugin(moduleID, pluginId, pluginDirName)
  }
  fun cleanUpAdditionalLambdaPlugin() {
    additionalLambdaPlugin = null
  }
}