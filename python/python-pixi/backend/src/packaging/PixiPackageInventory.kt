// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.intellij.python.community.execService.Args
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.requirements.PyPackageVersionComparator
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonPackageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

internal data class PixiInstalledCondaPackage(
  val name: String,
  val version: String,
  val pythonDistributions: Map<PyPackageName, String> = emptyMap(),
  val channel: String? = null,
  val dependencies: List<String>? = null,
  val constraints: List<String> = emptyList(),
  val artifact: PixiNativeArtifact? = null,
)

internal suspend fun readPixiCondaPackagesFromInterpreter(interpreter: PythonInterpreter): PyResult<List<PixiInstalledCondaPackage>> {
  val output = executePixiInterpreter(interpreter, Args("-c", CONDA_INVENTORY)).getOr { return it }
  return parseInstalledPixiCondaRecords(output)
}

internal suspend fun readInstalledPixiCondaPackages(prefix: Path): PyResult<List<PixiInstalledCondaPackage>> = withContext(Dispatchers.IO) {
  try {
    val packages = Files.newDirectoryStream(prefix.resolve("conda-meta"), "*.json").use { records ->
      records.map { record ->
        INVENTORY_JSON.decodeFromString<PixiCondaRecord>(record.readText()).toInstalledPackage()
      }
    }
    PyResult.success(packages)
  }
  catch (_: SerializationException) {
    PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.invalid.output"))
  }
  catch (e: IOException) {
    PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.inventory.failed", e.localizedMessage))
  }
}

internal fun parseInstalledPixiCondaRecords(output: String): PyResult<List<PixiInstalledCondaPackage>> = try {
  PyResult.success(INVENTORY_JSON.decodeFromString<List<PixiCondaRecord>>(output).map { it.toInstalledPackage() })
}
catch (_: SerializationException) {
  PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.invalid.output"))
}

private fun PixiCondaRecord.toInstalledPackage(): PixiInstalledCondaPackage {
  val paths = files.asSequence() + pathsData?.paths.orEmpty().asSequence().map { it.path }
  return PixiInstalledCondaPackage(
    name, version, paths.mapNotNull(::ownedPythonDistribution).toMap(), channel, dependencies, constraints,
    createPixiNativeArtifact(version, build, url, sha256, md5),
  )
}

internal fun mergeInstalledPixiPackages(
  nativePackages: List<PixiInstalledCondaPackage>,
  pythonMetadata: Map<PyPackageName, PythonPackageMetadata>,
  resolvedPackages: List<PixiPackage>,
): List<PixiPackage> {
  val native = nativePackages.filter { pkg ->
    val installedDistributions = pkg.distributions().mapNotNull { (name, version) ->
      pythonMetadata[name]?.let { it.version to version }
    }
    installedDistributions.isEmpty() || installedDistributions.any { (actual, expected) -> versionsMatch(actual, expected) }
  }
  val nativeNames = native.flatMapTo(mutableSetOf()) { pkg ->
    pkg.distributions().filter { (name, version) -> pythonMetadata[name]?.let { versionsMatch(it.version, version) } == true }.keys
  }
  val editableNames = resolvedPackages.filter { it.kind == "pypi" && it.editable }.mapTo(mutableSetOf()) { PyPackageName.from(it.name) }
  val python = pythonMetadata.filterKeys { it !in nativeNames }.map { (name, metadata) ->
    PixiPackage(name.name, metadata.version, "pypi", editable = name in editableNames)
  }
  return (native.map { PixiPackage(it.name, it.version, "conda") } + python).sortedBy { it.name }
}

private fun PixiInstalledCondaPackage.distributions(): Map<PyPackageName, String> =
  pythonDistributions.ifEmpty { mapOf(PyPackageName.from(name) to version) }

private fun versionsMatch(actual: String, expected: String): Boolean =
  PyPackageVersionComparator.STR_COMPARATOR.compare(actual, expected) == 0

private fun ownedPythonDistribution(path: String): Pair<PyPackageName, String>? {
  for (part in path.replace('\\', '/').split('/')) {
    val match = DIST_INFO.matchEntire(part) ?: EGG_INFO.matchEntire(part) ?: continue
    return PyPackageName.from(match.groupValues[1]) to match.groupValues[2]
  }
  return null
}

private val DIST_INFO = Regex("(.+)-([^-]+)\\.dist-info")
private val EGG_INFO = Regex("(.+?)-([0-9][^-]*)(?:-py[0-9.]+)?\\.egg-info")
private val INVENTORY_JSON = Json { ignoreUnknownKeys = true }
private val CONDA_INVENTORY = """
  import json, pathlib, sys
  records = pathlib.Path(sys.prefix) / "conda-meta"
  if not records.is_dir():
      raise FileNotFoundError(records)
  print(json.dumps([json.loads(record.read_text(encoding="utf-8")) for record in records.glob("*.json")]))
""".trimIndent()

@Serializable
private data class PixiCondaRecord(
  val name: String,
  val version: String,
  val channel: String? = null,
  val files: List<String> = emptyList(),
  @SerialName("depends") val dependencies: List<String>? = null,
  @SerialName("constrains") val constraints: List<String> = emptyList(),
  val build: String? = null,
  val url: String? = null,
  val sha256: String? = null,
  val md5: String? = null,
  @SerialName("paths_data") val pathsData: PixiCondaPaths? = null,
)

@Serializable
private data class PixiCondaPaths(val paths: List<PixiCondaPath> = emptyList())

@Serializable
private data class PixiCondaPath(@SerialName("_path") val path: String)
