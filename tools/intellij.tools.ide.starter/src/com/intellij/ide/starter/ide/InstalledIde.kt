package com.intellij.ide.starter.ide

import com.intellij.ide.starter.models.VMOptions
import com.intellij.util.system.OS
import java.nio.file.Path

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

  suspend fun resolveAndDownloadTheSameJDK(): Path

  /** Check the major version of the build number.
   * Eg: 232.9921.47 => 232
   **/
  fun isMajorBuildVersionAtLeast(v: Int) = build.substringBefore(".").toIntOrNull()?.let { it >= v } ?: true
}