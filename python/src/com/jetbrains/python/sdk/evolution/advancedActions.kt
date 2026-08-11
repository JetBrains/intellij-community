package com.jetbrains.python.sdk.evolution

import com.intellij.icons.AllIcons
import com.intellij.openapi.module.Module
import com.intellij.python.sdk.backend.evolution.EvoSelectSdkProvider
import com.intellij.python.sdk.backend.evolution.evoActionLeaf
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.collectAddInterpreterActions
import javax.swing.Icon

internal class AdvancedSelectSdkProvider : EvoSelectSdkProvider {
  override val id: String get() = "advanced"
  override val label: String get() = "Advanced"
  override val icon: Icon get() = AllIcons.Toolwindows.ToolWindowInternal

  override suspend fun loadSections(module: Module): EvoLoadResultDto {
    val actions = collectAddInterpreterActions(ModuleOrProject.ModuleAndProject(module)) { }
    val leaves = actions.map { action ->
      val title = action.templatePresentation.text ?: ""
      evoActionLeaf(title = title, icon = action.templatePresentation.icon ?: icon)
    }
    return EvoLoadResultDto.Ok(listOf(EvoSectionDto(label = null, leaves = leaves)))
  }
}
