package com.intellij.python.sdkConfigurator.frontend

import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.snapshots.SnapshotStateSet
import com.intellij.python.sdkConfigurator.common.impl.ModuleName
import com.intellij.python.sdkConfigurator.common.impl.ModulesDTO
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

private val fakePythons = arrayOf("Python 3.10", "Python 3.11")

/**
 * UI should display [checkBoxItems] (either enabled or disabled). On each click call [clicked].
 * Result can be taken from [checked]
 */
internal class ModulesViewModel(modulesDTO: ModulesDTO) {
  val checkBoxItems: PersistentMap<ModuleName, ModuleInfo> = persistentMapOf(*modulesDTO.modules
    .map { (moduleName, parent) ->
      Pair(moduleName, ModuleInfo(parent, pythons = persistentListOf(*fakePythons)))
    }.toTypedArray())
  val checked: SnapshotStateSet<ModuleName> = mutableStateSetOf()
  private val children = mutableMapOf<ModuleName, MutableSet<ModuleName>>()

  init {
    for ((child, parent) in modulesDTO.modules) {
      if (parent == null) continue
      children.getOrPut(parent) { HashSet() }.add(child)
    }
  }

  fun clicked(what: ModuleName, checkBoxSet: Boolean) {
    val toChange = setOf(what) + children.getOrDefault(what, emptySet())
    if (checkBoxSet) {
      checked.addAll(toChange)
    }
    else {
      checked.removeAll(toChange)
    }
  }
}

internal data class ModuleInfo(val parent: String?, val pythons: ImmutableList<String>)
