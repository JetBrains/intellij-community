package com.intellij.python.sdkConfigurator.frontend

import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.snapshots.SnapshotStateSet
import com.intellij.python.sdkConfigurator.common.ModuleName
import com.intellij.python.sdkConfigurator.common.ModulesDTO
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

internal enum class Status {
  ENABLED, DISABLED
}

/**
 * UI should display [checkBoxItems] (either enabled or disabled). On each click call [clicked].
 * Result can be taken from [checked]
 */
internal class ModulesViewModel(modulesDTO: ModulesDTO) {
  val checkBoxItems: PersistentList<Pair<ModuleName, Status>> = persistentListOf(*modulesDTO.modules
    .map { (module, parent) ->
      Pair(module, if (parent == null) Status.ENABLED else Status.DISABLED)
    }.toTypedArray())
  val checked: SnapshotStateSet<ModuleName> = mutableStateSetOf()
  private val disabledItems = checkBoxItems.filter { it.second == Status.DISABLED }.map { it.first }.toSet()
  private val children = mutableMapOf<ModuleName, MutableSet<ModuleName>>()

  init {
    for ((child, parent) in modulesDTO.modules) {
      if (parent == null) continue
      children.getOrPut(parent) { HashSet() }.add(child)
    }
  }

  fun clicked(what: ModuleName, checkBoxSet: Boolean) {
    assert(what !in disabledItems) { "$what should never be clicked" }
    val toChange = setOf(what) + children.getOrDefault(what, emptySet())
    if (checkBoxSet) {
      checked.addAll(toChange)
    }
    else {
      checked.removeAll(toChange)
    }
  }
}