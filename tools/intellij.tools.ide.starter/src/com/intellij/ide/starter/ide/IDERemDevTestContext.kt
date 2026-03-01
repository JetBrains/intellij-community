package com.intellij.ide.starter.ide

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.useInstaller
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.profiler.ProfilerType
import com.intellij.ide.starter.project.ProjectInfoSpec
import com.intellij.ide.starter.report.publisher.ReportPublisher
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.PlatformUtils
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Path

fun IDETestContext.isRemDevContext() = this is IDERemDevTestContext
fun IDETestContext.asRemDevContext(): IDERemDevTestContext = this as IDERemDevTestContext

/** Returns result or null if the context isn't of RemDev type */
inline fun <T> IDETestContext.onRemDevContext(action: IDERemDevTestContext.() -> T): T? {
  val remDevContext = if (this.isRemDevContext()) this.asRemDevContext() else return null

  return action(remDevContext)
}

class IDERemDevTestContext private constructor(
  paths: IDEDataPaths,
  ide: InstalledIde,
  testCase: TestCase<*>,
  testName: String,
  _resolvedProjectHome: Path?,
  profilerType: ProfilerType = ProfilerType.NONE,
  publishers: List<ReportPublisher> = di.direct.instance(),
  isReportPublishingEnabled: Boolean = true,
  preserveSystemDir: Boolean = false,
  val frontendIDEContext: IDETestContext,
) : IDETestContext(
  paths = paths,
  ide = ide,
  testCase = testCase,
  testName = testName,
  _resolvedProjectHome = _resolvedProjectHome,
  profilerType = profilerType,
  publishers = publishers,
  isReportPublishingEnabled = isReportPublishingEnabled,
  preserveSystemDir = preserveSystemDir,
) {

  override fun setProfiler(profilerType: ProfilerType): IDETestContext {
    frontendIDEContext.setProfiler(profilerType)
    return super.setProfiler(profilerType)
  }

  override fun applyVMOptionsPatch(patchVMOptions: VMOptions.() -> Unit): IDETestContext {
    frontendIDEContext.applyVMOptionsPatch(patchVMOptions)
    return super.applyVMOptionsPatch(patchVMOptions)
  }

  override fun wipeWorkspaceState(): IDETestContext = apply {
    frontendIDEContext.wipeWorkspaceState()
    super.wipeWorkspaceState()
  }

  companion object {

    fun from(backendContext: IDETestContext, frontendCtx: IDETestContext): IDERemDevTestContext {
      return IDERemDevTestContext(
        paths = backendContext.paths,
        ide = backendContext.ide,
        testCase = backendContext.testCase,
        testName = backendContext.testName,
        _resolvedProjectHome = backendContext._resolvedProjectHome,
        profilerType = backendContext.profilerType,
        publishers = backendContext.publishers,
        isReportPublishingEnabled = backendContext.isReportPublishingEnabled,
        preserveSystemDir = backendContext.preserveSystemDir,
        frontendIDEContext = frontendCtx,
      )
    }
  }
}

val IDETestContext.frontendTestCase: TestCase<out ProjectInfoSpec>
  get() {
    // This path is used in resolveIDE -> com.intellij.ide.starter.ide.LinuxIdeDistribution.installIde
    val executableFileName = when {
      SystemInfo.isLinux && ConfigurationStorage.useInstaller() -> "jetbrains_client.sh"
      SystemInfo.isWindows && ConfigurationStorage.useInstaller() -> "jetbrains_client"
      else -> this.testCase.ideInfo.executableFileName
    }

    return this.testCase.copy(ideInfo = this.testCase.ideInfo.copy(
      platformPrefix = PlatformUtils.JETBRAINS_CLIENT_PREFIX,
      baseIdePlatformPrefixForFrontend = this.testCase.ideInfo.platformPrefix,
      executableFileName = executableFileName
    ))
  }

/**
 * Sets the directory for event log metadata in the frontend IDE context.
 *
 * NOTE: The frontend/client expects the directory with the meta-data to be inside per_client_config,
 * but this directory is cleared when the frontend starts.
 * Therefore, here we redefine the path to the directory with the frontend meta-data to the same folder in the backend,
 * which is not cleared and contains the necessary data.
 * Without calling this function, the frontend event log will contain values like `validation.unreachable_metadata`, `validation.undefined_rule`
 *
 * @param path The path to the event log metadata directory. Defaults to the same folder from the backend.
 * @return The updated `IDERemDevTestContext` instance.
 */
fun IDERemDevTestContext.setFrontendEventLogsMetadataCustomPath(path: Path = paths.eventLogMetadataDir): IDERemDevTestContext {
  frontendIDEContext.applyVMOptionsPatch {
    addSystemProperty("intellij.fus.custom.schema.dir", path.toString())
  }
  return this
}

