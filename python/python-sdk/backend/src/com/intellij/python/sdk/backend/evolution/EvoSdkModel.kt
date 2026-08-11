@file:Suppress("UnstableApiUsage")

package com.intellij.python.sdk.backend.evolution

import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.common.evolution.EvoLeafKind
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoNodeDto
import com.intellij.python.sdk.common.evolution.EvoSdkDto
import com.intellij.python.sdk.common.evolution.EvoSdkOptionDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.intellij.python.sdk.common.evolution.getAddress
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.getOrNull
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.venvReader.VirtualEnvReader
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable

/**
 * Backend-side extension point for the "Evo" interpreter widget. Each provider (contributed by a *tool*
 * module — pip/uv/poetry/conda/hatch/…) knows nothing but its own tool: it presents one expandable node,
 * lazily lists that tool's environments, and optionally recognizes the module's current interpreter.
 *
 * `python-sdk` itself is tool-agnostic: it only aggregates providers and ships their frontend-safe DTOs
 * (icons travel as [com.intellij.ide.ui.icons.IconId] via [Icon.rpcId]).
 */
@ApiStatus.Internal
interface EvoSelectSdkProvider {
  /** Stable node id used to dispatch [loadSections]. */
  val id: String

  /** Collapsed node label. */
  val label: @Nls String

  /** Collapsed node icon (this provider's own tool icon). */
  val icon: Icon

  fun getNode(): EvoNodeDto = EvoNodeDto(id = id, label = label, icon = icon.rpcId())

  /** Lazily compute this node's sections for [module] when it is expanded. */
  suspend fun loadSections(module: Module): EvoLoadResultDto

  /**
   * If [sdk] is an interpreter this tool recognizes, present it (used for the widget's "current" label).
   * Returns `null` when this tool does not own [sdk].
   */
  suspend fun parseModuleSdk(module: Module, sdk: Sdk): EvoSdk? = null

  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<EvoSelectSdkProvider> = ExtensionPointName.create("Pythonid.evoTreeElementProvider")
  }
}

/**
 * Backend interpreter model handed to providers. Holds a concrete Swing [icon] (the provider's own tool
 * icon); [toDto] serializes it via [Icon.rpcId] so no tool identity leaks into the shared modules.
 */
@ApiStatus.Internal
data class EvoSdk(
  val target: String = "local",
  val icon: Icon,
  val name: String?,
  val pythonBinaryPath: PythonBinary?,
  val pythonVersion: String? = null,
  val options: List<EvoSdkOptionDto> = emptyList(),
) {
  fun withVersion(version: String?): EvoSdk = copy(pythonVersion = version)

  fun toDto(): EvoSdkDto = EvoSdkDto(
    target = target,
    name = name,
    pythonBinaryPath = pythonBinaryPath?.toString(),
    pythonVersion = pythonVersion,
    icon = icon.rpcId(),
    options = options,
  )
}

/** Converts an [EvoSdk] into a SELECT_ENV leaf, computing the Python version (backend-only) for display. */
@ApiStatus.Internal
suspend fun EvoSdk.toSelectLeaf(): EvoLeafDto {
  val version = pythonBinaryPath?.getPythonVersion()
  val dto = withVersion(version).toDto()
  return EvoLeafDto(
    title = dto.getAddress(),
    description = dto.pythonBinaryPath,
    secondaryText = version,
    icon = dto.icon,
    kind = EvoLeafKind.SELECT_ENV,
    sdk = dto,
  )
}

/** Builds a display-only ACTION leaf carrying the given (provider-owned) [icon]. */
@ApiStatus.Internal
fun evoActionLeaf(title: @Nls String, description: @Nls String? = title, secondaryText: @Nls String? = null, icon: Icon): EvoLeafDto =
  EvoLeafDto(title = title, description = description, secondaryText = secondaryText, icon = icon.rpcId(), kind = EvoLeafKind.ACTION)

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
suspend fun PythonBinary.getPythonVersion(): String? {
  val stdout = ExecService().execGetStdout(this, Args("--version")).getOrNull()
  return stdout.parsePythonVersion()
}

/** Generic, tool-agnostic scan for `.../bin/python` executables directly under the project root. */
@ApiStatus.Internal
fun findProjectPythonExecutables(module: Module): List<Path> {
  val root = Path.of(module.project.basePath ?: return emptyList())
  return Files.walk(root, 1, FileVisitOption.FOLLOW_LINKS)
    .use { stream ->
      stream.filter { it.isDirectory() }.map { it.resolvePythonExecutable() }.filter { it != null }.map { it!! }.sorted().toList()
    }
}

/**
 * The venv-style presentation reused by any tool that surfaces project-local virtualenvs (pip, uv, …):
 * groups discovered interpreters by their containing folder and adds a placeholder default `.venv` row.
 * The [icon] is supplied by the calling provider — this helper carries no tool identity of its own.
 */
@ApiStatus.Internal
suspend fun venvStyleSections(module: Module, icon: Icon): List<EvoSectionDto> {
  val environments = findProjectPythonExecutables(module).map { EvoSdk(icon = icon, name = it.resolvePythonHomeName(), pythonBinaryPath = it) }
  val byFolder = environments.groupBy { it.pythonBinaryPath?.parent?.parent?.parent }.toMutableMap()
  byFolder.putIfAbsent(
    module.baseDir?.path?.let { Path.of(it) },
    listOf(EvoSdk(icon = icon, name = VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME, pythonBinaryPath = null)),
  )
  return byFolder.map { (basePath, sdks) ->
    EvoSectionDto(label = basePath?.toString() ?: "undefined", leaves = sdks.map { it.toSelectLeaf() }, addNew = true)
  }
}

private fun Path.resolvePythonHomeName(): String = parent?.parent?.fileName?.toString() ?: fileName.toString()
