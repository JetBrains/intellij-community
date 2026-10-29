package com.intellij.lambda.testFramework.utils

import com.intellij.tools.ide.util.common.logOutput

object LambdaTestPluginHolder {
  enum class LoadingInSplitMode {
    OnlyFrontend,
    OnlyBackend,
    Monolith,
    All,
  }

  data class AdditionalLambdaPlugin(
    val devBuildModuleId: String,
    val pluginDirName: String,
    val splitMode: LoadingInSplitMode,
  )

  data class LambdaTestSetup(
    val testModuleId: String?,
    private val additionalLambdaPlugins: List<AdditionalLambdaPlugin>,
  ) {
    fun additionalDevBuildModuleIds(vararg loadingMode: LoadingInSplitMode): List<String> =
      getAdditionalPlugins(*loadingMode).map { it.devBuildModuleId }

    fun additionalPluginDirNames(vararg loadingMode: LoadingInSplitMode): List<String> =
      getAdditionalPlugins(*loadingMode).map { it.pluginDirName }

    private fun getAdditionalPlugins(vararg loadingMode: LoadingInSplitMode): List<AdditionalLambdaPlugin> =
      additionalLambdaPlugins.filter { it.splitMode in loadingMode }

    internal fun additionalDevBuildPlugins(): List<AdditionalLambdaPlugin> = additionalLambdaPlugins
  }

  private val defaultLambdaPlugin: AdditionalLambdaPlugin = AdditionalLambdaPlugin(
    devBuildModuleId = "intellij.lambda.test.plugin",
    pluginDirName = "lambda-test",
    splitMode = LoadingInSplitMode.All,
  )

  /**
   * The plugins a lambda IDE launches with, for the test class that asked for it.
   *
   * The default resolution, and the one every product uses that has not declared a setup of its own. A product
   * declares its own by overriding `LambdaReusedSessionStrategy.lambdaTestSetup` and naming that strategy in its
   * `@ReusedRun`, which is what makes the declaration part of the session that reads it rather than process-wide
   * state a later host in the same JVM can inherit. That override is the only way in for a product whose test
   * classes are generated and therefore cannot be named below.
   *
   * The table below is knowledge of ultimate products living in community code, which is the wrong direction and
   * is known debt: the entries belong with the products, expressed as their own strategies. Moving them is a
   * migration across nine product test modules, each of which launches a real IDE and none of which this change
   * can verify, so it is deliberately not done here -- but a *new* product should override the strategy rather
   * than add a line here, and every line removed from here is progress.
   *
   * The fall-through is a real configuration, not a failure: a test whose in-IDE lambdas only touch platform
   * classes needs no plugin of its own. It cannot be told apart from a test that needed one and was never
   * declared, which is why the resolved setup is logged -- an unresolved class inside the IDE is then one grep
   * away from the setup its launch was given.
   */
  fun setupFor(testClass: Class<*>): LambdaTestSetup {
    val className = testClass.name
    return setupForClassName(className).also { setup ->
      logOutput("Lambda test setup for $className: test module ${setup.testModuleId ?: "none"}, " +
                "plugins ${setup.additionalDevBuildPlugins().joinToString { it.devBuildModuleId }}")
    }
  }

  private fun setupForClassName(className: String): LambdaTestSetup {
    return when {
      className.startsWith("com.intellij.lambda.sampleTestsWithFixtures.") ->
        allModesPlugin("intellij.lambda.sampleTestsWithFixtures")

      className.startsWith("com.intellij.lambda.sampleTestsWithPlugin.") ->
        allModesPlugin("intellij.lambda.sampleTestsWithPlugin")

      className.startsWith("com.intellij.platform.daemon.lambdaTests.") ->
        allModesPlugin("intellij.platform.daemon.lambdaTests")

      className.startsWith("com.intellij.lambdaDebuggerTests.java.") ->
        allModesPlugin("intellij.debugger.lambdaDebuggerTests.java")

      className.startsWith("com.intellij.figma.lambdaTests.") ->
        allModesPlugin("intellij.figma.lambdaTests")

      className.startsWith("com.jetbrains.rdct.lambdaTestsUi.") ->
        allModesPlugin("intellij.rdct.lambdaTestsUi")

      className.startsWith("com.jetbrains.rdct.lambdaTests.") ->
        modeSplitPlugins("intellij.rdct.lambdaTests")

      className.startsWith("com.jetbrains.webstorm.lambda.") ->
        allModesPlugin("intellij.webstorm.lambdaTests")

      className.startsWith("com.jetbrains.php.frontend.split.lambda.") ->
        allModesPlugin("intellij.php.frontend.split.lambdaTests")

      else ->
        LambdaTestSetup(
          testModuleId = null,
          additionalLambdaPlugins = listOf(defaultLambdaPlugin),
        )
    }
  }

  /**
   * Sets up a single test plugin that loads in **all** modes (monolith, frontend-split,
   * backend-split). Use this when the same content modules are needed regardless of mode.
   *
   * Derives the dev-build plugin module and directory name from [mainTestModuleId] using the same naming
   * convention as the wrapper generator: `<base>.plugin` for the module, and the module with `intellij.` and `.plugin`
   * stripped and dots replaced by dashes for the plugin directory.
   *
   * Public because a product with generated test classes cannot be listed in [setupFor] and builds its setup
   * itself; [additionalPlugins] is where its own plugin goes, in the same list as the lambda test plugins
   * because the dev build caches an installation per set of additional modules.
   */
  fun allModesPlugin(
    mainTestModuleId: String,
    pluginDirName: String = mainTestModuleId.removePrefix("intellij.").replace('.', '-'),
    additionalPlugins: List<AdditionalLambdaPlugin> = emptyList(),
  ): LambdaTestSetup {
    return LambdaTestSetup(
      testModuleId = mainTestModuleId,
      additionalLambdaPlugins = listOf(
        AdditionalLambdaPlugin(
          devBuildModuleId = "$mainTestModuleId.plugin",
          pluginDirName = pluginDirName,
          splitMode = LoadingInSplitMode.All,
        ),
        defaultLambdaPlugin,
      ) + additionalPlugins,
    )
  }

  /**
   * Sets up mode-specific wrapper plugins generated by lambda test Product DSL specs. Derives
   * three [AdditionalLambdaPlugin] entries — monolith, backend-split, frontend-split — from the
   * naming convention used by the generated wrappers:
   * - module name: `<wrapperModuleBaseName>.<modeSuffix>`
   * - plugin directory: module name with `intellij.` and `.plugin` stripped and dots replaced by dashes
   */
  private fun modeSplitPlugins(
    mainTestModuleId: String,
    wrapperModuleBaseName: String = mainTestModuleId.removeSuffix("._test"),
    pluginDirNameBase: String = wrapperModuleBaseName.removePrefix("intellij.").replace('.', '-'),
  ): LambdaTestSetup {
    return LambdaTestSetup(
      testModuleId = mainTestModuleId,
      additionalLambdaPlugins = listOf(defaultLambdaPlugin) + MODE_SPLIT_SUFFIXES.map { (suffix, loadingMode) ->
        AdditionalLambdaPlugin(
          devBuildModuleId = "$wrapperModuleBaseName.$suffix",
          pluginDirName = "$pluginDirNameBase-${suffix.removeSuffix(".plugin").replace('.', '-')}",
          splitMode = loadingMode,
        )
      },
    )
  }

  private val MODE_SPLIT_SUFFIXES = listOf(
    "monolith.plugin" to LoadingInSplitMode.Monolith,
    "backend.split.plugin" to LoadingInSplitMode.OnlyBackend,
    "frontend.split.plugin" to LoadingInSplitMode.OnlyFrontend,
  )
}
