// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.intellij.python.pixi.backend.workspace.parsePixiToml
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.NonNls

internal fun parsePixiNativeChannelPriority(text: String): PyResult<String> {
  val table = parsePixiToml(text)
              ?: return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.manifest.invalid"))
  val pixi = table.getTable("tool.pixi") ?: table
  val workspace = pixi.getTable("workspace") ?: pixi.getTable("project")
  val priority = workspace?.get("channel-priority")
  return when (priority) {
    null, "strict" -> PyResult.success("strict")
    "disabled" -> PyResult.success("disabled")
    else -> PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.manifest.invalid"))
  }
}

internal val PIXI_EXTERNAL_UPDATE_OUTPUT_TRANSFORMER: ProcessOutputTransformer<String> = { output ->
  val transaction = if (output.exitCode == 1) decodeNativeUpdate(output.stdoutString) else null
  if (transaction?.isUnsolvable() == true) Result.success(output.stdoutString)
  else ZeroCodeStdoutTransformer(output)
}

internal fun parsePixiExternalNativeUpdates(
  output: String, requestedName: String, installedNames: Set<String>,
  installedPackages: List<PixiInstalledCondaPackage> = emptyList(),
  activeArtifacts: Map<String, PixiNativeArtifact?> = emptyMap(),
): PyResult<List<PixiNativePackage>> {
  val transaction = decodeNativeUpdate(output)
                    ?: return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.updates.invalid.output"))
  if (!transaction.success) {
    return if (transaction.isUnsolvable()) PyResult.success(emptyList())
    else PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.updates.invalid.output"))
  }
  val nativeInventory = installedPackages.filter { it.name in installedNames }
  val installed = nativeInventory.associateBy { it.name }
  if (installed.size != nativeInventory.size || activeArtifacts.any { (name, expected) ->
      expected == null || installed[name]?.artifact?.matches(expected) != true
    }) {
    return PyResult.success(emptyList())
  }
  val actions = transaction.actions ?: return PyResult.success(emptyList())
  val overwrittenNativeNames = installedPackages.filter { it.name !in installedNames }.mapTo(HashSet()) { it.name }
  if ((actions.unlink.asSequence() + actions.link.asSequence()).any {
      it.name in activeArtifacts || it.name in overwrittenNativeNames
    }) {
    return PyResult.success(emptyList())
  }
  val proposed = installed.mapValues { (_, pkg) -> NativeUpdatePackage(pkg.name, pkg.version, pkg.dependencies, pkg.constraints) }
    .toMutableMap()
  actions.unlink.forEach { proposed.remove(it.name) }
  actions.link.forEach { proposed[it.name] = it }
  val visited = HashSet<String>()
  fun validate(name: String): Boolean {
    if (!visited.add(name)) return true
    if (name in activeArtifacts) return true
    val pkg = proposed[name] ?: return false
    val dependencies = pkg.dependencies ?: return false
    fun dependencyName(specification: String): String? = DEPENDENCY_NAME.find(specification.trim())?.groupValues?.get(1)
    for (specification in dependencies) {
      val dependency = dependencyName(specification) ?: return false
      if (dependency.startsWith("__") || !validate(dependency)) return false
    }
    for (specification in pkg.constraints) {
      val dependency = dependencyName(specification) ?: return false
      if (dependency.startsWith("__") || dependency in overwrittenNativeNames || dependency in proposed && !validate(dependency)) return false
    }
    return true
  }
  if (!validate(requestedName) || proposed.keys.any { !validate(it) }) return PyResult.success(emptyList())
  return PyResult.success(actions.link.filter { it.name == requestedName }.map { PixiNativePackage(it.name, it.version) })
}

private fun decodeNativeUpdate(output: String): NativeUpdateTransaction? = try {
  NATIVE_UPDATE_JSON.decodeFromString<NativeUpdateTransaction>(output)
}
catch (_: SerializationException) {
  null
}

private val NATIVE_UPDATE_JSON = Json { ignoreUnknownKeys = true }
@NonNls private const val SOLVER_ERROR_PREFIX = "Could not solve for environment specs"

private fun NativeUpdateTransaction.isUnsolvable(): Boolean =
  !success && solverProblems.isNotEmpty() && logHistory.filter { it.level == "error" || it.level == "critical" }
    .all { it.message.startsWith(SOLVER_ERROR_PREFIX) }

@Serializable
private data class NativeUpdateTransaction(
  val success: Boolean,
  val actions: NativeUpdateActions? = null,
  @SerialName("solver_problems") val solverProblems: List<String> = emptyList(),
  @SerialName("log_history") val logHistory: List<NativeUpdateLog> = emptyList(),
)

@Serializable
private data class NativeUpdateLog(val level: String, val message: String)

@Serializable
private data class NativeUpdateActions(
  @SerialName("LINK") val link: List<NativeUpdatePackage> = emptyList(),
  @SerialName("UNLINK") val unlink: List<NativeUpdatePackage> = emptyList(),
)

@Serializable
private data class NativeUpdatePackage(
  val name: String,
  val version: String,
  @SerialName("depends") val dependencies: List<String>? = null,
  @SerialName("constrains") val constraints: List<String> = emptyList(),
)

private val DEPENDENCY_NAME = Regex("^([A-Za-z0-9_][A-Za-z0-9_.-]*)(?=$|[\\s<>=!~\\[])")
