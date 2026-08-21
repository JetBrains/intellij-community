package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeInfoType
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.FrontendIDEDataPaths
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.plugins.PluginInstalledState
import com.intellij.ide.starter.runner.events.TestContextInitializationStartedEvent
import com.intellij.ide.starter.telemetry.computeWithSpan
import com.intellij.ide.starter.utils.PortUtil
import com.intellij.ide.starter.utils.ReportingPathUtils
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.div

typealias IDEDataPathsProvider = (testDirectoryName: String, testDirectory: Path, useInMemoryFileSystem: Boolean) -> IDEDataPaths

internal fun IDEDataPathsProvider.asFrontendDataPathsProvider(): IDEDataPathsProvider = { testDirectoryName, testDirectory, useInMemoryFileSystem ->
  when (val paths = this(testDirectoryName, testDirectory, useInMemoryFileSystem)) {
    is FrontendIDEDataPaths -> paths
    // Converting rather than calling `createPaths` once more: the second call would wipe and re-create `testHome`
    // right after the first one, and both instances would own the very same in-memory root (its path is derived from
    // the test name), so collecting the discarded one could delete the directories of the live one.
    else -> paths.asFrontendDataPaths()
  }
}

interface TestContainer {
  companion object {
    init {
      EventsBus.subscribe(TestContainer::class.java) { _: TestContextInitializedEvent ->
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
      return when (context.testCase.ideInfo.productCode == IdeInfoType.ANDROID_STUDIO.productCode) {
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
            addSystemProperty("ide.default.smooth.caret.enabled", true)
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
   * Starting point to run your test.
   * @param preserveSystemDir Only for local runs when you know that having "dirty" system folder is ok and want to speed up test execution.
   * @param projectHome optional project home. If passed, some setup steps for the new context are omitted and project unpacking is reused.
   */
  fun newContext(
    testName: String, testCase: TestCase<*>, preserveSystemDir: Boolean = false, projectHome: Path?,
    ideDataPathsProvider: IDEDataPathsProvider = { testDirectoryName, testDirectory, useInMemoryFileSystem ->
      IDEDataPaths.createPaths<IDEDataPaths>(testDirectoryName, testDirectory, useInMemoryFileSystem)
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

    require(ide.productCode == testCase.ideInfo.productCode ||
            // some 253 versions(e.g. 253.28294.334) of IC are actually IU, seems like it is due to single distributive (SID-119)
            (ide.productCode == "IU" && testCase.ideInfo.productCode == "IC" && ide.isMajorBuildVersionAtLeast(253))
    ) { "Product code ${ide.productCode} must be the same as ${testCase.ideInfo.productCode}. IDE: $ide . TestCase: $testCase" }

    val testDirectoryName = ReportingPathUtils.testDirectoryName(testName)
    val testDirectory = run {
      val commonPath = (GlobalPaths.instance.testsDirectory / "${testCase.ideInfo.productCode}-$buildNumber") / testDirectoryName
      if (testCase.ideInfo.isFrontend) {
        commonPath / FrontendIDEDataPaths.FRONTEND_DIR_NAME
      }
      else {
        commonPath
      }
    }

    val paths = ideDataPathsProvider(testDirectoryName, testDirectory, testCase.useInMemoryFileSystem)
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
