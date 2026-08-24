// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.uv.backend.cli.uv

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.pytools.runtime.PyToolRuntime
import com.intellij.python.uv.backend.PyUvBundle
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Tolerant of fields uv adds: this parses the entries we use, not the whole shape of uv's output. */
private val UV_JSON: Json = Json { ignoreUnknownKeys = true }

/** The `major.minor.patch` of a [UvPythonEntry], as uv itself split it. */
@Serializable
data class UvPythonVersionParts(
  val major: Int,
  val minor: Int,
  val patch: Int,
) {
  /** The language-level form, which is the granularity `uv venv --python` resolves at: `3.14`. */
  val languageLevel: @NlsSafe String get() = "$major.$minor"
}

/**
 * One entry of `uv python list`: an interpreter uv knows of, whether it is on this machine or only available to fetch.
 */
@Serializable
data class UvPythonEntry(
  /** uv's own identifier, e.g. `cpython-3.14.5-macos-aarch64-none`. */
  val key: @NlsSafe String,
  /** The version as uv reports it, pre-release suffix included: `3.14.5`, `3.15.0b4`. */
  val version: @NlsSafe String,
  /**
   * The same version already split by uv, which is why it is read rather than parsed out of [version]: a pre-release
   * (`3.15.0b4`) is not a plain dotted triple, so grouping by major.minor off the string means re-implementing uv's
   * own parsing.
   */
  @SerialName("version_parts")
  val versionParts: UvPythonVersionParts,
  /** The interpreter binary, or `null` when uv only knows where to download this version from. */
  val path: @NlsSafe String? = null,
  /** What [path] points at, when it is a symlink. */
  val symlink: @NlsSafe String? = null,
  /** Where uv would fetch it from; `null` for one already installed. */
  val url: @NlsSafe String? = null,
  /** `cpython`, `pypy`, … */
  val implementation: @NlsSafe String,
  /** `default` or `freethreaded`. */
  val variant: @NlsSafe String,
) {
  /** True when uv would have to download this interpreter before an environment could be created from it. */
  val isDownloadable: Boolean get() = path == null && url != null

  /** True for a free-threaded (no-GIL) build. */
  val isFreeThreaded: Boolean get() = variant == FREE_THREADED_VARIANT
}

// Top level rather than a companion of the class it belongs to: the serialization plugin puts the generated
// `serializer()` on whatever companion the class declares, so a private one makes it unreachable and decoding dies with
// an IllegalAccessError at runtime.
private const val FREE_THREADED_VARIANT: String = "freethreaded"

/**
 * Parses `uv python list --output-format json`.
 *
 * Separate from the call so it can be tested against captured output: the text format this replaces was parsed with a
 * regex that kept only version numbers, which is why paths and downloadable entries were invisible.
 */
internal fun parseUvPythonList(stdout: String): PyResult<List<UvPythonEntry>> =
  try {
    PyResult.success(UV_JSON.decodeFromString<List<UvPythonEntry>>(stdout))
  }
  catch (e: SerializationException) {
    PyResult.localizedError(PyUvBundle.message("uv.python.list.unparseable", e.message ?: ""))
  }

/**
 * Manage Python versions and installations
 */
@Suppress("unused")
class UvPython(runtime: PyToolRuntime) : UvCommand("python", runtime) {
  /**
   * List the Python installations uv knows of — those on this machine and, unless [onlyInstalled], those it could fetch.
   *
   * Asks for JSON rather than uv's table, so each entry keeps its path, variant and download URL instead of being
   * reduced to a version number.
   *
   * @param request uv's own positional version request (`>=3.10,<3.14`, `3.12`, `pypy@3.11`), which uv parses itself and
   *   understands as PEP 440 — so a Poetry-style caret has to be expanded before it gets here or uv matches nothing.
   */
  suspend fun list(
    request: String? = null,
    onlyInstalled: Boolean? = null,
    allVersions: Boolean? = null,
  ): PyResult<List<UvPythonEntry>> {
    val arguments = buildList {
      add("list")
      request?.let { add(it) }
      addAll(listOf(onlyInstalled to "--only-installed", allVersions to "--all-versions").makeOptions())
      addAll(listOf("--output-format", "json"))
    }
    val stdout = executeAndHandleErrors(*arguments.toTypedArray(), transformer = ZeroCodeStdoutTransformer)
      .getOr { return it }
    return parseUvPythonList(stdout)
  }

  /**
   * Download and install Python versions
   */
  suspend fun install(): PyResult<Unit> = TODO()

  /**
   * Upgrade installed Python versions
   */
  suspend fun upgrade(): PyResult<Unit> = TODO()

  /**
   * Search for a Python installation
   */
  suspend fun find(): PyResult<String> = TODO()

  /**
   * Pin to a specific Python version
   */
  suspend fun pin(): PyResult<String> = TODO()

  /**
   * Show the uv Python installation directory
   */
  suspend fun dir(): PyResult<String> {
    return executeAndHandleErrors("dir", transformer = ZeroCodeStdoutTransformer)
  }

  /**
   * Uninstall Python versions
   */
  suspend fun uninstall(): PyResult<Unit> = TODO()

  /**
   * Ensure that the Python executable directory is on the PATH
   */
  suspend fun updateShell(): PyResult<Unit> = TODO()
}
