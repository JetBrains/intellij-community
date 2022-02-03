package com.intellij.ide.starter.ide

import com.intellij.ide.starter.models.VMOptions
import java.nio.file.Path

interface InstalledIDE {
  val originalVMOptions: VMOptions

  val build: String
  val os: String
  val productCode: String
  val isFromSources: Boolean

  /** Bundled plugins directory, if supported **/
  val bundledPluginsDir: Path?
    get() = null

  val patchedVMOptionsFile: Path?
    get() = null

  fun startConfig(vmOptions: VMOptions, logsDir: Path): IDEStartConfig

  fun resolveAndDownloadTheSameJDK(): Path

  fun isMajorVersionAtLeast(v: Int) = build.substringBefore(".").toIntOrNull()?.let { it >= v } ?: true
}