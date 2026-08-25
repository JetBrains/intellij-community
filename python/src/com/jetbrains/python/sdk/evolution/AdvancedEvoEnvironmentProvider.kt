package com.jetbrains.python.sdk.evolution

import com.intellij.icons.AllIcons
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.sdk.common.evolution.EvoNodeKind
import com.intellij.python.sdk.backend.evolution.DiscoveredVenv
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.evoActionLeaf
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoNodeIds
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.collectAddInterpreterActions
import javax.swing.Icon

/** The "advanced" node: the full set of add-interpreter actions. Not tool-specific. */
internal class AdvancedEvoEnvironmentProvider : PyEvoEnvironmentProvider {
  override val toolId: ToolId get() = ToolId(EvoNodeIds.ADVANCED)
  override val nodeKind: EvoNodeKind get() = EvoNodeKind.ADVANCED
  override val label: String get() = "Advanced"
  override val icon: Icon get() = AllIcons.Toolwindows.ToolWindowInternal

  override suspend fun loadSections(pyProject: EvoPyProject, fileSystem: FileSystem<PathHolder.Eel>, discovered: List<DiscoveredVenv>): EvoLoadResultDto {
    val actions = collectAddInterpreterActions(ModuleOrProject.ModuleAndProject(pyProject.module)) { }
    // Serialize each add-interpreter action by its stable index; the same list is re-collected on click to run it
    // (see PyEvoSdkApiProvider.performNodeAction).
    val leaves = actions.mapIndexed { index, action ->
      val title = action.templatePresentation.text ?: ""
      evoActionLeaf(title = title, icon = action.templatePresentation.icon ?: icon, actionId = index.toString())
    }
    return EvoLoadResultDto.Ok(listOf(EvoSectionDto(label = null, leaves = leaves)))
  }
}
