package com.intellij.python.sdkConfigurator.frontend

import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.snapshots.SnapshotStateSet
import com.intellij.python.common.tools.ToolId
import com.intellij.python.common.tools.getIcon
import com.intellij.python.sdkConfigurator.common.impl.ModuleDTO
import com.intellij.python.sdkConfigurator.common.impl.ModuleName
import com.intellij.python.sdkConfigurator.common.impl.ModulesDTO
import com.intellij.python.sdkConfigurator.common.impl.ToolIdDTO
import kotlinx.collections.immutable.*
import org.jetbrains.jewel.bridge.icon.fromPlatformIcon
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.IntelliJIconKey

/**
 * UI should display [checkBoxItems] (either enabled or disabled). On each click call [clicked].
 * Result can be taken from [checked]
 */
internal class ModulesViewModel(modulesDTO: ModulesDTO) {
  val icons: PersistentMap<ToolIdDTO, IconKey> = persistentMapOf(*modulesDTO.modules
    .mapNotNull { module ->
      val toolId = module.createdByTool
      val icon = getIcon(ToolId(toolId))?.let { IntelliJIconKey.fromPlatformIcon(it.first, it.second) } ?: return@mapNotNull null
      Pair(toolId, icon)
    }.toTypedArray())

  val checkBoxItems: PersistentList<ModuleDTO> = modulesDTO.modules.toPersistentList()
  val checked: SnapshotStateSet<ModuleName> = mutableStateSetOf()

  // parent -> children
  private val children: ImmutableMap<ModuleName, ImmutableSet<ModuleName>> = checkBoxItems.associate {
    Pair(it.name, it.childModules.toPersistentSet())
  }.toPersistentMap()

  // child -> parent
  private val parents: ImmutableMap<ModuleName, ModuleName> = children.flatMap { (parent, children) ->
    children.map { Pair(it, parent) }
  }.toMap().toImmutableMap()

  fun clicked(module: ModuleName) {
    val what = parents[module] ?: module // Get parent if child module
    val toChange = setOf(what) + children.getOrDefault(what, emptySet()) // Get children if parent
    val checkBoxSet = what !in checked
    if (checkBoxSet) {
      checked.addAll(toChange)
    }
    else {
      checked.removeAll(toChange)
    }
  }
}

