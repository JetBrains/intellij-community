// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.application.PathManager
import com.intellij.util.system.CpuArch
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path

@ApiStatus.Internal
internal class NativeBridgeLibrary(
  private val displayName: String,
  private val logEvent: String,
  private val relativePaths: List<String>,
  private val rebuildHint: String,
  private val loadFailureHint: String,
  private val pluginAnchorClass: Class<*>,
  private val sourceFileLookup: (String) -> Path? = ::defaultSourceFileLookup,
  private val pluginResourceLookup: (Class<*>, String) -> Path? = ::defaultPluginResourceLookup,
  private val missingFileDetails: (String) -> String = ::defaultMissingPluginResourceDetails,
) {
  fun availability(): NativeBridgeLibraryAvailability {
    return findLibrary()?.let { NativeBridgeLibraryAvailability.Available(it) }
           ?: NativeBridgeLibraryAvailability.Missing(missingLibraryMessage())
  }

  fun load(): Path {
    val libraryPath = when (val availability = availability()) {
      is NativeBridgeLibraryAvailability.Available -> availability.path
      is NativeBridgeLibraryAvailability.Missing -> error(availability.message)
    }
    WebViewLogger.logLifecycle(logEvent, libraryPath.toString())
    try {
      System.load(libraryPath.toString())
    }
    catch (e: UnsatisfiedLinkError) {
      throw IllegalStateException("Failed to load $displayName: $libraryPath. $loadFailureHint", e)
    }
    return libraryPath
  }

  fun verifyAbi(libraryPath: Path, expectedAbiVersion: String, readAbiVersion: () -> String) {
    val nativeAbiVersion = try {
      readAbiVersion()
    }
    catch (e: UnsatisfiedLinkError) {
      throw IllegalStateException(
        "$displayName is stale or incompatible: $libraryPath. " +
        "Expected ABI '$expectedAbiVersion', but abiVersionNative is missing. $rebuildHint",
        e,
      )
    }
    check(nativeAbiVersion == expectedAbiVersion) {
      "$displayName ABI mismatch: $libraryPath reports '$nativeAbiVersion', expected '$expectedAbiVersion'. " +
      rebuildHint
    }
    WebViewLogger.logLifecycle(logEvent, "native ABI $nativeAbiVersion")
  }

  private fun findLibrary(): Path? {
    for (relativePath in relativePaths) {
      sourceFileLookup(relativePath)?.let { return it.toAbsolutePath().normalize() }
      pluginResourceLookup(pluginAnchorClass, relativePath)?.let { return it.toAbsolutePath().normalize() }
    }
    return null
  }

  private fun missingLibraryMessage(): String {
    return buildString {
      append("$displayName is missing. Checked WebView plugin resources: ")
      append(relativePaths.joinToString())
      val details = relativePaths.firstOrNull()?.let(missingFileDetails)?.takeIf { it.isNotBlank() }
      if (details != null) {
        append(". ")
        append(details)
      }
    }
  }
}

internal sealed interface NativeBridgeLibraryAvailability {
  data class Available(val path: Path) : NativeBridgeLibraryAvailability
  data class Missing(val message: String) : NativeBridgeLibraryAvailability
}

internal fun webViewNativeArchDirectory(): String {
  return when {
    CpuArch.isIntel64() -> "x86_64"
    CpuArch.isArm64() -> "aarch64"
    else -> CpuArch.CURRENT.name.lowercase()
  }
}

private const val WEBVIEW_PLUGIN_SOURCE_ROOT = "community/plugins/ui.webview"
private const val IDEA_DEV_PROJECT_ROOT_PROPERTY = "idea.dev.project.root"

private fun defaultSourceFileLookup(relativePath: String): Path? {
  return defaultSourceRoots().asSequence()
    .map { sourceRoot ->
      sourceRoot
        .resolve(WEBVIEW_PLUGIN_SOURCE_ROOT)
        .resolve(relativePath)
    }
    .firstOrNull { Files.isRegularFile(it) }
}

private fun defaultPluginResourceLookup(anchorClass: Class<*>, relativePath: String): Path? {
  return (anchorClass.classLoader as? PluginAwareClassLoader)
    ?.pluginDescriptor
    ?.pluginPath
    ?.resolve(relativePath)
    ?.takeIf(Files::isRegularFile)
}

private fun defaultMissingPluginResourceDetails(relativePath: String): String {
  val sourcePaths = defaultSourceRoots().map { sourceRoot ->
    sourceRoot
      .resolve(WEBVIEW_PLUGIN_SOURCE_ROOT)
      .resolve(relativePath)
      .toAbsolutePath()
      .normalize()
  }

  return if (sourcePaths.isNotEmpty()) {
    "Source-tree fallback was also checked at: ${sourcePaths.joinToString()}"
  }
  else {
    "'$relativePath' not found in WebView plugin resources"
  }
}

private fun defaultSourceRoots(): List<Path> {
  val roots = mutableListOf<Path>()
  System.getProperty(IDEA_DEV_PROJECT_ROOT_PROPERTY)
    ?.takeIf { it.isNotBlank() }
    ?.let { runCatching { Path.of(it) }.getOrNull() }
    ?.let { roots.addIfAbsent(it) }
  runCatching { PathManager.getHomeDir() }
    .getOrNull()
    ?.let { roots.addIfAbsent(it) }
  return roots
}

private fun MutableList<Path>.addIfAbsent(path: Path) {
  val normalizedPath = path.toAbsolutePath().normalize()
  if (normalizedPath !in this) {
    add(normalizedPath)
  }
}
