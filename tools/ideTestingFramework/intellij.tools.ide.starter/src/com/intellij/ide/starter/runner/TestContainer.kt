package com.intellij.ide.starter.runner

import com.intellij.ide.starter.bus.EventState
import com.intellij.ide.starter.bus.StarterBus
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.*
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.plugins.PluginInstalledState
import com.intellij.ide.starter.utils.catchAll
import com.intellij.ide.starter.utils.logOutput
import org.kodein.di.direct
import org.kodein.di.factory
import org.kodein.di.instance
import java.io.Closeable
import kotlin.io.path.div

/**
 * [ciServer] - use [NoCIServer] for only local run. Otherwise - pass implementation of CIServer
 */
interface TestContainer<T> : Closeable {
  val ciServer: CIServer
  var useLatestDownloadedIdeBuild: Boolean
  var testContext: IDETestContext
  val setupHooks: MutableList<IDETestContext.() -> IDETestContext>

  override fun close() {
    catchAll { testContext.paths.close() }

    logOutput("TestContainer $this disposed")
  }

  /**
   * Allows to apply the common configuration to all created IDETestContext instances
   */
  fun withSetupHook(hook: IDETestContext.() -> IDETestContext): T = apply {
    setupHooks += hook
  } as T

  /**
   * Makes the test use the latest available locally IDE build for testing.
   */
  fun useLatestDownloadedIdeBuild(): T = apply {
    assert(!ciServer.isBuildRunningOnCI)
    useLatestDownloadedIdeBuild = true
  } as T

  /**
   * @return <Build Number, InstalledIde>
   */
  fun resolveIDE(ideInfo: IdeInfo): Pair<String, InstalledIde> {
    return di.direct.factory<IdeInfo, IdeInstallator>().invoke(ideInfo).install(ideInfo)
  }

  fun installPerformanceTestingPluginIfMissing(context: IDETestContext) {
    val performancePluginId = "com.jetbrains.performancePlugin"

    context.pluginConfigurator.apply {
      val pluginState = getPluginInstalledState(performancePluginId)
      if (pluginState != PluginInstalledState.INSTALLED && pluginState != PluginInstalledState.BUNDLED_TO_IDE)
        setupPluginFromPluginManager(performancePluginId, ide = context.ide)
    }
  }

  /** Starting point to run your test */
  fun initializeTestContext(testName: String, testCase: TestCase): IDETestContext {
    logOutput("Resolving IDE build for $testName...")
    val (buildNumber, ide) = resolveIDE(testCase.ideInfo)

    require(ide.productCode == testCase.ideInfo.productCode) { "Product code of $ide must be the same as for $testCase" }

    val testDirectory = (di.direct.instance<GlobalPaths>().testsDirectory / "${testCase.ideInfo.productCode}-$buildNumber") / testName

    val paths = IDEDataPaths.createPaths(testName, testDirectory, testCase.useInMemoryFileSystem)
    logOutput("Using IDE paths for '$testName': $paths")
    logOutput("IDE to run for '$testName': $ide")

    val projectHome = testCase.projectInfo?.downloadAndUnpackProject()
    testContext = IDETestContext(paths, ide, testCase, testName, projectHome, patchVMOptions = { this }, ciServer = ciServer)

    testContext = when (testCase.ideInfo == IdeProductProvider.AI) {
      true -> testContext
        .addVMOptionsPatch {
          overrideDirectories(paths)
            .withEnv("STUDIO_VM_OPTIONS", ide.patchedVMOptionsFile.toString())
        }
      false -> testContext
        .disableInstantIdeShutdown()
        .disableFusSendingOnIdeClose()
        .disableLinuxNativeMenuForce()
        .withGtk2OnLinux()
        .disableGitLogIndexing()
        .enableSlowOperationsInEdtInTests()
        .collectOpenTelemetry()
        .enableAsyncProfiler()
        .addVMOptionsPatch {
          overrideDirectories(paths)
        }
    }

    val contextWithAppliedHooks = setupHooks
      .fold(testContext.updateGeneralSettings()) { acc, hook -> acc.hook() }
      .apply { installPerformanceTestingPluginIfMissing(this) }

    StarterBus.post(TestContextInitializedEvent(EventState.AFTER, contextWithAppliedHooks))

    return contextWithAppliedHooks
  }
}