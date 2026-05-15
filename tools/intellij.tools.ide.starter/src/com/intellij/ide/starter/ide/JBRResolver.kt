package com.intellij.ide.starter.ide

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.jbrVersionForDevServer
import com.intellij.ide.starter.config.localJbrPath
import com.intellij.ide.starter.config.useDockerContainer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.runner.SetupException
import com.intellij.ide.starter.telemetry.computeWithSpan
import com.intellij.ide.starter.utils.catchAll
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.div

object JBRResolver {
  data class JBRVersion(val majorVersion: String, val buildNumber: String)
  class JBRDownloadException(jbrFullVersion: String) : SetupException("$jbrFullVersion can't be downloaded/unpacked")

  private fun getJBRVersionFromBuild(jbrFullVersion: String): JBRVersion {
    val jbrVersion = jbrFullVersion.split(".")[0].toInt()
    var majorVersion = jbrFullVersion.split("+").firstOrNull()
    if (jbrVersion < 17) {
      majorVersion = majorVersion?.replace(".", "_")
    }
    requireNotNull(majorVersion) {
      { "majorVersion is: $majorVersion" }
    }
    val buildNumber = jbrFullVersion.split("-b").drop(1).singleOrNull()
    requireNotNull(buildNumber) {
      { "buildNumber is: $buildNumber" }
    }
    logOutput("Detected JBR version $jbrFullVersion with parts: $majorVersion and build $buildNumber")
    return JBRVersion(majorVersion, buildNumber)
  }

  private fun getJBRVersionFromSources(jbrFullVersion: String): JBRVersion {
    var majorVersion = jbrFullVersion.split("b").firstOrNull()
    requireNotNull(majorVersion) { { "majorVersion is: $majorVersion" } }
    val jbrVersion = jbrFullVersion.split(".")[0].toIntOrNull() ?: majorVersion.toIntOrNull()
    requireNotNull(jbrVersion) { "Cannot parse JBR version from: $jbrFullVersion" }
    if (jbrVersion < 17) {
      majorVersion = majorVersion.replace(".", "_")
    }
    val buildNumber = jbrFullVersion.split("b").drop(1).singleOrNull()
    requireNotNull(buildNumber) {
      { "buildNumber is: $buildNumber" }
    }
    logOutput("Detected JBR version $jbrFullVersion with parts: $majorVersion and build $buildNumber")
    return JBRVersion(majorVersion, buildNumber)
  }

  suspend fun downloadAndUnpackJbrFromBuildIfNeeded(jbrFullVersion: String): Path {
    localJbrPathOverride()?.let { return it }
    return catchAll { downloadAndUnpackJbrIfNeeded(getJBRVersionFromBuild(jbrFullVersion)) } ?: throw JBRDownloadException(jbrFullVersion)
  }

  suspend fun downloadAndUnpackJbrFromSourcesIfNeeded(jbrFullVersion: String): Path {
    localJbrPathOverride()?.let { return it }
    return catchAll { downloadAndUnpackJbrIfNeeded(getJBRVersionFromSources(jbrFullVersion)) } ?: throw JBRDownloadException(jbrFullVersion)
  }

  private fun localJbrPathOverride(): Path? {
    val raw = ConfigurationStorage.localJbrPath() ?: return null
    val path = Path.of(raw).toAbsolutePath()
    require(Files.isDirectory(path)) { "Local JBR override does not point to an existing directory: $path" }
    logOutput("Using local JBR override at $path")
    return path
  }

  /**
   * Installer-mode override: swap `<appHome>/jbr` (or `<appHome>/jbr/Contents/Home` on macOS) with a
   * symlink to the user-supplied path. The bundled tree is kept as a `.bundled` sibling and restored
   * automatically on the next run with no override.
   */
  @OptIn(LowLevelLocalMachineAccess::class)
  fun applyInstallerJbrOverrideIfLocalJbrPathNotEmpty(appHome: Path) {
    val swapTarget = if (OS.CURRENT == OS.macOS) appHome / "jbr" / "Contents" / "Home" else appHome / "jbr"
    val backupSibling = swapTarget.parent.resolve("${swapTarget.fileName}.bundled")
    val raw = ConfigurationStorage.localJbrPath()

    if (raw != null) {
      val overridePath = Path.of(raw).toAbsolutePath()
      require(Files.isDirectory(overridePath)) { "Local JBR override is not a directory: $overridePath" }
      require(Files.isRegularFile(overridePath / "bin" / "java") || Files.isRegularFile(overridePath / "bin" / "java.exe")) {
        "Local JBR override does not contain bin/java(.exe): $overridePath"
      }

      if (Files.notExists(backupSibling)) {
        if (Files.isSymbolicLink(swapTarget)) Files.delete(swapTarget)
        else if (Files.exists(swapTarget)) Files.move(swapTarget, backupSibling)
      }
      else if (Files.isSymbolicLink(swapTarget) || Files.exists(swapTarget)) {
        Files.delete(swapTarget)
      }

      Files.createDirectories(swapTarget.parent)
      try {
        Files.createSymbolicLink(swapTarget, overridePath)
        logOutput("Installer JBR override active: $swapTarget -> $overridePath (original kept at $backupSibling)")
      }
      catch (e: Exception) {
        logError("Failed to create JBR override symlink at $swapTarget (${e.message}); restoring bundled JBR")
        if (Files.exists(backupSibling)) Files.move(backupSibling, swapTarget)
      }
    }
    else if (Files.exists(backupSibling)) {
      if (Files.isSymbolicLink(swapTarget) || Files.exists(swapTarget)) Files.delete(swapTarget)
      Files.move(backupSibling, swapTarget)
      logOutput("Restored bundled installer JBR at $swapTarget")
    }
  }

  @OptIn(LowLevelLocalMachineAccess::class)
  suspend fun downloadAndUnpackJbrIfNeeded(jbrVersion: JBRVersion): Path = computeWithSpan("download and unpack JBR") {
    val (majorVersion, buildNumber) = listOf(jbrVersion.majorVersion, jbrVersion.buildNumber)

    var os = when (OS.CURRENT) {
      OS.Linux -> "linux"
      OS.Windows -> "windows"
      OS.macOS -> "osx"
      else -> error("Unknown OS")
    }

    if(ConfigurationStorage.useDockerContainer()){
      os = "linux"
    }

    val arch = when (SystemInfo.isAarch64) {
      true -> "aarch64"
      false -> "x64"
    }

    val jbrFileName = "jbrsdk_jcef-$majorVersion-$os-$arch-b$buildNumber.tar.gz"
    val appHome = withContext(Dispatchers.IO) {
      di.direct.instance<JBRDownloader>().downloadJbr(jbrFileName)
    }
    return if (OS.CURRENT == OS.macOS && !ConfigurationStorage.useDockerContainer()) appHome.resolve("Contents/Home") else appHome
  }

  fun getRuntimeBuildVersion(): String {
    val jbrVersion = ConfigurationStorage.jbrVersionForDevServer() ?: run {
      val jbrDependencyFile = GlobalPaths.instance.checkoutDir / "community" / "build" / "dependencies" / "dependencies.properties"
      val props = Properties()
      Files.newBufferedReader(jbrDependencyFile).use { reader -> props.load(reader) }
      props.getProperty("runtimeBuild")
    }
    return jbrVersion
  }
}