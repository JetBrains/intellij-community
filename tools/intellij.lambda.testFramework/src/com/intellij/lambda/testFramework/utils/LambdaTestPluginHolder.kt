package com.intellij.lambda.testFramework.utils

object LambdaTestPluginHolder {
  data class AdditionalLambdaPlugin(val moduleID: String, val pluginId: String, val pluginDirName: String)

  private var additionalLambdaPlugins: List<AdditionalLambdaPlugin> = emptyList()
  private var mainTestModuleId: String? = null
  val defaultLambdaPlugin: AdditionalLambdaPlugin = AdditionalLambdaPlugin("intellij.lambda.testFramework",
                                                                           "intellij.lambda.test.plugin",
                                                                           "lambda-test-plugin")


  fun testModuleId(): String? = mainTestModuleId
  fun additionalPluginIds(): List<String> = additionalLambdaPlugins.map { it.pluginId }
  fun additionalPluginDirNames(): List<String> = additionalLambdaPlugins.map { it.pluginDirName }

  fun setupAdditionalLambdaPlugins(mainTestModuleId: String, additionalLambdaPlugins: List<AdditionalLambdaPlugin>) {
    this.mainTestModuleId = mainTestModuleId
    this.additionalLambdaPlugins = additionalLambdaPlugins
  }

  fun cleanUpAdditionalLambdaPlugin() {
    this.mainTestModuleId = null
    this.additionalLambdaPlugins = emptyList()
  }
}