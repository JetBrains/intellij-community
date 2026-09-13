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
import com.intellij.platform.buildScripts.concurrency.withLockInterruptibly
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.ScrambleTool
import org.jetbrains.intellij.build.dev.BuildRequest
import org.jetbrains.intellij.build.dev.buildProductInProcess
import org.jetbrains.intellij.build.dev.readCustomCommand
import org.jetbrains.intellij.build.dev.resolveAdditionalJvmArguments
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.io.path.copyTo
import kotlin.io.path.div
import kotlin.io.path.exists

/**
 * Starts, stops Dev Build Server.
 *
 * IDE selection is enforced at compile-time through module dependencies.
 * Tests must depend on IDE-specific modules (e.g., `intellij.tools.ide.starter.product.goland`)
 * to use the corresponding IdeInfo, which ensures CI can determine upfront which IDEs need to be built.
 */
object DevBuildServerRunnerImpl : DevBuildServerRunner {
  private val ideaRootPath = PathManager.getHomeDir()

  private val lock = ReentrantLock()

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

  override fun readCustomCommandJvmArguments(installationDirectory: Path, command: String): List<String>? =
    readCustomCommand(installationDirectory, command)?.resolveAdditionalJvmArguments(installationDirectory)

  /**
   * Returns IDE installation directory.
   *
   * The build runs on a virtual thread of its own, and the caller suspends until it is done. A dispatcher thread
   * must not own the build: a platform thread that loads a class beside the build workers can take part in the
   * deadlock of JDK-8369019. A cancelled caller interrupts the build.
   */
  override suspend fun startDevBuild(ideInfo: IdeInfo): Path {
    val result = CompletableFuture<Path>()
    val builder = Thread.ofVirtual().name("dev build ${ideInfo.platformPrefix}").start {
      try {
        result.complete(buildDevDistribution(ideInfo))
      }
      catch (failure: Throwable) {
        result.completeExceptionally(failure)
      }
    }
    try {
      return result.await()
    }
    catch (e: CancellationException) {
      builder.interrupt()
      throw e
    }
  }

  /** Builds one IDE at a time. The wait for the lock stops for an interrupt. */
  private fun buildDevDistribution(ideInfo: IdeInfo): Path {
    return lock.withLockInterruptibly {
      logOutput("Starting dev build server for $ideInfo ...")
      copyArtifactsToDefaultDir()

      val installationDirectory = computeWithSpan("building ide $ideInfo") {
        System.setProperty("intellij.build.console.exporter.enabled", false.toString())
        System.setProperty("intellij.build.export.opentelemetry.spans", true.toString())
        val targetOs = if (ConfigurationStorage.useDockerContainer()) OsFamily.LINUX else OsFamily.currentOs
        buildProductInProcess(
          BuildRequest(
            projectDir = ideaRootPath,
            os = targetOs,
            platformPrefix = ideInfo.platformPrefix,
            baseIdePlatformPrefixForFrontend = ideInfo.baseIdePlatformPrefixForFrontend,
            additionalModules = ideInfo.additionalModules,
            scrambleTool = di.direct.instance<ScrambleToolProvider>().get() as ScrambleTool?,
            generateRuntimeModuleRepository = ConfigurationStorage.includeRuntimeModuleRepositoryInIde(),
            tracer = TestTelemetryService.instance.getTracer(),
            isBootClassPathCorrect = true,
            classesOutputDirectory = GlobalPaths.instance.compiledRootDirectory.resolve("classes"),
            devRunDirPrefix = if (targetOs != OsFamily.currentOs) "${targetOs.name.lowercase()}-" else "",
          )
        ).runDir
      }

      logOutput("Building IDE by dev build server finished for $ideInfo")
      installationDirectory
    }
  }
}
