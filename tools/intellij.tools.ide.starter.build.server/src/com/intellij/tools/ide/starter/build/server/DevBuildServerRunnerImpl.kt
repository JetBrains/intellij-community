package com.intellij.tools.ide.starter.build.server

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.ScrambleToolProvider
import com.intellij.ide.starter.config.includeRuntimeModuleRepositoryInIde
import com.intellij.ide.starter.config.useDockerContainer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.runner.DevBuildServerRunner
import com.intellij.ide.starter.telemetry.TestTelemetryService
import com.intellij.ide.starter.telemetry.computeWithSpan
import com.intellij.openapi.application.PathManager
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.ScrambleTool
import org.jetbrains.intellij.build.dev.BuildRequest
import org.jetbrains.intellij.build.dev.buildProductInProcess
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.div
import kotlin.io.path.exists

/**
 * Starts, stops Dev Build Server
 */
object DevBuildServerRunnerImpl : DevBuildServerRunner {
  private val ideaRootPath = PathManager.getHomeDir()

  private val mutex = Mutex()

  private fun copyArtifactsToDefaultDir() {
    if (!CIServer.instance.isBuildRunningOnCI) {
      return
    }

    // E.g: ../out/tests/jps-artifacts
    val artifactsOnCI = GlobalPaths.instance.compiledRootDirectory / "jps-artifacts"
    if (artifactsOnCI.exists()) {
      // required to run a dev build server on CI (could find variable to override artifacts path)
      artifactsOnCI.copyTo(GlobalPaths.instance.artifactsDirectory, overwrite = true)
      artifactsOnCI.copyTo((GlobalPaths.instance.intelliJOutDirectory / "classes" / "artifacts"), overwrite = true)
    }
  }

  override fun isDevBuildSupported(): Boolean = true

  override suspend fun readVmOptions(installationDirectory: Path): List<String> =
    org.jetbrains.intellij.build.dev.readVmOptions(installationDirectory)

  /** Returns IDE installation directory */
  override suspend fun startDevBuild(ideInfo: IdeInfo): Path {
    mutex.withLock {
      logOutput("Starting dev build server for $ideInfo ...")
      copyArtifactsToDefaultDir()

      val installationDirectory: Path = withContext(Dispatchers.IO) {
        computeWithSpan("building ide $ideInfo") {
          System.setProperty("intellij.build.console.exporter.enabled", false.toString())
          System.setProperty("intellij.build.export.opentelemetry.spans", true.toString())
          buildProductInProcess(BuildRequest(
            projectDir = ideaRootPath,
            productionClassOutput = GlobalPaths.instance.compiledRootDirectory.resolve("classes/production"),
            os = if (ConfigurationStorage.useDockerContainer()) OsFamily.LINUX else OsFamily.currentOs,
            platformPrefix = ideInfo.platformPrefix,
            baseIdePlatformPrefixForFrontend = ideInfo.baseIdePlatformPrefixForFrontend,
            additionalModules = ideInfo.additionalModules,
            scrambleTool = di.direct.instance<ScrambleToolProvider>().get() as ScrambleTool?,
            keepHttpClient = false,
            generateRuntimeModuleRepository = ConfigurationStorage.includeRuntimeModuleRepositoryInIde(),
            tracer = TestTelemetryService.instance.getTracer(),
            isBootClassPathCorrect = true,
          ))
        }
      }

      logOutput("Building IDE by dev build server finished for $ideInfo")

      return installationDirectory
    }
  }
}