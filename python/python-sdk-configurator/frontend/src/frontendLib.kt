package com.intellij.python.sdkConfigurator.frontend

import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.snapshots.SnapshotStateSet
import com.intellij.python.common.tools.ToolId
import com.intellij.python.common.tools.getIcon
import com.intellij.python.sdkConfigurator.common.impl.CreateSdkDTO
import com.intellij.python.sdkConfigurator.common.impl.ModuleName
import com.intellij.python.sdkConfigurator.common.impl.ModulesDTO
import com.intellij.python.sdkConfigurator.common.impl.ToolIdDTO
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.jewel.bridge.icon.fromPlatformIcon
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.IntelliJIconKey

/**
 * UI should display [checkBoxItems] (either enabled or disabled). On each click call [clicked].
 * Result can be taken from [checked]
 */
internal class ModulesViewModel(modulesDTO: ModulesDTO) {
  val icons: PersistentMap<ToolIdDTO, IconKey> = persistentMapOf(*modulesDTO.modules.values.mapNotNull {
    when (it) {
      is CreateSdkDTO.ConfigurableModule -> it.createdByTool
      is CreateSdkDTO.SameAs -> null
    }
  }.mapNotNull { toolId ->
    val icon = getIcon(ToolId(toolId))?.let { IntelliJIconKey.fromPlatformIcon(it.first, it.second) } ?: return@mapNotNull null
    Pair(toolId, icon)
  }.toTypedArray())

  val checkBoxItems: PersistentMap<ModuleName, CreateSdkDTO> = persistentMapOf(*modulesDTO.modules
    .map { (moduleName, createSdkInfo) ->
      Pair(moduleName, createSdkInfo)
    }.toTypedArray())
  val checked: SnapshotStateSet<ModuleName> = mutableStateSetOf()
  private val children = mutableMapOf<ModuleName, MutableSet<ModuleName>>()

  init {
    for ((child, createSdkDTO) in modulesDTO.modules) {
      val parent = when (createSdkDTO) {
        is CreateSdkDTO.ConfigurableModule -> continue
        is CreateSdkDTO.SameAs -> createSdkDTO.parentModuleName
      }
      children.getOrPut(parent) { HashSet() }.add(child)
    }
  }

  fun clicked(what: ModuleName) {
    val toChange = setOf(what) + children.getOrDefault(what, emptySet())
    val checkBoxSet = what !in checked
    if (checkBoxSet) {
      checked.addAll(toChange)
    }
    else {
      checked.removeAll(toChange)
    }
  }
}

