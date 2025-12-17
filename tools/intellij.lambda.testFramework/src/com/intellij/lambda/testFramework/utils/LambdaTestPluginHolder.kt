package com.intellij.lambda.testFramework.utils

object LambdaTestPluginHolder {
  enum class LoadingInSplitMode {
    OnlyFrontend,
    OnlyBackend,
    Monolith,
    All,
  }

  data class AdditionalLambdaPlugin(
    val moduleID: String,
    val pluginId: String,
    val pluginDirName: String,
    val splitMode: LoadingInSplitMode,
  )

  private var additionalLambdaPlugins: List<AdditionalLambdaPlugin> = emptyList()
  private var mainTestModuleId: String? = null
  val defaultLambdaPlugin: AdditionalLambdaPlugin = AdditionalLambdaPlugin("intellij.lambda.testFramework",
                                                                           "intellij.lambda.test.plugin",
                                                                           "lambda-test-plugin",
                                                                           LoadingInSplitMode.All)


  fun testModuleId(): String? = mainTestModuleId
  fun additionalPluginIds(vararg ideTarget: LoadingInSplitMode): List<String> = getAdditionalPlugins(*ideTarget).map { it.pluginId }
  fun additionalPluginDirNames(vararg ideTarget: LoadingInSplitMode): List<String> =
    getAdditionalPlugins(*ideTarget).map { it.pluginDirName }

  private fun getAdditionalPlugins(vararg ideTarget: LoadingInSplitMode): List<AdditionalLambdaPlugin> =
    additionalLambdaPlugins.filter { it.splitMode in ideTarget || it.splitMode == LoadingInSplitMode.All }

  fun setupAdditionalLambdaPlugins(mainTestModuleId: String, additionalLambdaPlugins: List<AdditionalLambdaPlugin>) {
    this.mainTestModuleId = mainTestModuleId
    this.additionalLambdaPlugins = additionalLambdaPlugins
  }

  fun cleanUpAdditionalLambdaPlugin() {
    this.mainTestModuleId = null
    this.additionalLambdaPlugins = emptyList()
  }
}