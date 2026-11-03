// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import java.net.URI

internal data class PixiNativeArtifact(
  val version: String,
  val build: String,
  val source: String,
  val sha256: String?,
  val md5: String?,
) {
  fun matches(other: PixiNativeArtifact): Boolean =
    version == other.version && build == other.build && source == other.source && when {
      sha256 != null && other.sha256 != null -> sha256.equals(other.sha256, ignoreCase = true)
      md5 != null && other.md5 != null -> md5.equals(other.md5, ignoreCase = true)
      else -> false
    }
}

internal fun createPixiNativeArtifact(
  version: String, build: String?, source: String?, sha256: String?, md5: String?,
): PixiNativeArtifact? {
  if (build.isNullOrEmpty() || source.isNullOrEmpty()) return null
  val validSha256 = sha256?.takeIf { SHA256.matches(it) }
  val validMd5 = md5?.takeIf { MD5.matches(it) }
  if (validSha256 == null && validMd5 == null) return null
  val normalizedSource = normalizePixiNativeArtifactSource(source) ?: return null
  return PixiNativeArtifact(version, build, normalizedSource, validSha256, validMd5)
}

internal fun normalizePixiNativeArtifactSource(source: String): String? {
  return try {
    val uri = when {
      source.startsWith('/') -> URI("file", null, source, null)
      WINDOWS_ARTIFACT_PATH.matches(source) -> URI("file", null, "/" + source.replace('\\', '/'), null)
      else -> URI(source)
    }
    if (uri.scheme !in setOf("https", "http", "file")) return null
    URI(uri.scheme, uri.authority, uri.path, uri.query, uri.fragment).normalize().toASCIIString()
  }
  catch (_: IllegalArgumentException) {
    null
  }
  catch (_: java.net.URISyntaxException) {
    null
  }
}

private val SHA256 = Regex("[a-fA-F0-9]{64}")
private val MD5 = Regex("[a-fA-F0-9]{32}")
private val WINDOWS_ARTIFACT_PATH = Regex("[A-Za-z]:[\\\\/].*")
