@file:Suppress("UnstableApiUsage")

package com.intellij.python.sdk.backend.evolution

import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.common.evolution.EvoLeafKind
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoNodeDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.getOrNull
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString

/**
 * Backend extension point for the "Evo" interpreter widget. Each provider (contributed by a *tool* module —
 * pip/uv/poetry/conda/hatch/…) owns one expandable node and the *layout* of that tool's environments.
 *
 * The central discovery finds every virtualenv under the project's base dirs and hands the list to every
 * provider via [loadSections]; each provider selects the subset it owns (uv: all; pip: non-uv; poetry: its
 * own) and may add tool-specific rows (conda/hatch enumerate their own envs; poetry adds per-version rows).
 *
 * `python-sdk` stays tool-agnostic: it aggregates providers, runs discovery, serializes DTOs, and performs the
 * interpreter switch. Providers never build SDKs or hand-roll display strings/icons.
 */
@ApiStatus.Internal
interface PyEvoEnvironmentProvider {
  /** Stable node id used to dispatch [loadSections]. */
  val id: String

  /** Collapsed node label. */
  val label: @Nls String

  /** Collapsed node icon (this provider's own tool icon). */
  val icon: Icon

  fun getNode(): EvoNodeDto = EvoNodeDto(id = id, label = label, icon = icon.rpcId())

  /**
   * Whether this provider's tool is available on the project's Eel machine. Unavailable providers are dropped
   * from the node list, so an uninstalled tool never shows up. Defaults to always-available.
   */
  suspend fun isAvailable(pyProject: PyProject, fileSystem: FileSystem<PathHolder.Eel>): Boolean = true

  /**
   * Lazily compute this node's sections (layout owned by the provider) when it is expanded. [discovered] is the
   * centrally-found list of virtualenvs under the project's base dirs; providers filter it to the subset they own.
   */
  suspend fun loadSections(pyProject: PyProject, fileSystem: FileSystem<PathHolder.Eel>, discovered: List<DiscoveredVenv>): EvoLoadResultDto

  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<PyEvoEnvironmentProvider> = ExtensionPointName.create("Pythonid.evoEnvironmentProvider")
  }
}

/**
 * A virtualenv found by the central discovery, together with its parsed `pyvenv.cfg` [config] (tool markers +
 * version). No python is executed during discovery; the display [version] comes from `pyvenv.cfg`.
 */
@ApiStatus.Internal
data class DiscoveredVenv(
  val pythonBinary: Path,
  val config: Map<String, String>,
  val version: @NlsSafe String?,
) {
  /** The environment root directory (`…/<venv>/bin/python` → `<venv>`). */
  val venvRoot: Path? get() = pythonBinary.parent?.parent

  /** True when `pyvenv.cfg` carries a `uv` marker (the env was created by uv). */
  val createdByUv: Boolean get() = "uv" in config
}

/**
 * Discovers all virtualenvs directly under [baseDirs], skipping any child in [excludedRoots] (content roots of
 * other modules — we do not descend into inner modules). No python is executed; the display version is read
 * from `pyvenv.cfg`.
 */
@ApiStatus.Internal
suspend fun discoverVenvs(baseDirs: List<Path>, excludedRoots: Set<Path>): List<DiscoveredVenv> = withContext(Dispatchers.IO) {
  val reader = VirtualEnvReader()
  baseDirs.flatMap { baseDir ->
    val children = try {
      baseDir.listDirectoryEntries()
    }
    catch (_: IOException) {
      emptyList()
    }
    children
      .filter { it.isDirectory() && it !in excludedRoots }
      .mapNotNull { dir ->
        val binary = reader.findPythonInPythonRoot(dir) ?: return@mapNotNull null
        val config = parsePyvenvCfg(dir.resolve("pyvenv.cfg"))
        DiscoveredVenv(binary, config, config.pyvenvVersion())
      }
  }
}

private fun parsePyvenvCfg(path: Path): Map<String, String> {
  if (!path.exists()) return emptyMap()
  return try {
    buildMap {
      for (line in Files.readAllLines(path)) {
        val eq = line.indexOf('=')
        if (eq >= 0) put(line.substring(0, eq).trim(), line.substring(eq + 1).trim())
      }
    }
  }
  catch (_: IOException) {
    emptyMap()
  }
}

private fun Map<String, String>.pyvenvVersion(): String? =
  this["version"]?.takeIf { it.isNotBlank() }
  ?: this["version_info"]?.substringBefore(".final")?.takeIf { it.isNotBlank() }

/** Shortens the user home to `~` for display, matching how SDK names are rendered. */
@ApiStatus.Internal
fun Path.toDisplayPath(): @NlsSafe String = FileUtil.getLocationRelativeToUserHome(pathString, false)

/** Builds a SELECT_ENV leaf for a discovered venv; the version comes from `pyvenv.cfg` (or "n/a"). */
@ApiStatus.Internal
fun DiscoveredVenv.toLeaf(icon: Icon): EvoLeafDto {
  val name: @NlsSafe String = venvRoot?.fileName?.toString() ?: pythonBinary.fileName.toString()
  return EvoLeafDto(
    title = name,
    description = pythonBinary.toDisplayPath(),
    secondaryText = version ?: "n/a",
    icon = icon.rpcId(),
    kind = EvoLeafKind.SELECT_ENV,
    ref = PyInterpreterRef.DetectedPath(pythonBinary.pathString),
  )
}

/**
 * Groups discovered venvs by their containing folder into sections. When the list is empty and [addNew] is set,
 * emits a single "Add new environment" section so the node is never an empty popup.
 */
@ApiStatus.Internal
fun List<DiscoveredVenv>.toSectionsGroupedByParent(icon: Icon, addNew: Boolean): List<EvoSectionDto> {
  if (isEmpty()) {
    return if (addNew) listOf(EvoSectionDto(label = null, leaves = emptyList(), addNew = true)) else emptyList()
  }
  return groupBy { it.venvRoot?.parent }.map { (containingFolder, venvs) ->
    EvoSectionDto(
      label = containingFolder?.toDisplayPath(),
      leaves = venvs.map { it.toLeaf(icon) },
      addNew = addNew,
    )
  }
}

/**
 * Builds an ACTION leaf carrying the given (provider-owned) [icon]. Pass [actionId] to make the row *runnable* on
 * the backend (dispatched to [com.intellij.python.sdk.common.evolution.PyEvoSdkApi.performNodeAction]); leave it
 * `null` for a display-only row.
 */
@ApiStatus.Internal
fun evoActionLeaf(title: @Nls String, description: @Nls String? = title, secondaryText: @Nls String? = null, icon: Icon, actionId: String? = null): EvoLeafDto =
  EvoLeafDto(title = title, description = description, secondaryText = secondaryText, icon = icon.rpcId(), kind = EvoLeafKind.ACTION, actionId = actionId)

/**
 * Builds a leaf for a declared-but-not-yet-materialized env (poetry per-version row, hatch declared env). Selecting
 * it creates the env via the tool's create logic and then assigns it; [token] is tool-specific — see
 * [com.intellij.python.sdk.common.evolution.PyInterpreterRef.CreateEnv].
 */
@ApiStatus.Internal
fun evoCreateEnvLeaf(title: @Nls String, token: String, icon: Icon): EvoLeafDto =
  EvoLeafDto(title = title, icon = icon.rpcId(), kind = EvoLeafKind.SELECT_ENV, ref = PyInterpreterRef.CreateEnv(token))

/**
 * Builds a leaf for a *tool-enumerated* environment (conda/hatch/poetry-per-version) identified by [pythonBinary].
 * The interpreter [version] is NOT probed here — pass it only when the tool reports it cheaply; otherwise leave it
 * `null` and the frontend resolves it lazily on hover, so we never spawn a `python --version` process per env.
 * When [pythonBinary] is `null` the environment has no runnable interpreter yet, so a display-only "n/a" ACTION leaf is produced.
 */
@ApiStatus.Internal
fun evoEnvLeaf(title: @Nls String, pythonBinary: Path?, icon: Icon, version: @NlsSafe String? = null): EvoLeafDto {
  if (pythonBinary == null) {
    return evoActionLeaf(title = title, description = null, secondaryText = version ?: "n/a", icon = icon)
  }
  return EvoLeafDto(
    title = title,
    description = pythonBinary.toDisplayPath(),
    secondaryText = version,
    icon = icon.rpcId(),
    kind = EvoLeafKind.SELECT_ENV,
    ref = PyInterpreterRef.DetectedPath(pythonBinary.pathString),
  )
}

/** Convenience for providers that fail softly (tool not installed, etc.). */
@ApiStatus.Internal
fun evoWarning(message: @Nls String): EvoLoadResultDto = EvoLoadResultDto.Warning(message)

@ApiStatus.Internal
fun Path.resolvePythonExecutable(): Path? {
  val candidates = if (SystemInfo.isWindows) listOf(Path.of("bin", "python.exe")) else listOf(Path.of("bin", "python"))
  return candidates.firstNotNullOfOrNull { rel -> resolve(rel).takeIf { it.isExecutable() } }
}

private const val VERSION_PREFIX = "Python "

internal fun String?.parsePythonVersion(): String? =
  this?.trim()?.takeIf { it.startsWith(VERSION_PREFIX) }?.removePrefix(VERSION_PREFIX)?.trim()?.takeIf { it.isNotEmpty() }

@ApiStatus.Internal
suspend fun PythonBinary.getPythonVersion(): @NlsSafe String? {
  val stdout = ExecService().execGetStdout(this, Args("--version")).getOrNull()
  return stdout.parsePythonVersion()
}
