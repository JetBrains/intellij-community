// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.python.community.execService.Args
import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonPackageMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal suspend fun exportInstalledPixiEnvironment(
  interpreter: PythonInterpreter,
  environmentName: String,
  channels: List<String>,
): PyResult<String> {
  val output = executePixiInterpreter(interpreter, Args("-c", EXPORT_INVENTORY)).getOr { return it }
  val inventory = try {
    EXPORT_JSON.decodeFromString<PixiExportInventory>(output)
  }
  catch (_: SerializationException) {
    return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.invalid.output"))
  }
  val native = parseInstalledPixiCondaRecords(EXPORT_JSON.encodeToString(inventory.conda)).getOr { return it }
  val distributions = inventory.python.associateBy { PyPackageName.from(it.name) }
  val metadata = distributions.mapValues { (_, pkg) -> PythonPackageMetadata(name = pkg.name, version = pkg.version) }
  val packages = mergeInstalledPixiPackages(native, metadata, emptyList())
  val nativeChannels = native.associate { it.name to it.channel?.trimEnd('/')?.takeIf(String::isNotBlank) }
  val export = buildString {
    append("name: ").append(yamlScalar(environmentName)).append('\n')
    append("channels:\n")
    for (channel in (channels + nativeChannels.values.filterNotNull()).distinct()) {
      append("  - ").append(yamlScalar(channel)).append('\n')
    }
    append("dependencies:\n")
    for ((name, version) in packages.filter { it.kind == "conda" }) {
      val channel = nativeChannels[name]
      val specification = listOfNotNull(channel, name).joinToString("::") + " ==" + version
      append("  - ").append(yamlScalar(specification)).append('\n')
    }
    val python = packages.filter { it.kind == "pypi" }
    if (python.isNotEmpty()) {
      if (packages.none { it.kind == "conda" && it.name == "pip" }) append("  - pip\n")
      append("  - pip:\n")
      for ((name) in python) {
        val distribution = distributions.getValue(PyPackageName.from(name))
        append("      - ").append(yamlScalar(distribution.requirement())).append('\n')
      }
    }
  }
  return PyResult.success(export)
}

private fun PixiExportDistribution.requirement(): String {
  val normalizedName = PyPackageName.from(name).name
  val source = directUrl ?: return "$normalizedName==$version"
  val vcs = source.vcsInfo
  var url = source.url
  if (vcs != null) {
    if (!url.startsWith("${vcs.vcs}+")) url = "${vcs.vcs}+$url"
    val revision = vcs.commitId ?: vcs.requestedRevision
    if (revision != null) url = url.substringBefore('#') + "@" + revision + url.substringAfter('#', "").let {
      if (it.isEmpty()) "" else "#$it"
    }
  }
  val fragments = buildList {
    val archive = source.archiveInfo
    val hash = archive?.hashes?.get("sha256")?.let { "sha256=$it" }
               ?: archive?.hashes?.entries?.minByOrNull { it.key }?.let { "${it.key}=${it.value}" }
               ?: archive?.hash
    if (hash != null) add(hash)
    source.subdirectory?.let {
      add("subdirectory=" + URLEncoder.encode(it, StandardCharsets.UTF_8).replace("+", "%20").replace("%2F", "/"))
    }
    if (source.dirInfo?.editable == true) add("egg=$normalizedName")
  }
  if (fragments.isNotEmpty()) url += (if ('#' in url) "&" else "#") + fragments.joinToString("&")
  return if (source.dirInfo?.editable == true) "-e $url" else "$normalizedName @ $url"
}

private fun yamlScalar(value: String): String =
  if (PLAIN_YAML_SCALAR.matches(value) && value.lowercase() !in YAML_RESERVED_SCALARS) value else JsonPrimitive(value).toString()

private val PLAIN_YAML_SCALAR = Regex("[A-Za-z_][A-Za-z0-9_.-]*")
private val YAML_RESERVED_SCALARS = setOf("null", "true", "false", "yes", "no", "on", "off")
private val EXPORT_JSON = Json { ignoreUnknownKeys = true }
private val EXPORT_INVENTORY = """
  import importlib.metadata, json, pathlib, sys
  records = pathlib.Path(sys.prefix) / "conda-meta"
  if not records.is_dir():
      raise FileNotFoundError(records)
  packages = []
  for distribution in importlib.metadata.distributions():
      name = distribution.metadata.get("Name")
      if not name:
          continue
      direct_url = distribution.read_text("direct_url.json")
      packages.append({"name": name, "version": distribution.version,
                       "direct_url": json.loads(direct_url) if direct_url else None})
  print(json.dumps({"conda": [json.loads(record.read_text(encoding="utf-8")) for record in records.glob("*.json")],
                    "python": packages}))
""".trimIndent()

@Serializable
private data class PixiExportInventory(val conda: List<JsonObject>, val python: List<PixiExportDistribution>)

@Serializable
private data class PixiExportDistribution(
  val name: String,
  val version: String,
  @SerialName("direct_url") val directUrl: PixiExportDirectUrl? = null,
)

@Serializable
private data class PixiExportDirectUrl(
  val url: String,
  val subdirectory: String? = null,
  @SerialName("dir_info") val dirInfo: PixiExportDirectory? = null,
  @SerialName("archive_info") val archiveInfo: PixiExportArchive? = null,
  @SerialName("vcs_info") val vcsInfo: PixiExportVcs? = null,
)

@Serializable
private data class PixiExportDirectory(val editable: Boolean = false)

@Serializable
private data class PixiExportArchive(val hash: String? = null, val hashes: Map<String, String> = emptyMap())

@Serializable
private data class PixiExportVcs(
  val vcs: String,
  @SerialName("commit_id") val commitId: String? = null,
  @SerialName("requested_revision") val requestedRevision: String? = null,
)
