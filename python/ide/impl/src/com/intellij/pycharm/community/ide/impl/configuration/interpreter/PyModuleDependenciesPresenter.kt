// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.openapi.util.NlsSafe

/**
 * Swing-free state for [PyModuleDependenciesPane]. Owns the "which candidates are checked",
 * "is the editor read-only", and "has the user changed anything" decisions so a unit test can pin
 * them without a project fixture — the pane only renders this state and forwards user clicks back.
 */
internal class PyModuleDependenciesPresenter<M>(
  candidates: Map<DependencyId, M>,
  initialDependencies: Set<DependencyId>,
  private val isEditableProvider: () -> Boolean,
) {

  /**
   * Candidate id → backing module. Presenter owns the lookup so [prepareApply] can resolve
   * additions without a per-call callback from the pane. Generic over `M` so the pane binds it
   * to `com.intellij.openapi.module.Module` while a unit test uses plain data.
   */
  private val candidatesById: Map<DependencyId, M> = candidates

  /** Candidate ids in render order (alphabetical, case-insensitive). Pane iterates this to build its list model. */
  val candidates: List<DependencyId> = candidatesById.keys.sortedBy { it.name.lowercase() }

  /**
   * Typed name of a python-eligible module that may appear in the dependency list. Keeps the
   * presenter free of `com.intellij.openapi.module.Module` so a test can build an identity by
   * name only — a Swing-free pane layer runs with no project.
   */
  @JvmInline
  value class DependencyId(@NlsSafe val name: String)

  /**
   * Baseline against which [isModified] decides whether the user changed anything. Updated by
   * the pane after a successful commit so a subsequent `isModified` returns `false` until the
   * next edit.
   */
  var initialDependencies: Set<DependencyId> = initialDependencies
    private set

  /** `true` while the user may change the checked set. `false` renders a read-only banner. */
  val isEditable: Boolean get() = isEditableProvider()

  fun isModified(uiChecked: Set<DependencyId>): Boolean = isEditable && uiChecked != initialDependencies

  fun markApplied(uiChecked: Set<DependencyId>) {
    initialDependencies = uiChecked
  }

  /**
   * Split a desired check-state against the current baseline into the two mutations the pane
   * needs to apply on the underlying module model. Pure — no Swing / platform access.
   */
  fun computeDiff(uiChecked: Set<DependencyId>): Diff {
    val toAdd = uiChecked - initialDependencies
    val toRemove = initialDependencies - uiChecked
    return Diff(toAdd, toRemove)
  }

  data class Diff(val toAdd: Set<DependencyId>, val toRemove: Set<DependencyId>)

  /**
   * One slot of a module's order entry list, classified for the dependency planner.
   *
   * The pane translates each `OrderEntry` on the subject module into one of these cases; the
   * presenter then operates on this closed hierarchy instead of the platform's open interface,
   * which keeps [planApply] free of `is` / `as?` on `OrderEntry` and lets a unit test build the
   * exact slot layout without a project fixture.
   */
  sealed interface OrderEntrySlot {
    /** Edge to another module — the only kind the "Dependencies" tab can add or drop. */
    data class ModuleDependency(@NlsSafe val moduleName: String) : OrderEntrySlot

    /** SDK, own-sources or library slot. Must survive the apply; dropping it would break the module. */
    data object NonModule : OrderEntrySlot
  }

  /** Mutation plan the pane applies on a `ModifiableRootModel` after resolving indices back to live entries. */
  data class ApplyPlan(val slotsToRemove: List<Int>, val toAdd: Set<DependencyId>)

  /**
   * Resolved mutations the pane applies on a `ModifiableRootModel`.
   *
   * `entriesToRemove` already carries live `OrderEntry` references (not indices) so a subsequent
   * `removeOrderEntry` loop is safe: the platform compacts the entry array on each removal, which
   * would invalidate a second pass by index. `modulesToAdd` already dropped ids that have no
   * backing candidate, so the pane can iterate it without a nullable step.
   */
  data class Mutations<E, M>(val entriesToRemove: List<E>, val modulesToAdd: List<M>) {
    val isEmpty: Boolean get() = entriesToRemove.isEmpty() && modulesToAdd.isEmpty()
  }

  /**
   * Decide what the pane should mutate on the subject module's root model.
   *
   * [slots] is the subject module's full order entry list in insertion order, each pre-classified
   * by the pane. The returned [ApplyPlan.slotsToRemove] names which positions the pane should
   * drop; the pane resolves them back to live `OrderEntry` instances by the same index. Only
   * [OrderEntrySlot.ModuleDependency] positions whose module name is in the diff's `toRemove`
   * set are reported — [OrderEntrySlot.NonModule] positions never are. Pure, so a test can pin
   * the selection rule without a project fixture.
   */
  fun planApply(uiChecked: Set<DependencyId>, slots: List<OrderEntrySlot>): ApplyPlan {
    val diff = computeDiff(uiChecked)
    val slotsToRemove = slots.withIndex().mapNotNull { (index, slot) ->
      when (slot) {
        is OrderEntrySlot.ModuleDependency -> index.takeIf { DependencyId(slot.moduleName) in diff.toRemove }
        OrderEntrySlot.NonModule -> null
      }
    }
    return ApplyPlan(slotsToRemove, diff.toAdd)
  }

  /**
   * Pure half of [apply]: classify the subject module's current order entries, compute the plan,
   * then resolve both sides back to the live references the pane needs. `E` stays generic so a
   * unit test can drive the pipeline with plain data instead of a project fixture — the pane
   * binds `E` to `OrderEntry` and `M` to `Module`.
   */
  fun <E> prepareApply(
    uiChecked: Set<DependencyId>,
    entries: List<E>,
    classify: (E) -> OrderEntrySlot,
  ): Mutations<E, M> {
    val plan = planApply(uiChecked, entries.map(classify))
    val entriesToRemove = plan.slotsToRemove.map { entries[it] }
    val modulesToAdd = plan.toAdd.mapNotNull { candidatesById[it] }
    return Mutations(entriesToRemove, modulesToAdd)
  }

  /**
   * Compute mutations and feed them to the pane's model callbacks in the required order:
   * removals first (so index-based resolution stays valid), additions second. Keeps the pane's
   * write action free of any decision logic — it only forwards `model::removeOrderEntry` and
   * `model::addModuleOrderEntry` and then commits.
   *
   * Returns `true` when anything was fed to the callbacks, so the pane can skip `commit()` on a
   * no-op change.
   */
  fun <E> apply(
    uiChecked: Set<DependencyId>,
    entries: List<E>,
    classify: (E) -> OrderEntrySlot,
    removeEntry: (E) -> Unit,
    addModule: (M) -> Unit,
  ): Boolean {
    val mutations = prepareApply(uiChecked, entries, classify)
    if (mutations.isEmpty) return false
    mutations.entriesToRemove.forEach(removeEntry)
    mutations.modulesToAdd.forEach(addModule)
    return true
  }
}
