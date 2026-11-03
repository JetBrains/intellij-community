// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.intellij.python.requirements.PyPackageVersionComparator
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.net.URI

internal fun parsePixiCompatibleUpdates(
  output: String, environment: String, platform: String, locked: List<PixiNativePackage> = emptyList(),
): PyResult<List<PixiNativePackage>> {
  val diff = try {
    UPDATE_JSON.decodeFromString<PixiUpdateDiff>(output)
  }
  catch (_: SerializationException) {
    return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.updates.invalid.output"))
  }
  if (diff.version != 1) return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.updates.invalid.output"))
  val changes = diff.environment[environment]?.get(platform).orEmpty().filter { it.type == "conda" }
  val candidates = changes.mapNotNull { change ->
    val location = change.after?.conda ?: return@mapNotNull null
    val source = normalizePixiNativeArtifactSource(location)
                 ?: return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.updates.invalid.output"))
    val fileName = URI.create(source).path?.substringAfterLast('/') ?: return@mapNotNull null
    val artifact = when {
      fileName.endsWith(".conda") -> fileName.removeSuffix(".conda")
      fileName.endsWith(".tar.bz2") -> fileName.removeSuffix(".tar.bz2")
      else -> return@mapNotNull null
    }
    val prefix = "${change.name}-"
    if (!artifact.startsWith(prefix)) return@mapNotNull null
    val versionAndBuild = artifact.removePrefix(prefix)
    if ('-' !in versionAndBuild) return@mapNotNull null
    PixiNativePackage(change.name, versionAndBuild.substringBeforeLast('-'))
  }
  val changedNames = changes.mapTo(HashSet()) { it.name }
  return PyResult.success(locked.filterNot { it.name in changedNames } + candidates)
}

private val UPDATE_JSON = Json { ignoreUnknownKeys = true }

@Serializable
private data class PixiUpdateDiff(val version: Int, val environment: Map<String, Map<String, List<PixiUpdateChange>>>)

@Serializable
private data class PixiUpdateChange(val name: String, val type: String, val after: PixiUpdatePackage? = null)

@Serializable
private data class PixiUpdatePackage(val conda: String? = null, val sha256: String? = null, val md5: String? = null)

internal fun parsePixiActiveNativeArtifacts(
  output: String, environment: String, platform: String, lockedOutput: String?,
): PyResult<Map<String, PixiNativeArtifact?>> {
  val artifacts = mutableMapOf<String, PixiNativeArtifact?>()
  val diff = try {
    if (lockedOutput != null) {
      UPDATE_JSON.decodeFromString<List<PixiResolvedArtifact>>(lockedOutput).filter { it.kind == "conda" }.forEach { pkg ->
        artifacts[pkg.name] = pkg.version?.let { createPixiNativeArtifact(it, pkg.build, pkg.url, pkg.sha256, pkg.md5) }
      }
    }
    UPDATE_JSON.decodeFromString<PixiUpdateDiff>(output)
  }
  catch (_: SerializationException) {
    return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.updates.invalid.output"))
  }
  if (diff.version != 1) return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.updates.invalid.output"))
  for ((name, _, after) in diff.environment[environment]?.get(platform).orEmpty().filter { it.type == "conda" }) {
    if (after == null) {
      artifacts.remove(name)
      continue
    }
    val location = after.conda
    val fileName = location?.let(::normalizePixiNativeArtifactSource)?.let { URI.create(it).path?.substringAfterLast('/') }
    val stem = when {
      fileName == null -> null
      fileName.endsWith(".conda") -> fileName.removeSuffix(".conda")
      fileName.endsWith(".tar.bz2") -> fileName.removeSuffix(".tar.bz2")
      else -> null
    }?.takeIf { it.startsWith("$name-") }?.removePrefix("$name-")
    artifacts[name] = if (stem == null || '-' !in stem) null
    else createPixiNativeArtifact(stem.substringBeforeLast('-'), stem.substringAfterLast('-'), location, after.sha256, after.md5)
  }
  return PyResult.success(artifacts)
}

@Serializable
private data class PixiResolvedArtifact(
  val name: String,
  val version: String?,
  val kind: String,
  val build: String? = null,
  val url: String? = null,
  val sha256: String? = null,
  val md5: String? = null,
)

internal fun findPixiNativePackageUpdate(installed: PixiPackage, candidates: List<PixiNativePackage>): PythonOutdatedPackage? {
  if (installed.kind != "conda") return null
  val current = installed.version ?: return null
  val latest = candidates.filter { it.name == installed.name }.map { it.version }
    .maxWithOrNull(PIXI_CONDA_VERSION_COMPARATOR) ?: return null
  if (!isPixiNativeVersionSelectable(latest)) return null
  if (PIXI_CONDA_VERSION_COMPARATOR.compare(latest, current) <= 0) return null
  return PythonOutdatedPackage(installed.name, current, latest)
}

internal suspend fun findPixiPythonPackageUpdate(
  installed: PixiPackage, repositories: PythonRepositoryManager,
): PythonOutdatedPackage? {
  if (installed.kind != "pypi") return null
  val current = installed.version ?: return null
  repositories.awaitReady()
  val latest = repositories.repositories.mapNotNull { repository ->
    repositories.getLatestVersion(installed.name, repository)?.presentableText
  }.maxWithOrNull(PyPackageVersionComparator.STR_COMPARATOR) ?: return null
  if (PyPackageVersionComparator.STR_COMPARATOR.compare(latest, current) <= 0) return null
  return PythonOutdatedPackage(installed.name, current, latest)
}
