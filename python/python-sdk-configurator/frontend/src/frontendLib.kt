package com.intellij.python.sdkConfigurator.frontend

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.state.ToggleableState
import com.intellij.python.common.tools.ToolId
import com.intellij.python.common.tools.getIcon
import com.intellij.python.sdkConfigurator.common.impl.ModuleDTO
import com.intellij.python.sdkConfigurator.common.impl.ModuleName
import com.intellij.python.sdkConfigurator.common.impl.ModulesDTO
import com.intellij.python.sdkConfigurator.common.impl.ToolIdDTO
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
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
 * UI should display [filteredParentModules] (either enabled or disabled). On each click call [moduleClicked].
 * Show filter from [moduleFilter]
 * Result can be taken from [checkedModules].
 * While composable is displayed. call [processFilterUpdates]
 */
internal class ModulesViewModel(modulesDTO: ModulesDTO) {
  // To be called when "ok" button should be enabled
  @Volatile
  var okButtonEnabledListener: ((enabled: Boolean) -> Unit)? = null
    set(value) {
      field = value
      callEnabledButtonListener() // Set value as soon as set
    }
  val icons: ImmutableMap<ToolIdDTO, IconKey> = persistentMapOf(*modulesDTO.modules
    .mapNotNull { module ->
      val toolId = module.createdByTool
      val icon = getIcon(ToolId(toolId))?.let { IntelliJIconKey.fromPlatformIcon(it.first, it.second) } ?: return@mapNotNull null
      Pair(toolId, icon)
    }.toTypedArray())

  private val parentModules: List<ModuleDTO> = modulesDTO.modules.sortedBy { it.name }
  private val parentModuleNames: Set<ModuleName> = parentModules.map { it.name }.toSet()

  var selectAllState: ToggleableState by mutableStateOf(ToggleableState.Off)
  var filteredParentModules: List<ModuleDTO> by mutableStateOf(parentModules)
  val checkedModules: SnapshotStateSet<ModuleName> = mutableStateSetOf()
  val moduleFilter = TextFieldState()

  // parent -> children
  private val children: ImmutableMap<ModuleName, ImmutableSet<ModuleName>> = filteredParentModules.associate {
    Pair(it.name, it.childModules.toPersistentSet())
  }.toPersistentMap()

  private val parentOnlyCheckedModules: Set<ModuleName> get() = parentModuleNames.intersect(checkedModules)

  // child -> parent
  private val parents: ImmutableMap<ModuleName, ModuleName> = children.flatMap { (parent, children) ->
    children.map { Pair(it, parent) }
  }.toMap().toImmutableMap()

  fun selectAllClicked() {
    val checked = when (selectAllState) {
      ToggleableState.On -> false
      ToggleableState.Off, ToggleableState.Indeterminate -> true
    }
    setParentModules(checked = checked, parentModulesToSet = parentModuleNames.toTypedArray())
  }

  fun moduleClicked(module: ModuleName) {
    val parentModule = parents[module] ?: module // Get parent if child module
    val alreadyChecked = parentModule in checkedModules
    setParentModules(checked = !alreadyChecked, parentModule)
  }

  private fun setParentModules(checked: Boolean, vararg parentModulesToSet: ModuleName) {
    val parentModulesToSet = parentModulesToSet.toSet()
    val parentsAndChildrenModules = parentModulesToSet + parentModulesToSet.flatMap { children.getOrDefault(it, emptySet()) } // Get children if parent
    if (checked) {
      checkedModules.addAll(parentsAndChildrenModules)
    }
    else {
      checkedModules.removeAll(parentsAndChildrenModules)
    }
    callEnabledButtonListener()
    selectAllState = when (parentOnlyCheckedModules.size) {
      0 -> ToggleableState.Off
      parentModules.size -> ToggleableState.On // All parent modules are checked
      else -> ToggleableState.Indeterminate
    }
  }

  private fun callEnabledButtonListener() {
    this.okButtonEnabledListener?.let { listener ->
      listener(checkedModules.isNotEmpty())
    }
  }

  @OptIn(FlowPreview::class)
  suspend fun processFilterUpdates() {
    snapshotFlow { moduleFilter.text }
      .debounce(500.milliseconds)
      .collectLatest { filter ->
        withContext(Dispatchers.Default) {
          val filter = filter.trim()
          filteredParentModules = if (filter.isEmpty()) parentModules else parentModules.filter { filter in it.name }
        }
      }
  }
}

