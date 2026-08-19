package com.jetbrains.python.poetry.sdk.evolution

import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.python.community.impl.poetry.backend.PoetryPyTool
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.pytools.resolveExecutable
import com.intellij.python.sdk.backend.evolution.DiscoveredVenv
import com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.evoCreateEnvLeaf
import com.intellij.python.sdk.backend.evolution.evoEnvLeaf
import com.intellij.python.sdk.backend.evolution.resolvePythonExecutable
import com.intellij.python.sdk.backend.evolution.toSectionsGroupedByParent
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.jetbrains.python.getOrNull
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
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
    // Poetry's own environments (in-project + poetry cache), as full env-root paths.
    val poetryEnvRoots: List<Path> = runPoetry(projectDir, "env", "list", "--full-path").getOrNull()
      ?.lineSequence()
      ?.map { Path.of(it.removeSuffix("(Activated)").trim()) }
      ?.filter { it.name.isNotBlank() }
      ?.toList()
      ?: emptyList()

    // (a) Found envs (from central discovery) that poetry recognizes as its own — usually the in-project one.
    val poetryRootSet = poetryEnvRoots.toHashSet()
    val ownFound = discovered.filter { it.venvRoot in poetryRootSet }
    val foundSections = ownFound.toSectionsGroupedByParent(icon, addNew = false)

    // (b) One row per system-python major version: existing poetry env for that version → select it; else create a
    // poetry env from that system Python on click ([evoCreateEnvLeaf] carries the base python as the token).
    val eelApi = fileSystem.eelDescriptor?.toEelApi() ?: localEel
    val systemPythons = SystemPythonService().findSystemPythons(eelApi)
      .distinctBy { it.pythonInfo.languageLevel }
      .sortedByDescending { it.pythonInfo.languageLevel }
    val perVersionLeaves = systemPythons.map { sysPython ->
      val versionStr = sysPython.pythonInfo.languageLevel.toPythonVersion()
      val existingBinary = poetryEnvRoots.firstOrNull { it.name.endsWith(versionStr) }?.resolvePythonExecutable()
      if (existingBinary != null) evoEnvLeaf(title = versionStr, pythonBinary = existingBinary, icon = icon)
      else evoCreateEnvLeaf(title = versionStr, token = sysPython.pythonBinary.pathString, icon = icon)
    }
    // Per-version rows belong to poetry's virtualenvs cache dir, not to the project folder — give them their own header.
    val virtualenvsPath = runPoetry(projectDir, "config", "virtualenvs.path").getOrNull()?.trim()?.takeIf { it.isNotBlank() }
      ?.let { FileUtil.getLocationRelativeToUserHome(it, false) }
    val perVersionSection = if (perVersionLeaves.isEmpty()) null else EvoSectionDto(label = virtualenvsPath, leaves = perVersionLeaves)

    return EvoLoadResultDto.Ok(foundSections + listOfNotNull(perVersionSection))
  }
}
