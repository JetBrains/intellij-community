package com.intellij.ide.starter.ide.installer

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.isScramblingEnabled
import com.intellij.ide.starter.config.useDockerContainer
import com.intellij.ide.starter.ide.IDEStartConfig
import com.intellij.ide.starter.ide.IdeInstaller
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.ide.JBRResolver
import com.intellij.ide.starter.ide.LinuxIdeDistribution
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.models.VMOptionsDiff
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.runner.AdditionalModulesForDevBuildServer
import com.intellij.ide.starter.runner.DevBuildServerRunner
import com.intellij.ide.starter.runner.FINGERPRINT_DEBUG_FILE_NAME
import com.intellij.ide.starter.runner.FINGERPRINT_DEBUG_PROPERTY
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.findOrCreateFile
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.JavaModuleOptions
import com.intellij.util.PlatformUtils
import com.intellij.util.io.createParentDirectories
import com.intellij.util.system.OS
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.appendLines
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

class IdeFromCodeInstaller(private val useInstallationCache: Boolean = true) : IdeInstaller {
  private val projectRoot by lazy { Path.of(PathManager.getHomePath(false)) }

  private fun getClassPath(ideInfo: IdeInfo, installationDirectory: Path): List<String> {
    val coreClassPathFile = installationDirectory.resolve("core-classpath.txt")

    return if (ideInfo.platformPrefix == PlatformUtils.JETBRAINS_CLIENT_PREFIX) {
      val moduleRepository = RuntimeModuleRepository.create(installationDirectory.resolve("modules").resolve("module-descriptors.jar"))
      moduleRepository.getModule(RuntimeModuleId.module("intellij.platform.runtime.loader")).moduleClasspath.map { it.toString() }
    }
    else {
      Files.readAllLines(coreClassPathFile)
    }
  }

  private fun getTestVmOptions(runDir: Path): List<String> {
    return listOf(
      "-Didea.use.dev.build.server=true",
      "-Didea.ui.icons.svg.disk.cache=false",
      "-Didea.is.internal=false",
      "-Ddev.build.dir=${runDir.last()}",
      "-Didea.dev.project.root=${projectRoot}",
      // the following options are required because `PathManager#getHomePath` cannot detect the dev build installation root
      // (due to a misplaced product info file -- but correct placing breaks about all Rider tests)
      "-D${PathManager.PROPERTY_HOME_PATH}=${runDir}",
      "-D${PathManager.PROPERTIES_FILE}=${runDir}/bin/${PathManager.PROPERTIES_FILE_NAME}",
    )
  }

  private fun getEntryPoint(ideInfo: IdeInfo): String =
    if (ideInfo.platformPrefix == PlatformUtils.JETBRAINS_CLIENT_PREFIX) "com.intellij.platform.runtime.loader.IntellijLoader"
    else "com.intellij.idea.Main"

  /**
   * Resolve all the things necessary to run tests on a locally built IDE instance.
   */
  private suspend fun resolveLocallyBuiltIDE(ideInfo: IdeInfo, installationDirectory: Path): InstalledIde {
    val runTimeVersion = JBRResolver.getRuntimeBuildVersion()
    logOutput("Found following JBR version: $runTimeVersion ")
    val javaHome = JBRResolver.downloadAndUnpackJbrFromSourcesIfNeeded(runTimeVersion)

    require(Files.isDirectory(javaHome)) { "Failed to resolve Java Home at: $javaHome" }

    val javaBin = javaHome.resolve("bin/java${if (SystemInfoRt.isWindows) ".exe" else ""}")
    require(Files.isRegularFile(javaBin)) { "Failed to resolve Java executable at: $javaBin" }

    printWarning(ideInfo)

    val defaultVmOptions = DevBuildServerRunner.instance.readVmOptions(installationDirectory) + getTestVmOptions(installationDirectory)

    @Suppress("IO_FILE_USAGE")
    val classpathArg = getClassPath(ideInfo, installationDirectory).joinToString(File.pathSeparator)

    return object : InstalledIde {
      override val vmOptions: VMOptions = VMOptions(ide = this, data = defaultVmOptions, env = emptyMap())

      override val build: String
        get() = Files.readString(projectRoot.resolve("community/build.txt")).trim()

      override val os = OS.CURRENT
      override val productCode: String = ideInfo.productCode
      override val isFromSources = true
      override val installationPath: Path = installationDirectory

      override suspend fun resolveAndDownloadTheSameJDK(): Path = javaHome

      override fun toString(): String = "LocalIDE($ideInfo)"

      override fun startConfig(vmOptions: VMOptions, logsDir: Path): IDEStartConfig {
        val seed = System.currentTimeMillis()

        val argsFile = GlobalPaths.instance.testHomePath.resolve("tmp").resolve("perf-vmOps-$seed-").also {
          it.createParentDirectories().findOrCreateFile()
        }
        val finalVMOptions = if (ConfigurationStorage.useDockerContainer()) {
          // Replace $IDE_HOME macro with actual path - the build system already produces
          // Linux native libraries when useDockerContainer() is true
          vmOptions.copy(data = vmOptions.data()
            .map { value -> value.replace("\$IDE_HOME", installationDirectory.toString()) })
        }
        else {
          vmOptions
        }
        finalVMOptions.writeJavaArgsFile(argsFile)

        val openedPackages = GlobalPaths.instance.checkoutDir
          .resolve("community/platform/platform-impl/resources/META-INF/OpenedPackages.txt")
          .let { JavaModuleOptions.readOptions(it, if (ConfigurationStorage.useDockerContainer()) OS.Linux else OS.CURRENT) }

        val otherArgs = buildList {
          addAll(openedPackages)
          add("-classpath")
          add(classpathArg)
          add(getEntryPoint(ideInfo))
        }.filter { it.isNotBlank() }
        argsFile.appendLines(otherArgs)

        logOutput("IDE run with: $finalVMOptions")

        return object : IDEStartConfig, Closeable {
          override fun close() {
            Files.deleteIfExists(argsFile)
          }

          override val workDir = projectRoot

          val commandArgs = mutableListOf(
            javaBin.toString(),
            "@$argsFile"
          )

          val xvfbRunLog = LinuxIdeDistribution.Companion.createXvfbRunLog(logsDir)

          override val commandLine: List<String> = when {
            SystemInfoRt.isLinux -> LinuxIdeDistribution.Companion.linuxCommandLine(xvfbRunLog, vmOptions = finalVMOptions) + commandArgs
            else -> commandArgs
          }

          override fun vmOptionsDiff(): VMOptionsDiff? = null
        }
      }
    }
  }

  private fun printWarning(ideInfo: IdeInfo) {
    val red = "\u001b[31m"
    val reset = "\u001b[0m"

    logOutput("\n\n\n\n\n")
    logOutput("======================================================================")
    logOutput("****                                                              ****")
    logOutput("****   $red   WARNING         WARNING         WARNING      $reset           ****")
    logOutput("****                                                              ****")
    logOutput("****      Running IDE ${ideInfo.productCode} built from local sources (Dev Server)    ****")
    logOutput("****                                                              ****")
    logOutput("****      Please note,                                            ****")
    logOutput("****         this is NOT A PRODUCTION ENVIRONMENT                 ****")
    logOutput("****         (where you run tests from a real installer),         ****")
    logOutput("****         instead, in this DEV mode we just run your           ****")
    logOutput("****         IntelliJ-based IDE from your locally built           ****")
    logOutput("****         sources (jar files under 'out/dev-run' dir)          ****")
    logOutput("****                                                              ****")
    logOutput("****      In short:                                               ****")
    logOutput("****       - call 'make' in IDE to prepare classes                ****")
    logOutput("****       - it may not work, because of different classloading   ****")
    logOutput("****                                                              ****")
    logOutput("****      You can use it to test your local changes faster        ****")
    logOutput("****                                                              ****")
    logOutput("****      In order to use Installer in test                       ****")
    logOutput("****      Please, use annotation                                  ****")
    logOutput("****      @ExtendWith(UseInstaller::class)                   ****")
    logOutput("****                                                              ****")
    logOutput("****      Running IDE ${ideInfo.productCode} built from local sources (Dev Server)    ****")
    logOutput("****                                                              ****")
    logOutput("****    $red  WARNING         WARNING         WARNING     $reset            ****")
    logOutput("****                                                              ****")
    logOutput("======================================================================")
    logOutput("\n\n\n\n\n")
  }

  companion object {
    private val cachedInstallationDirectories = mutableMapOf<IdeInfo, Path>()

    // usually is only needed to save agent's space
    @OptIn(ExperimentalPathApi::class)
    fun cleanUpCachedInstallationDirectories(filter: (IdeInfo) -> Boolean = { true }) {
      cachedInstallationDirectories.filter { filter(it.key) }.forEach {
        logOutput("Cleaning up cached installation directory: ${it.value}")
        it.value.deleteRecursively()
        cachedInstallationDirectories.remove(it.key)
      }
    }
  }

  override suspend fun install(ideInfo: IdeInfo): Pair<String, InstalledIde> {
    if (DevBuildServerRunner.instance.isDevBuildSupported().not()) {
      error("Dev build is not supported. Add dependency on intellij.tools.ide.starter.build.server module.")
    }

    val ideWithProvidedAdditionalModules =
      ideInfo.copy(additionalModules = ideInfo.additionalModules + AdditionalModulesForDevBuildServer.getAdditionalModules(ideInfo))

    val existingInstallationPath = cachedInstallationDirectories[ideWithProvidedAdditionalModules]
    val hasCachedInstallation =
      useInstallationCache && existingInstallationPath != null && existingInstallationPath.exists() && !ConfigurationStorage.isScramblingEnabled()
    val isFingerprintDebugRequested = System.getProperty(FINGERPRINT_DEBUG_PROPERTY).toBoolean()
    val canReuseCachedInstallation =
      hasCachedInstallation &&
      (!isFingerprintDebugRequested || existingInstallationPath.resolve(FINGERPRINT_DEBUG_FILE_NAME).exists())

    val installationDirectory =
      if (canReuseCachedInstallation) {
        logOutput("Using cached installation directory: $existingInstallationPath for $ideWithProvidedAdditionalModules")
        existingInstallationPath
      }
      else {
        if (hasCachedInstallation) {
          logOutput(
            "Cached installation directory $existingInstallationPath doesn't contain ${FINGERPRINT_DEBUG_FILE_NAME}. Rebuilding $ideWithProvidedAdditionalModules."
          )
        }
        logOutput("startDevBuild IDE: $ideWithProvidedAdditionalModules")
        DevBuildServerRunner.instance.startDevBuild(ideWithProvidedAdditionalModules).also {
          cachedInstallationDirectories[ideWithProvidedAdditionalModules] = it
        }
      }
    val suffix = if (ConfigurationStorage.useDockerContainer()) "-DOCKER" else ""
    return "LOCAL${suffix}" to resolveLocallyBuiltIDE(ideInfo = ideWithProvidedAdditionalModules, installationDirectory)
  }
}
