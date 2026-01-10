// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.core

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import com.intellij.util.text.SemVer
import org.jetbrains.annotations.ApiStatus

/**
 * Shared Python version mapping utility for test environment providers.
 *
 * Maps major.minor versions to full major.minor.patch versions based on
 * python-build-standalone releases.
 */
@ApiStatus.Internal
object PyVersionMapping {

  /**
   * Python version build information.
   * Files map key format: platform-arch-libc-variant
   * Example: "linux-x86_64-gnu-install_only"
   * Files map value: FileInfo with filename and optional sha256
   */
  data class PythonBuildInfo(
    @field:JsonProperty("baseUrl") val baseUrl: String,
    @field:JsonProperty("files") val files: Map<String, FileInfo>
  )

  /**
   * File information for a Python build.
   */
  data class FileInfo(
    @field:JsonProperty("filename") val filename: String,
    @field:JsonProperty("sha256") val sha256: String,
    @field:JsonProperty("size") val size: Long
  )

  /**
   * Download information for a Python build.
   */
  data class DownloadInfo(
    val url: String,
    val sha256: String,
    val size: Long
  )

  /**
   * Python version to build information mapping.
   * Loaded from python-version-mapping.json resource file.
   */
  private val PYTHON_VERSION_MAPPING: Map<SemVer, PythonBuildInfo> by lazy {
    loadVersionMapping()
  }

  /**
   * Get the Python version to build information mapping.
   * @return Map of version strings to PythonBuildInfo
   */
  fun getPythonVersionMapping(): Map<SemVer, PythonBuildInfo> = PYTHON_VERSION_MAPPING

  /**
   * Load version mapping from JSON file.
   */
  private fun loadVersionMapping(): Map<SemVer, PythonBuildInfo> {
    val resourceName = "/com/intellij/python/test/env/core/python-version-mapping.json"
    val mapper = ObjectMapper().registerKotlinModule()

    val inputStream = PyVersionMapping::class.java.getResourceAsStream(resourceName)
      ?: error("Failed to load Python version mapping from $resourceName")

    return inputStream.use { stream ->
      mapper.readValue(stream, jacksonTypeRef<Map<String, PythonBuildInfo>>())
    }.mapKeys { parsePythonVersion(it.key) }
  }

  /**
   * Get the download information for python-build-standalone for a given Python version.
   * Automatically detects the platform, architecture, and selects appropriate libc/variant.
   *
   * @param version Version string like "3.11", "3.12", or "3.11.14"
   * @return Download information containing URL and optional SHA256 checksum
   */
  fun getDownloadInfo(version: String): DownloadInfo {
    val fullVersion = parsePythonVersion(version)
    val buildInfo = PYTHON_VERSION_MAPPING[fullVersion]
      ?: error("No build information found for Python $fullVersion")

    // Detect platform
    val platform = when (OS.CURRENT) {
      OS.Windows -> "windows"
      OS.macOS -> "darwin"
      OS.Linux -> "linux"
      else -> error("Unsupported platform: ${OS.CURRENT}")
    }

    // Detect architecture
    val baseArch = when {
      CpuArch.isArm64() -> "aarch64"
      CpuArch.isIntel64() -> "x86_64"
      else -> error("Unsupported architecture: ${CpuArch.CURRENT}")
    }
    
    // For ARM64, try both aarch64 and arm64 as synonyms on all platforms
    val archVariants = if (baseArch == "aarch64") {
      listOf("aarch64", "arm64")
    } else {
      listOf(baseArch)
    }

    // Select libc preference
    val libc = when (platform) {
      "windows" -> "msvc"
      "darwin" -> "unknown"
      "linux" -> "gnu" // Prefer gnu over musl by default
      else -> "unknown"
    }

    // Try to find a file with preferred variant and libc, fallback if needed
    val preferredVariants = listOf("install_only", "install_only_stripped")
    val preferredLibcs = if (platform == "linux") listOf(libc, "musl") else listOf(libc)

    var fileInfo: FileInfo? = null

    // Try preferred combinations first
    for (arch in archVariants) {
      for (variant in preferredVariants) {
        for (libcOption in preferredLibcs) {
          val key = "$platform-$arch-$libcOption-$variant"
          if (key in buildInfo.files) {
            fileInfo = buildInfo.files[key]
            break
          }
        }
        if (fileInfo != null) break
      }
      if (fileInfo != null) break
    }

    // If no match found, try any file for this platform
    if (fileInfo == null) {
      for (arch in archVariants) {
        val matchingKey = buildInfo.files.keys.firstOrNull { it.startsWith("$platform-$arch-") }
        if (matchingKey != null) {
          fileInfo = buildInfo.files[matchingKey]
          break
        }
      }
    }

    if (fileInfo == null) {
      error("No suitable build found for Python $fullVersion on $platform/$baseArch. Available: ${buildInfo.files.keys}")
    }

    // Construct URL: baseUrl/{filename}
    val url = "${buildInfo.baseUrl}/${fileInfo.filename}"
    return DownloadInfo(url, fileInfo.sha256, fileInfo.size)
  }
}
