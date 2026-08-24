package com.intellij.python.pytools

import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.python.pytools.PyToolsBundle.message
import com.intellij.python.pytools.impl.detectExecutableOnEel
import com.intellij.python.pytools.services.PyCustomExecutablePaths
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import java.nio.file.Path

fun PyTool.getState(project: Project): PyToolsState.ToolEntry = PyToolsState.getInstance(project).getEntry(this)

fun PyTool.isEnabledOn(project: Project): Boolean = getState(project).enabled

/**
 * A tool is "active" when the user enabled it as an LSP tool, or it is the project's selected type
 * engine. Server start/stop and LSP feature gating key off this (rather than the raw enabled flag)
 * so that a tool acting as the type engine keeps its shared LSP server running and its features on,
 * even though its External Tools enable toggle is locked. See [PyTool.isSelectedAsTypeEngine].
 */
fun PyTool.isActiveOn(project: Project): Boolean = isEnabledOn(project) || isSelectedAsTypeEngine(project)

/**
 * The user-chosen custom executable path for this executable on [eelDescriptor]'s machine, or `null`
 * for auto-detection. Stored per Eel machine in [PyCustomExecutablePaths] (machine-wide, not per project).
 */
fun PyExecutable.getCustomExecutablePath(eelDescriptor: EelDescriptor): Path? =
  PyCustomExecutablePaths.getInstance().get(eelDescriptor, this)

/** Persist (or clear, when `null`) the custom executable path for this executable on [eelDescriptor]'s machine. */
fun PyExecutable.setCustomExecutablePath(eelDescriptor: EelDescriptor, path: Path?): Unit =
  PyCustomExecutablePaths.getInstance().set(eelDescriptor, this, path)

/**
 * Resolve [executableName] in the environment [eelApi] describes: on `PATH` and in the well-known per-user
 * install directories tool installers use (pip's user scripts dir, uv/pipx's `~/.local/bin`, …). Detection
 * goes through [detectExecutableOnEel] so it matches how the executable was installed — a plain `PATH`
 * lookup misses those per-user dirs, which are frequently not on `PATH` on Windows (PY-91493). Not tied
 * to a [PyTool]: also used to find `uv`/`uvx`, which have no tool entry.
 */
suspend fun findExecutableInPath(eelApi: EelApi, executableName: String): Path? =
  detectExecutableOnEel(eelApi, pyExecutableSpec(executableName))

/**
 * After a successful install/upgrade, drops the cached executable detection for every executable this tool ships
 * ([PyTool.executables] — uv also brings `uvx`, pyright its language server), each a separate cache key, so the very
 * next lookup re-detects the freshly installed binaries instead of returning the stale (often negative) result
 * [PyExecutableCache] keeps for its TTL. Without this a just-installed tool can stay invisible to callers — the
 * Settings page and the interpreter widget both resolve availability through the cache — for minutes.
 */
private fun PyTool.invalidateDetectionAfter(result: PyResult<Path>, eel: EelApi) {
  if (result !is Result.Success) return
  val cache = PyExecutableCache.getInstance()
  executables.forEach { cache.invalidate(eel.descriptor, it) }
}

/**
 * Installs this tool's executable into the environment described by [eel] via the tool's [PyTool.manager]
 * (by default a `uv tool install` / pip install; conda uses its own). Returns the resolved executable
 * path, or an error when the tool has no installer ([PyTool.manager] is `null`). On success the tool's cached
 * detection is invalidated ([invalidateDetectionAfter]) so callers see the new binary immediately.
 */
suspend fun PyTool.performToolInstallation(eel: EelApi): PyResult<Path> =
  (manager?.install(this, eel) ?: PyResult.localizedError(message("python.tool.install.no.installer", presentableName)))
    .also { invalidateDetectionAfter(it, eel) }

/**
 * Upgrades this tool to the latest version in the environment described by [eel] via the tool's
 * [PyTool.manager]. Returns the resolved executable path, or an error when the tool has no installer. On success
 * the tool's cached detection is invalidated ([invalidateDetectionAfter]) so a moved/upgraded binary is re-resolved.
 */
suspend fun PyTool.performToolUpgrade(eel: EelApi): PyResult<Path> =
  (manager?.upgrade(this, eel) ?: PyResult.localizedError(message("python.tool.install.no.installer", presentableName)))
    .also { invalidateDetectionAfter(it, eel) }
