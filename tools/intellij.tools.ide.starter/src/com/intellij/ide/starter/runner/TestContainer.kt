package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.plugins.PluginInstalledState
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.runner.events.TestContextInitializationStartedEvent
import com.intellij.ide.starter.telemetry.computeWithSpan
import com.intellij.ide.starter.utils.PortUtil
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.div

typealias IDEDataPathsProvider = (testName: String, testDirectory: Path, useInMemoryFileSystem: Boolean) -> IDEDataPaths

interface TestContainer {
  companion object {
    init {
      EventsBus.subscribe(TestContainer::javaClass) { _: TestContextInitializedEvent ->
        logOutput("Starter configuration storage: ${ConfigurationStorage.instance().getAll()}")
      }
    }

    suspend fun resolveIDE(ideInfo: IdeInfo): Pair<String, InstalledIde> {
      return ideInfo.getInstaller(ideInfo).install(ideInfo)
    }

    fun installPerformanceTestingPluginIfMissing(context: IDETestContext) {
      val performancePluginId = "com.jetbrains.performancePlugin"

      context.pluginConfigurator.apply {
        val pluginState = getPluginInstalledState(performancePluginId)
        if (pluginState != PluginInstalledState.INSTALLED && pluginState != PluginInstalledState.BUNDLED_TO_IDE)
          installPluginFromPluginManager(performancePluginId, ide = context.ide)
      }
    }

    fun applyDefaultVMOptions(context: IDETestContext): IDETestContext {
      return when (context.testCase.ideInfo == IdeProductProvider.AI) {
        true -> context
          .addProjectToTrustedLocations()
          .disableFusSendingOnIdeClose()
          .disableReportingStatisticsToProduction()
          .disableReportingStatisticToJetStat()
          .disableMigrationNotification()
          .applyVMOptionsPatch {
            overrideDirectories(context.paths)
            withEnv("STUDIO_VM_OPTIONS", context.ide.patchedVMOptionsFile.toString())
          }
        false -> context
          .disableLoadShellEnv()
          .disableInstantIdeShutdown()
          .disableFusSendingOnIdeClose()
          .disableLinuxNativeMenuForce()
          .withGtk2OnLinux()
          .skipGitLogIndexing()
          .enableSlowOperationsInEdtInTests()
          .enableAsyncProfiler()
          .applyVMOptionsPatch {
            overrideDirectories(context.paths)
            if (isUnderDebug()) {
              debug(PortUtil.getAvailablePort(proposedPort = 5010), suspend = false)
            }
          }
          .disableMinimap()
          .addProjectToTrustedLocations()
          .disableReportingStatisticsToProduction()
          .disableReportingStatisticToJetStat()
          .disableMigrationNotification()
          .setKotestMaxCollectionEnumerateSize()
          .acceptNonTrustedCertificates()
          .apply {
            if (!CIServer.instance.isBuildRunningOnCI) {
              //this option is affecting only local launches
              disableTraceDataSharingNotification()
            }
          }
      }
    }
  }

  /**
   * @return <Build Number, InstalledIde>
   */
  suspend fun resolveIDE(ideInfo: IdeInfo): Pair<String, InstalledIde> {
    return TestContainer.resolveIDE(ideInfo)
  }

  fun installPerformanceTestingPluginIfMissing(context: IDETestContext) {
    TestContainer.installPerformanceTestingPluginIfMissing(context)
  }

  fun newContext(testName: String, testCase: TestCase<*>, preserveSystemDir: Boolean = false): IDETestContext {
    return newContext(
      testName = testName,
      testCase = testCase,
      preserveSystemDir = preserveSystemDir,
      projectHome = computeWithSpan("download and unpack project") { testCase.projectInfo.downloadAndUnpackProject() },
    )
  }

  /**
   * Creates a context from the `existingContext` one. The difference from the [newContext] method is that the project is not set up, but
   * re-used from the `existingContext`
   */
  fun createFromExisting(testName: String, testCase: TestCase<*>, preserveSystemDir: Boolean = false, existingContext: IDETestContext): IDETestContext =
    newContext(testName, testCase, preserveSystemDir, if (testCase.projectInfo is NoProject) null else existingContext.resolvedProjectHome)

  /**
   * Starting point to run your test.
   * @param preserveSystemDir Only for local runs when you know that having "dirty" system folder is ok and want to speed up test execution.
   * @param baseContext - optional base context. If passed, some set up steps for the new context are omitted and we are re-using base context information.
   *                      For example - project unpacking
   */
  fun newContext(
    testName: String, testCase: TestCase<*>, preserveSystemDir: Boolean = false, projectHome: Path?,
    ideDataPathsProvider: IDEDataPathsProvider = { testName, testDirectory, useInMemoryFileSystem ->
      IDEDataPaths.createPaths<IDEDataPaths>(testName, testDirectory, useInMemoryFileSystem)
    },
  ): IDETestContext {
    EventsBus.postAndWaitProcessing(TestContextInitializationStartedEvent())
    logOutput("Resolving IDE build for $testName...")
    val (buildNumber, ide) = @Suppress("SSBasedInspection")
    (runBlocking(Dispatchers.Default) {
      computeWithSpan("resolving IDE") {
        resolveIDE(testCase.ideInfo)
      }
    })

    require(ide.productCode == testCase.ideInfo.productCode) { "Product code ${ide.productCode} must be the same as ${testCase.ideInfo.productCode}. IDE: $ide . TestCase: $testCase" }

    val testDirectory = run {
      val commonPath = (GlobalPaths.instance.testsDirectory / "${testCase.ideInfo.productCode}-$buildNumber") / testName
      if (testCase.ideInfo.isFrontend) {
        commonPath / "frontend"
      }
      else {
        commonPath
      }
    }

    val paths = ideDataPathsProvider(testName, testDirectory, testCase.useInMemoryFileSystem)
    logOutput("Using IDE paths for '$testName': $paths")
    logOutput("IDE to run for '$testName': $ide")

    var testContext = IDETestContext(paths, ide, testCase, testName, projectHome, preserveSystemDir = preserveSystemDir)
    testContext.wipeSystemDir()

    testContext = applyDefaultVMOptions(testContext)

    val preparedContext = testContext
      .updateGeneralSettings()
      .apply { installPerformanceTestingPluginIfMissing(this) }

    testCase.projectInfo.configureProjectBeforeUse.invoke(preparedContext)

    EventsBus.postAndWaitProcessing(TestContextInitializedEvent(this, preparedContext))

    return preparedContext
  }
}
