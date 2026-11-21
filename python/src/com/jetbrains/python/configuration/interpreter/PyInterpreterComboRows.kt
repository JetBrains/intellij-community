// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration.interpreter

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

/**
 * One row of [PyInterpreterComboBox]. It is generic over the interpreter [T], so a test can use plain values.
 */
@ApiStatus.Internal
sealed interface SdkComboItem<out T : Any> {
  /** The row that stands for the interpreter of the module, whichever it is at run time. */
  data object ModuleInterpreter : SdkComboItem<Nothing>

  /** An interpreter. */
  data class Existing<out T : Any>(val interpreter: T) : SdkComboItem<T>

  /** The caption of an owner group: the name of the Python project that owns the interpreters below. It is not selectable. */
  data class GroupHeader(val title: @NlsSafe String) : SdkComboItem<Nothing>

  /** The row that shows or hides the interpreters of the other Python projects. */
  data object ToggleOtherProjects : SdkComboItem<Nothing>

  /** The row that opens the "All Interpreters" settings page. */
  data object ShowAll : SdkComboItem<Nothing>
}

/** What [PyInterpreterComboBox] can list for one module. */
@ApiStatus.Internal
class InterpreterComboSource<T : Any>(
  /** The interpreter of the module, or `null` when it has none. */
  val moduleInterpreter: T?,
  /**
   * The interpreters of the workspace root of the module. Every member of the workspace shares them, and the combo lists
   * them first. They are empty for a module that is not a Python project.
   */
  val ownInterpreters: List<T>,
  /** The interpreters of every Python project of the IDE project. */
  val projectInterpreters: List<T>,
  /** The name of the Python project that owns each interpreter, for the owner captions. */
  val owners: Map<T, String> = emptyMap(),
  /**
   * Whether the IDE project has another workspace than the one of the module, also one without interpreters. The other
   * members of the workspace of the module do not count: they share its interpreters.
   */
  val hasOtherProjects: Boolean = false,
)

/**
 * The rows of the combo for [source].
 *
 * - The own interpreters come first, in their order. With [showOtherProjects], the interpreters of the other Python
 *   projects follow. A module without own interpreters lists none until the toggle.
 * - The interpreter of the module always has a row. In the mode with the other projects, each of [kept], such as the
 *   selection of the user, has a row too. The owned mode lists only the own interpreters, and the combo keeps a selection
 *   of another project without a row.
 * - The owner captions show in the mode with the other projects, and else when the rows hold the interpreters of more
 *   than one owner.
 * - The toggle shows when there is another workspace, also one without interpreters, so the user always finds it.
 * - With [offerModuleInterpreter], the "module interpreter" row comes first.
 * - No row stands for no interpreter: the combo draws no selection as a grey text, and a module does not go back to it.
 * - The "All Interpreters" row shows only with [offerShowAll].
 */
@ApiStatus.Internal
fun <T : Any> interpreterComboRows(
  source: InterpreterComboSource<T>,
  showOtherProjects: Boolean,
  kept: List<T>,
  offerShowAll: Boolean,
  offerModuleInterpreter: Boolean,
): List<SdkComboItem<T>> {
  val own = source.ownInterpreters
  val others = source.projectInterpreters - own.toSet()
  val shown = if (showOtherProjects) own + others else own
  val listed = buildList {
    for (always in kept.takeIf { showOtherProjects }.orEmpty() + listOfNotNull(source.moduleInterpreter)) {
      if (always !in shown && always !in this) add(always)
    }
    addAll(shown)
  }
  val rootOwner = own.firstOrNull()?.let { source.owners[it] }
  val groups = listed.groupBy { source.owners[it] }
  val grouped = groups[rootOwner].orEmpty() + groups.filterKeys { it != rootOwner }.values.flatten()
  val withHeaders = showOtherProjects || grouped.mapNotNullTo(HashSet()) { source.owners[it] }.size > 1
  return buildList {
    if (offerModuleInterpreter) add(SdkComboItem.ModuleInterpreter)
    var lastOwner: String? = null
    for (interpreter in grouped) {
      val owner = source.owners[interpreter]
      if (withHeaders && owner != null && owner != lastOwner) add(SdkComboItem.GroupHeader(owner))
      lastOwner = owner
      add(SdkComboItem.Existing(interpreter))
    }
    if (others.isNotEmpty() || source.hasOtherProjects) add(SdkComboItem.ToggleOtherProjects)
    if (offerShowAll) add(SdkComboItem.ShowAll)
  }
}

/**
 * Whether the combo shows the interpreters of the other Python projects for [selected], a selection that a read brings
 * in, such as the interpreter of a run configuration. A selection of another project is one of them, so the combo shows
 * them all, and its toggle offers to show the own interpreters only.
 */
@ApiStatus.Internal
fun <T : Any> opensOtherProjects(source: InterpreterComboSource<T>, selected: T?): Boolean =
  selected != null && selected !in source.ownInterpreters
