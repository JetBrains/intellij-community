package com.intellij.ide.starter.ide

import com.intellij.ide.starter.utils.JvmUtils
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.utils.catchAll
import com.intellij.util.system.OS
import com.intellij.tools.ide.util.common.logError
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

interface InstalledIde {
  val vmOptions: VMOptions

  val build: String
  val os: OS
  val productCode: String
  val isFromSources: Boolean

  /** Eg: /opt/REPO/intellij/out/ide-tests/cache/builds/GO-233.6745.304/GoLand-233.6745.304/ */
  val installationPath: Path

  /** Bundled plugins directory, if supported **/
  val bundledPluginsDir: Path?
    get() = null

  val patchedVMOptionsFile: Path?
    get() = null

  fun startConfig(vmOptions: VMOptions, logsDir: Path): IDEStartConfig

  /**
   * Throws IllegalArgumentException is the same jdk was not found
   */
  suspend fun resolveAndDownloadTheSameJDK(): Path

  /** Check the major version of the build number.
   * Eg: 232.9921.47 => 232
   **/
  fun isMajorBuildVersionAtLeast(v: Int) = build.substringBefore(".").toIntOrNull()?.let { it >= v } ?: true

  /**
   * Throws IllegalArgumentException is no jdk was not found
   */
  suspend fun resolveAndDownloadTheSameJDKOrFallback(): Path {
    return jdkHomeCache[this] ?: try {
      resolveAndDownloadTheSameJDK().also { jdkHomeCache[this] = it }
    }
    catch (e: Exception) {
      logError("Failed to download the same JDK as in $build, Fallback to default java: $fallbackJdkHome", e)
      requireNotNull(fallbackJdkHome) { "Failed to resolve default jdk" }
    }
  }

  companion object {
    private val fallbackJdkHome: Path? by lazy { catchAll("Resolve default jdk") { JvmUtils.resolveInstalledJdk() } }
    private val jdkHomeCache = ConcurrentHashMap<InstalledIde, Path>()
  }
}