package com.jetbrains.python.poetry.sdk.evolution

import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.python.community.impl.poetry.backend.PoetryPyTool
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.pytools.resolveExecutable
import com.intellij.python.sdk.backend.evolution.DiscoveredVenv
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.defaultVenvDir
import com.intellij.python.sdk.backend.evolution.evoCreateEnvLeaf
import com.intellij.python.sdk.backend.evolution.evoEnvLeaf
import com.intellij.python.sdk.backend.evolution.resolvePythonExecutable
import com.intellij.python.sdk.backend.evolution.toDisplayPath
import com.intellij.python.sdk.backend.evolution.toLeaf
import com.intellij.python.sdk.backend.evolution.toSectionLabel
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.getOrNull
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.packaging.PyVersionSpecifiers
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.evolution.requiresPython
import com.jetbrains.python.sdk.poetry.runPoetry
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.name
import kotlin.io.path.pathString

internal class PoetryEvoEnvironmentProvider : PyEvoEnvironmentProvider {
  override val id: String get() = "Poetry"
  override val label: String get() = "Poetry"
  override val icon: Icon get() = PythonIcons.Python.Origami

  override suspend fun isAvailable(pyProject: EvoPyProject, fileSystem: FileSystem<PathHolder.Eel>): Boolean =
    PoetryPyTool.getInstance().resolveExecutable(fileSystem) != null

  override suspend fun loadSections(pyProject: EvoPyProject, fileSystem: FileSystem<PathHolder.Eel>, discovered: List<DiscoveredVenv>): EvoLoadResultDto {
    val projectDir = pyProject.baseDir
    // Poetry's cache environments, as full env-root paths. Force `virtualenvs.in-project=false` (as the v2 dialog does)
    // so poetry enumerates the cache envs even when an in-project `.venv` exists — otherwise it reports only `.venv`.
    val poetryEnvRoots: List<Path> = runPoetry(projectDir, "env", "list", "--full-path", inProjectEnv = false).getOrNull()
      ?.lineSequence()
      ?.map { Path.of(it.removeSuffix("(Activated)").trim()) }
      ?.filter { it.name.isNotBlank() }
      ?.toList()
      ?: emptyList()

    // (a) In-project: exactly the project's `.venv` (poetry's only in-project location — it can't be `.venv1` nor more
    // than one). Show it if it exists, even if poetry didn't create it, and then hide "add new"; otherwise offer an
    // "add new" that creates the in-project env with a chosen Python version (PyEvoSdkApiProvider: inProjectEnv).
    val inProjectVenv = discovered.firstOrNull { it.venvRoot == defaultVenvDir(projectDir) }
    val inProjectSection = EvoSectionDto(
      label = PySdkBundle.message("evolution.poetry.in.project"),
      leaves = listOfNotNull(inProjectVenv?.toLeaf(icon)),
      addNew = inProjectVenv == null,
      addNewFolderPath = projectDir.pathString,
    )

    // (b) Poetry cache: one row per system-python major version — an existing cache env → select it (points straight at
    // that env's python); otherwise create a poetry cache env from that system Python ([evoCreateEnvLeaf] carries the
    // base python as the token, no folder → inProjectEnv=false). Shown regardless of an in-project `.venv`.
    val eelApi = fileSystem.eelDescriptor?.toEelApi() ?: localEel
    // Only versions the project actually allows: filter by pyproject `requires-python` (+ the >=3.8 venv floor), like uv/pip.
    val spec = PyVersionSpecifiers(requiresPython(projectDir) ?: "")
    val systemPythons = SystemPythonService().findSystemPythons(eelApi)
      .filter { it.pythonInfo.languageLevel.isAtLeast(LanguageLevel.PYTHON38) && spec.isValid(it.pythonInfo.languageLevel) }
      .distinctBy { it.pythonInfo.languageLevel }
      .sortedByDescending { it.pythonInfo.languageLevel }
    val perVersionLeaves = systemPythons.map { sysPython ->
      val versionStr = sysPython.pythonInfo.languageLevel.toPythonVersion()
      // These rows are identified by the Python they hold rather than by an env name, so spell that out the way the
      // add-new version rows do ("Python 3.13") instead of showing a bare number. Only the label changes: the lookup
      // below still matches on the plain version, which is what poetry puts at the end of the cache env's folder name.
      val title = PySdkBundle.message("evolution.python.version", versionStr)
      val existingBinary = poetryEnvRoots.firstOrNull { it.name.endsWith(versionStr) }?.resolvePythonExecutable()
      if (existingBinary != null) evoEnvLeaf(title = title, pythonBinary = existingBinary, icon = icon)
      else evoCreateEnvLeaf(title = title, token = sysPython.pythonBinary.pathString, icon = icon)
    }
    val virtualenvsDir = runPoetry(projectDir, "config", "virtualenvs.path").getOrNull()?.trim()?.takeIf { it.isNotBlank() }
      ?.let { Path.of(it) }
    val cacheSection = if (perVersionLeaves.isEmpty()) null
    else EvoSectionDto(
      label = virtualenvsDir?.toSectionLabel(),
      labelTooltip = virtualenvsDir?.toDisplayPath(),
      leaves = perVersionLeaves,
    )

    return EvoLoadResultDto.Ok(listOf(inProjectSection) + listOfNotNull(cacheSection))
  }
}
