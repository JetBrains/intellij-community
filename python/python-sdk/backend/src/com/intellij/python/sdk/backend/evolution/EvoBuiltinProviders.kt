@file:Suppress("UnstableApiUsage")

package com.intellij.python.sdk.backend.evolution

import com.intellij.icons.AllIcons
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import javax.swing.Icon

/**
 * The generic "autoconfigure" node: suggested interpreter setups from [PyProjectSdkConfigurationExtension].
 * This is not tool-specific, so it stays in `python-sdk`.
 */
internal class AutoconfigEvoEnvironmentProvider : PyEvoEnvironmentProvider {
  override val id: String get() = "autoconfig"
  override val label: String get() = "Autoconfigure"
  override val icon: Icon get() = AllIcons.General.Layout

  private val ratingIcons = listOf(
    AllIcons.Ide.Rating, AllIcons.Ide.Rating4, AllIcons.Ide.Rating3, AllIcons.Ide.Rating2, AllIcons.Ide.Rating1,
  )

  override suspend fun loadSections(pyProject: PyProject, fileSystem: FileSystem<PathHolder.Eel>, discovered: List<DiscoveredVenv>): EvoLoadResultDto {
    val options = PyProjectSdkConfigurationExtension.findAllSortedForModule(pyProject.residesOnModule)
    val leaves = options.mapIndexed { index, option ->
      val intention = option.createSdkInfo.intentionName
      evoActionLeaf(title = intention, icon = ratingIcons.getOrElse(index) { AllIcons.Ide.Rating1 })
    }
    return EvoLoadResultDto.Ok(listOf(EvoSectionDto(label = null, leaves = leaves)))
  }
}
