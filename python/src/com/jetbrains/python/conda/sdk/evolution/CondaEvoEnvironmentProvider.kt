package com.jetbrains.python.conda.sdk.evolution

import com.intellij.python.community.impl.conda.CondaPyTool
import com.intellij.python.community.impl.conda.PyCondaBundle
import com.intellij.python.community.impl.conda.icons.PythonCommunityImplCondaIcons
import com.intellij.python.pytools.resolveExecutable
import com.intellij.python.pytools.runTool
import com.intellij.python.sdk.backend.evolution.DiscoveredVenv
import com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.evoEnvLeaf
import com.intellij.python.sdk.backend.evolution.evoWarning
import com.intellij.python.sdk.backend.evolution.resolvePythonExecutable
import com.intellij.python.sdk.backend.evolution.toDisplayPath
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.jetbrains.python.getOrNull
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.name

internal class CondaEvoEnvironmentProvider : PyEvoEnvironmentProvider {
  override val id: String get() = "Conda"
  override val label: String get() = "Conda"
  override val icon: Icon get() = PythonCommunityImplCondaIcons.Anaconda

  // Resolve via the tool's PyExecutableCache (custom-path store + conda-specific search paths such as
  // ~/miniconda3/bin), so conda installed outside PATH is still detected.
  override suspend fun isAvailable(pyProject: PyProject, fileSystem: FileSystem<PathHolder.Eel>): Boolean =
    CondaPyTool.getInstance().resolveExecutable(fileSystem) != null

  override suspend fun loadSections(pyProject: PyProject, fileSystem: FileSystem<PathHolder.Eel>, discovered: List<DiscoveredVenv>): EvoLoadResultDto {
    val conda = CondaPyTool.getInstance()
    val condaExecutable = conda.resolveExecutable(fileSystem)
                          ?: return evoWarning(PyCondaBundle.message("evolution.conda.executable.is.not.found"))
    val stdout = conda.runTool(fileSystem, null, null, "env", "list").getOrNull()
                 ?: return EvoLoadResultDto.Ok(emptyList())
    val envs = parseEnvList(stdout)
    val leaves = envs.map { (name, binary) -> evoEnvLeaf(name, binary, icon) }
    // Conda envs are named (not folder-based): propose a free env name derived from the project so the widget's
    // in-place "add new" can offer name + Python version (PyEvoSdkApiProvider fills the version options), instead of
    // the modal dialog. addNewFolderPath carries the proposed name here.
    val proposedName = firstFreeCondaEnvName(pyProject.baseDir.fileName?.toString() ?: "conda", envs.mapTo(mutableSetOf()) { it.first })
    val sections = listOf(
      EvoSectionDto(label = condaExecutable.path.toDisplayPath(), leaves = leaves),
      EvoSectionDto(label = null, leaves = emptyList(), addNew = true, addNewFolderPath = proposedName),
    )
    return EvoLoadResultDto.Ok(sections)
  }

  /** First conda env name not already taken: `base`, then `base-1`, `base-2`, … */
  private fun firstFreeCondaEnvName(base: String, existing: Set<String>): String {
    if (base !in existing) return base
    var i = 1
    while ("$base-$i" in existing) i++
    return "$base-$i"
  }

  private fun parseEnvList(stdout: String): List<Pair<String, Path?>> =
    stdout.trim().lines()
      .filter { !it.startsWith('#') }
      .mapNotNull { line ->
        val parts = line.split("\\s+".toRegex())
        val pathStr = parts.lastOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val path = Path.of(pathStr)
        val realName = parts.first().takeIf { it.isNotBlank() } ?: path.name
        realName to path.resolvePythonExecutable()
      }
}
