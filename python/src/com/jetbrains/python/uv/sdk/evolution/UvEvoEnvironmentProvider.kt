package com.jetbrains.python.uv.sdk.evolution

import com.intellij.python.pytools.resolveExecutable
import com.intellij.python.sdk.backend.evolution.DiscoveredVenv
import com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.toSectionsGroupedByParent
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.uv.backend.UvPyTool
import com.intellij.python.uv.common.icons.PythonUvCommonIcons
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import javax.swing.Icon

internal class UvEvoEnvironmentProvider : PyEvoEnvironmentProvider {
  override val id: String get() = "uv"
  override val label: String get() = "uv"
  override val icon: Icon get() = PythonUvCommonIcons.UV

  override suspend fun isAvailable(pyProject: PyProject, fileSystem: FileSystem<PathHolder.Eel>): Boolean =
    UvPyTool.getInstance().resolveExecutable(fileSystem) != null

  // uv works with any virtualenv, so it shows all discovered environments.
  override suspend fun loadSections(pyProject: PyProject, fileSystem: FileSystem<PathHolder.Eel>, discovered: List<DiscoveredVenv>): EvoLoadResultDto =
    EvoLoadResultDto.Ok(discovered.toSectionsGroupedByParent(icon, addNew = true, baseDir = pyProject.baseDir))
}
