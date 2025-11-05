package com.intellij.python.sdkConfigurator.frontend

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateSet
import com.intellij.python.common.tools.ToolId
import com.intellij.python.common.tools.getIcon
import com.intellij.python.sdkConfigurator.common.impl.ModuleDTO
import com.intellij.python.sdkConfigurator.common.impl.ModuleName
import com.intellij.python.sdkConfigurator.common.impl.ModulesDTO
import com.intellij.python.sdkConfigurator.common.impl.ToolIdDTO
import kotlinx.collections.immutable.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.bridge.icon.fromPlatformIcon
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.IntelliJIconKey
import kotlin.time.Duration.Companion.milliseconds

/**
 * UI should display [filteredModules] (either enabled or disabled). On each click call [clicked].
 * Show filter from [moduleFilter]
 * Result can be taken from [checkedModules].
 * While composable is displayed. call [processFilterUpdates]
 */
internal class ModulesViewModel(modulesDTO: ModulesDTO) {
  val icons: ImmutableMap<ToolIdDTO, IconKey> = persistentMapOf(*modulesDTO.modules
    .mapNotNull { module ->
      val toolId = module.createdByTool
      val icon = getIcon(ToolId(toolId))?.let { IntelliJIconKey.fromPlatformIcon(it.first, it.second) } ?: return@mapNotNull null
      Pair(toolId, icon)
    }.toTypedArray())

  private val modules: List<ModuleDTO> = modulesDTO.modules.sortedBy { it.name }

  var filteredModules: List<ModuleDTO> by mutableStateOf(modules)
  val checkedModules: SnapshotStateSet<ModuleName> = mutableStateSetOf()
  val moduleFilter = TextFieldState()

  // parent -> children
  private val children: ImmutableMap<ModuleName, ImmutableSet<ModuleName>> = filteredModules.associate {
    Pair(it.name, it.childModules.toPersistentSet())
  }.toPersistentMap()

  // child -> parent
  private val parents: ImmutableMap<ModuleName, ModuleName> = children.flatMap { (parent, children) ->
    children.map { Pair(it, parent) }
  }.toMap().toImmutableMap()

  fun clicked(module: ModuleName) {
    val what = parents[module] ?: module // Get parent if child module
    val toChange = setOf(what) + children.getOrDefault(what, emptySet()) // Get children if parent
    val checkBoxSet = what !in checkedModules
    if (checkBoxSet) {
      checkedModules.addAll(toChange)
    }
    else {
      checkedModules.removeAll(toChange)
    }
  }

  @OptIn(FlowPreview::class)
  suspend fun processFilterUpdates() {
    snapshotFlow { moduleFilter.text }
      .debounce(500.milliseconds)
      .collectLatest { filter ->
        withContext(Dispatchers.Default) {
          val filter = filter.trim()
          filteredModules = if (filter.isEmpty()) modules else modules.filter { filter in it.name }
        }
      }
  }
}

