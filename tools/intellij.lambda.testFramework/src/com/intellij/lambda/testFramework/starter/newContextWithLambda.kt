package com.intellij.lambda.testFramework.starter

import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import com.intellij.ide.starter.ide.IDERemDevTestContext
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.asRemDevContext
import com.intellij.ide.starter.ide.isRemDevContext
import com.intellij.ide.starter.runner.AdditionalModulesForDevBuildServer
import com.intellij.ide.starter.runner.AdditionalModulesForDevBuildServer.IdeTarget
import com.intellij.ide.starter.runner.IDEDataPathsProvider
import com.intellij.ide.starter.runner.Starter
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder.LambdaTestSetup
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder.LoadingInSplitMode.All
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder.LoadingInSplitMode.Monolith
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder.LoadingInSplitMode.OnlyBackend
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder.LoadingInSplitMode.OnlyFrontend
import com.intellij.util.io.copyRecursively
import com.intellij.util.io.createDirectories
import org.jetbrains.annotations.ApiStatus
import kotlin.io.path.exists

fun Starter.newContextWithLambda(
  testName: String,
  setup: LambdaTestSetup,
  projectConfiguration: CachedProjectConfiguration,
  ideDataPathsProvider: IDEDataPathsProvider,
): IDETestContext {
  try {
    addLambdaTestPluginsToDevBuild(setup)

    val testContainer = newTestContainer()
    val context = testContainer.newContext(
      testName = testName,
      testCase = projectConfiguration.testCase,
      preserveSystemDir = false,
      projectHome = projectConfiguration.testCase.projectInfo.downloadAndUnpackProject(),
      ideDataPathsProvider = ideDataPathsProvider,
    )
    val contextToApplyHeadless = if (context.isRemDevContext()) context.asRemDevContext().frontendIDEContext else context
    //backend can't be started in headless mode, would fail
    contextToApplyHeadless.applyVMOptionsPatch {
      inHeadlessMode()
    }
    val preparedContext = projectConfiguration.setup(context)
    if (preparedContext.isRemDevContext()) {
      preparedContext.asRemDevContext().installFrontendLambdaPlugins(setup)
    }
    return preparedContext
  }
  finally {
    removeLambdaTestPluginsFromDevBuild(setup)
  }
}

/**
 * Copies the frontend lambda test plugins out of the installation into the frontend custom plugins directory.
 *
 * This is a workaround, see [AT-4407](https://youtrack.jetbrains.com/issue/AT-4407). The frontend process reads
 * its bundled plugins from `product-modules.xml` and not from the `plugins` directory of the installation, so it
 * does not see a plugin the dev build put there. A copy into the custom plugins directory is what it does load.
 *
 * [addLambdaTestPluginsToDevBuild] makes the dev build bundle the plugins, so the installation carries them only
 * when the run resolved a dev build from code. A missing plugin means the run got an installer build instead, and
 * no copy can repair that: the plugin holds this repository's own test code.
 */
private fun IDERemDevTestContext.installFrontendLambdaPlugins(setup: LambdaTestSetup) {
  val frontendCustomPluginsDir = frontendIDEContext.paths.pluginsDir
  if (!frontendCustomPluginsDir.exists()) {
    frontendCustomPluginsDir.createDirectories()
  }
  val bundledPluginsDir = frontendIDEContext.ide.installationPath.resolve("plugins")
  setup.additionalPluginDirNames(OnlyFrontend, All).forEach { pluginDirName ->
    val bundledPluginDir = bundledPluginsDir.resolve(pluginDirName)
    check(bundledPluginDir.exists()) {
      "The frontend IDE at ${frontendIDEContext.ide.installationPath} does not bundle the lambda test plugin " +
      "'$pluginDirName'. A lambda test needs a dev build from code, because the plugin holds the test code of this " +
      "repository. Check that the run does not use a downloaded installer build."
    }
    bundledPluginDir.copyRecursively(frontendCustomPluginsDir.resolve(pluginDirName))
  }
}

private val additionalModuleTargets = listOf(
  OnlyFrontend to IdeTarget.FRONTEND,
  OnlyBackend to IdeTarget.BACKEND,
  All to IdeTarget.ANY,
  Monolith to IdeTarget.MONOLITH,
)

private inline fun LambdaTestSetup.forEachAdditionalModuleTarget(action: (List<String>, IdeTarget) -> Unit) {
  additionalModuleTargets.forEach { (loadingMode, target) ->
    action(additionalDevBuildModuleIds(loadingMode), target)
  }
}

/**
 * Asks the dev build for the plugins [setup] declares, for the window in which a context is created.
 *
 * That window is the whole point: the registry is read once, while the installer resolves the IDE
 * (`IdeFromCodeInstaller.install`), and never again at launch time. A host that builds its own
 * [IDETestContext] instead of going through [newContextWithLambda] therefore has to open the same window
 * itself - the lambda channel it sets up afterwards is worthless if the IDE it launches has no lambda test
 * plugin to load the test code with. Such a host gets its [setup] from
 * [LambdaTestPluginHolder.setupFor].
 */
@ApiStatus.Internal
fun addLambdaTestPluginsToDevBuild(setup: LambdaTestSetup) {
  setup.forEachAdditionalModuleTarget { devBuildModuleIds, target ->
    AdditionalModulesForDevBuildServer.addAdditionalModules(*devBuildModuleIds.toTypedArray(), target = target)
  }
}

/**
 * Undoes [addLambdaTestPluginsToDevBuild].
 *
 * [AdditionalModulesForDevBuildServer] is a JVM-global singleton, so a registration left behind is not this
 * context's any more - it silently joins every later dev build in the same JVM.
 */
@ApiStatus.Internal
fun removeLambdaTestPluginsFromDevBuild(setup: LambdaTestSetup) {
  setup.forEachAdditionalModuleTarget { devBuildModuleIds, target ->
    AdditionalModulesForDevBuildServer.removeAdditionalModules(*devBuildModuleIds.toTypedArray(), target = target)
  }
}
