@file:Suppress("UnstableApiUsage")

package com.intellij.python.sdk.backend.evolution

import com.intellij.icons.AllIcons
import com.intellij.openapi.module.Module
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import javax.swing.Icon

/**
 * The generic "autoconfigure" node: suggested interpreter setups from [PyProjectSdkConfigurationExtension].
 * This is not tool-specific, so it stays in `python-sdk`.
 */
internal class AutoconfigEvoSelectSdkProvider : EvoSelectSdkProvider {
  override val id: String get() = "autoconfig"
  override val label: String get() = "Autoconfigure"
  override val icon: Icon get() = AllIcons.General.Layout

  private val ratingIcons = listOf(
    AllIcons.Ide.Rating, AllIcons.Ide.Rating4, AllIcons.Ide.Rating3, AllIcons.Ide.Rating2, AllIcons.Ide.Rating1,
  )

  override suspend fun loadSections(module: Module): EvoLoadResultDto {
    val options = PyProjectSdkConfigurationExtension.findAllSortedForModule(module)
    val leaves = options.mapIndexed { index, option ->
      val intention = option.createSdkInfo.intentionName
      evoActionLeaf(title = intention, icon = ratingIcons.getOrElse(index) { AllIcons.Ide.Rating1 })
    }
    return EvoLoadResultDto.Ok(listOf(EvoSectionDto(label = null, leaves = leaves)))
  }
}
