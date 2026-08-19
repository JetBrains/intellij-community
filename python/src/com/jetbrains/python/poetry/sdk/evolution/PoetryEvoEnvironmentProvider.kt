package com.jetbrains.python.poetry.sdk.evolution

import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.python.community.impl.poetry.backend.PoetryPyTool
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.pytools.resolveExecutable
import com.intellij.python.sdk.backend.evolution.DiscoveredVenv
import com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.defaultVenvDir
import com.intellij.python.sdk.backend.evolution.evoCreateEnvLeaf
import com.intellij.python.sdk.backend.evolution.evoEnvLeaf
import com.intellij.python.sdk.backend.evolution.resolvePythonExecutable
import com.intellij.python.sdk.backend.evolution.toLeaf
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.getOrNull
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.packaging.PyVersionSpecifiers
import com.jetbrains.python.project.PyProject
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

  override suspend fun isAvailable(pyProject: PyProject, fileSystem: FileSystem<PathHolder.Eel>): Boolean =
    PoetryPyTool.getInstance().resolveExecutable(fileSystem) != null

  override suspend fun loadSections(pyProject: PyProject, fileSystem: FileSystem<PathHolder.Eel>, discovered: List<DiscoveredVenv>): EvoLoadResultDto {
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
      val existingBinary = poetryEnvRoots.firstOrNull { it.name.endsWith(versionStr) }?.resolvePythonExecutable()
      if (existingBinary != null) evoEnvLeaf(title = versionStr, pythonBinary = existingBinary, icon = icon)
      else evoCreateEnvLeaf(title = versionStr, token = sysPython.pythonBinary.pathString, icon = icon)
    }
    val virtualenvsPath = runPoetry(projectDir, "config", "virtualenvs.path").getOrNull()?.trim()?.takeIf { it.isNotBlank() }
      ?.let { FileUtil.getLocationRelativeToUserHome(it, false) }
    val cacheSection = if (perVersionLeaves.isEmpty()) null else EvoSectionDto(label = virtualenvsPath, leaves = perVersionLeaves)

    return EvoLoadResultDto.Ok(listOf(inProjectSection) + listOfNotNull(cacheSection))
  }
}
