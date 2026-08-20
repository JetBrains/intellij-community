package com.intellij.python.venv.evolution

import com.intellij.python.sdk.backend.evolution.DiscoveredVenv
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.toSectionsGroupedByParent
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.venv.icons.PythonVenvIcons
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import javax.swing.Icon

/** Contributes the generic "pip" (virtualenv) node — plain venvs without a `uv` marker. Always available. */
internal class VenvEvoEnvironmentProvider : PyEvoEnvironmentProvider {
  override val id: String get() = "pip"
  override val label: String get() = "pip"
  override val icon: Icon get() = PythonVenvIcons.VirtualEnv

  override suspend fun loadSections(pyProject: EvoPyProject, fileSystem: FileSystem<PathHolder.Eel>, discovered: List<DiscoveredVenv>): EvoLoadResultDto =
    EvoLoadResultDto.Ok(discovered.filterNot { it.createdByUv }.toSectionsGroupedByParent(icon, addNew = true, baseDir = pyProject.baseDir))
}
