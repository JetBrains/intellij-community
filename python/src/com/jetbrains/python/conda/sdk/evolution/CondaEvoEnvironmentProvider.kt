package com.jetbrains.python.conda.sdk.evolution

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.impl.conda.CondaPyTool
import com.intellij.python.community.impl.conda.PyCondaBundle
import com.intellij.python.community.impl.conda.icons.PythonCommunityImplCondaIcons
import com.intellij.python.pytools.resolveExecutable
import com.intellij.python.pytools.runTool
import com.intellij.python.sdk.backend.evolution.DiscoveredVenv
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.evoEnvLeaf
import com.intellij.python.sdk.backend.evolution.evoWarning
import com.intellij.python.sdk.backend.evolution.resolvePythonExecutable
import com.intellij.python.sdk.backend.evolution.toDisplayPath
import com.intellij.python.sdk.backend.evolution.toSectionLabel
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.jetbrains.python.getOrNull
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
  override suspend fun isAvailable(pyProject: EvoPyProject, fileSystem: FileSystem<PathHolder.Eel>): Boolean =
    CondaPyTool.getInstance().resolveExecutable(fileSystem) != null

  override suspend fun loadSections(pyProject: EvoPyProject, fileSystem: FileSystem<PathHolder.Eel>, discovered: List<DiscoveredVenv>): EvoLoadResultDto {
    val conda = CondaPyTool.getInstance()
    // Presence check only: the rows are grouped by where each env lives, not by where conda itself is installed.
    conda.resolveExecutable(fileSystem) ?: return evoWarning(PyCondaBundle.message("evolution.conda.executable.is.not.found"))
    val stdout = conda.runTool(fileSystem, null, null, "env", "list").getOrNull()
                 ?: return EvoLoadResultDto.Ok(emptyList())
    val envs = parseEnvList(stdout)
    // One section per folder the envs actually live in, labelled with that folder — the same grouping the venv-based tools
    // use. Conda keeps a base env at the installation root and the named ones under its `envs/`, so this separates the two
    // (and keeps several conda installations apart) instead of filing everything under one heading.
    val envSections = envs.groupBy { it.root.parent }.map { (containingFolder, group) ->
      EvoSectionDto(
        label = containingFolder?.toSectionLabel(),
        labelTooltip = containingFolder?.toDisplayPath(),
        leaves = group.map { evoEnvLeaf(it.name, it.binary, icon) },
      )
    }
    // Conda envs are named (not folder-based): propose a free env name derived from the project so the widget's
    // in-place "add new" can offer name + Python version (PyEvoSdkApiProvider fills the version options), instead of
    // the modal dialog. addNewFolderPath carries the proposed name here.
    val proposedName = firstFreeCondaEnvName(pyProject.baseDir.fileName?.toString() ?: "conda", envs.mapTo(mutableSetOf()) { it.name })
    val addNewSection = EvoSectionDto(label = null, leaves = emptyList(), addNew = true, addNewFolderPath = proposedName)
    return EvoLoadResultDto.Ok(envSections + addNewSection)
  }

  /** First conda env name not already taken: `base`, then `base-1`, `base-2`, … */
  private fun firstFreeCondaEnvName(base: String, existing: Set<String>): String {
    if (base !in existing) return base
    var i = 1
    while ("$base-$i" in existing) i++
    return "$base-$i"
  }

  /**
   * One environment from `conda env list`: its [name], the [root] directory it lives in (which is what the rows are grouped
   * by), and its interpreter — null when the env has no runnable python, which renders as a display-only "n/a" row.
   */
  private data class CondaEnv(val name: @NlsSafe String, val root: Path, val binary: Path?)

  private fun parseEnvList(stdout: String): List<CondaEnv> =
    stdout.trim().lines()
      .filter { !it.startsWith('#') }
      .mapNotNull { line ->
        val parts = line.split("\\s+".toRegex())
        val pathStr = parts.lastOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val root = Path.of(pathStr)
        // An env created with `-p` has no name column, so the line starts with the marker/path: fall back to the dir name.
        val realName = parts.first().takeIf { it.isNotBlank() } ?: root.name
        CondaEnv(realName, root, root.resolvePythonExecutable())
      }
}
