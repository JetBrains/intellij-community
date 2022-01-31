package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.downloadAndroidStudio
import com.intellij.ide.starter.ide.*
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeProduct
import com.intellij.ide.starter.models.StartUpPerformanceCase
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.logOutput
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.Closeable
import java.io.File
import java.nio.file.Path
import kotlin.io.path.div

/**
 * [ciServer] - use [NoCIServer] for only local run. Otherwise - pass implementation of CIServer
 */
interface TestContainer<T> : Closeable {
  val ciServer: CIServer
  var useLatestDownloadedIdeBuild: Boolean
  val allContexts: MutableList<IDETestContext>
  val setupHooks: MutableList<IDETestContext.() -> IDETestContext>

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

  fun resolveIDE(ideInfo: IdeInfo) = resolveExternallyBuiltIDE(ideInfo)

  /**
   * Download IDE installer, if necessary
   * @return BuildNumber / InstalledIDE
   */
  fun resolveExternallyBuiltIDE(ideInfo: IdeInfo): Pair<String, InstalledIDE> {
    val installDir: Path
    val installerFile: File
    var buildNumber = ""

    if (ideInfo == IdeProduct.AI.ideInfo) {
      downloadAndroidStudio().also {
        installDir = it.first
        installerFile = it.second
      }
    }
    else {
      val ideInstaller = IdeInstaller.resolveIdeInstaller(ideInfo, useLatestDownloadedIdeBuild, ciServer)
      buildNumber = ideInstaller.buildNumber
      installDir = di.direct.instance<GlobalPaths>().getCacheDirectoryFor("builds") / "${ideInstaller.ideInfo.productCode}-$buildNumber"
      if (buildNumber == "SNAPSHOT") {
        logOutput("Cleaning up SNAPSHOT IDE installation $installDir")
        installDir.toFile().deleteRecursively()
      }
      installerFile = ideInstaller.installerFile.toFile()
    }

    extractIDEIfNeeded(installerFile, installDir.toFile())

    val installationPath = when (ideInfo == IdeProduct.AI.ideInfo && !SystemInfo.isMac) {
      true -> installDir.resolve("android-studio")
      false -> installDir
    }

    val ide = resolveInstalledIDE(installationPath.toFile(), ideInfo.executableFileName)

    if (ideInfo == IdeProduct.AI.ideInfo) buildNumber = ide.build

    return Pair(buildNumber, ide)
  }

  /** Starting point to run your test */
  fun initializeTestRunner(testName: String, testCase: StartUpPerformanceCase): IDETestContext {
    check(allContexts.none { it.testName == testName }) { "Test $testName is already initialized. Use another name." }
    logOutput("Resolving IDE build for $testName...")

    val (buildNumber, ide) = resolveIDE(testCase.ideInfo)

    require(ide.productCode == testCase.ideInfo.productCode) { "Product code of $ide must be the same as for $testCase" }

    val build = when (testCase.ideInfo == IdeProduct.AI.ideInfo) {
      true -> Regex("\\d*.\\d*.\\d*").find(buildNumber)?.groups?.first()?.value
      false -> buildNumber
    }
    val testDirectory = (di.direct.instance<GlobalPaths>().testsDirectory / "${testCase.ideInfo.productCode}-$build") / testName

    val paths = IDEDataPaths.createPaths(testName, testDirectory, testCase.useInMemoryFileSystem)
    logOutput("Using IDE paths for $testName: $paths")
    logOutput("IDE to run for $testName: $ide")

    val projectHome = testCase.projectInfo?.resolveProjectHome()
    val context = IDETestContext(paths, ide, testCase, testName, projectHome, patchVMOptions = { this }, ciServer = ciServer)
    allContexts += context

    val baseContext = when (testCase.ideInfo == IdeProduct.AI.ideInfo) {
      true -> context
        .addVMOptionsPatch {
          overrideDirectories(paths)
            .withEnv("STUDIO_VM_OPTIONS", ide.patchedVMOptionsFile.toString())
        }
      false -> context
        .disableInstantIdeShutdown()
        .disableFusSendingOnIdeClose()
        .disableJcef()
        .disableLinuxNativeMenuForce()
        .withGtk2OnLinux()
        .disableGitLogIndexing()
        .enableSlowOperationsInEdtInTests()
        .addVMOptionsPatch {
          overrideDirectories(paths)
        }
    }
    return setupHooks.fold(baseContext.updateGeneralSettings()) { acc, hook -> acc.hook() }
  }
}