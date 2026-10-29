// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.pycharm.community.ide.impl.configuration.interpreter.PyModuleDependenciesPresenter
import com.intellij.pycharm.community.ide.impl.configuration.interpreter.PyModuleDependenciesPresenter.DependencyId
import com.intellij.pycharm.community.ide.impl.configuration.interpreter.PyModuleDependenciesPresenter.OrderEntrySlot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PyModuleDependenciesPresenterTest {

  @Test
  fun `not modified when UI matches the initial baseline`() {
    val presenter = presenter(initial = setOf(dep("cli"), dep("web")))

    assertFalse(presenter.isModified(setOf(dep("cli"), dep("web"))))
  }

  @Test
  fun `modified when UI adds a dependency`() {
    val presenter = presenter(initial = setOf(dep("cli")))

    assertTrue(presenter.isModified(setOf(dep("cli"), dep("web"))))
  }

  @Test
  fun `modified when UI removes a dependency`() {
    val presenter = presenter(initial = setOf(dep("cli"), dep("web")))

    assertTrue(presenter.isModified(setOf(dep("cli"))))
  }

  @Test
  fun `never modified in read-only mode even when UI differs`() {
    val presenter = presenter(initial = setOf(dep("cli")), editable = false)

    assertFalse(presenter.isModified(setOf(dep("cli"), dep("web"))))
  }

  @Test
  fun `computeDiff reports additions and removals`() {
    val presenter = presenter(initial = setOf(dep("cli"), dep("web")))

    val diff = presenter.computeDiff(setOf(dep("web"), dep("lib")))

    assertEquals(setOf(dep("lib")), diff.toAdd)
    assertEquals(setOf(dep("cli")), diff.toRemove)
  }

  @Test
  fun `computeDiff is empty when nothing changed`() {
    val presenter = presenter(initial = setOf(dep("cli")))

    val diff = presenter.computeDiff(setOf(dep("cli")))

    assertTrue(diff.toAdd.isEmpty())
    assertTrue(diff.toRemove.isEmpty())
  }

  @Test
  fun `markApplied moves baseline so subsequent isModified returns false`() {
    val presenter = presenter(initial = setOf(dep("cli")))
    val next = setOf(dep("cli"), dep("web"))
    assertTrue(presenter.isModified(next))

    presenter.markApplied(next)

    assertFalse(presenter.isModified(next))
    assertEquals(next, presenter.initialDependencies)
  }

  @Test
  fun `planApply targets only module slots matching the removal set`() {
    val presenter = presenter(initial = setOf(dep("cli"), dep("web")))
    // Positions: 0=SDK, 1=own-sources, 2=module(cli), 3=module(web), 4=library
    val slots = listOf(
      nonModule(),
      nonModule(),
      moduleDep("cli"),
      moduleDep("web"),
      nonModule(),
    )

    val plan = presenter.planApply(uiChecked = setOf(dep("cli")), slots)

    assertEquals(listOf(3), plan.slotsToRemove)
    assertTrue(plan.toAdd.isEmpty())
  }

  @Test
  fun `planApply never removes SDK, own-sources or library slots`() {
    val presenter = presenter(initial = setOf(dep("cli")))
    val slots = listOf(nonModule(), nonModule(), nonModule())

    val plan = presenter.planApply(uiChecked = emptySet(), slots)

    assertTrue(plan.slotsToRemove.isEmpty())
    assertTrue(plan.toAdd.isEmpty())
  }

  @Test
  fun `planApply reports additions from the diff`() {
    val presenter = presenter(initial = setOf(dep("cli")))
    val slots = listOf(nonModule(), moduleDep("cli"))

    val plan = presenter.planApply(uiChecked = setOf(dep("cli"), dep("web")), slots)

    assertTrue(plan.slotsToRemove.isEmpty())
    assertEquals(setOf(dep("web")), plan.toAdd)
  }

  @Test
  fun `planApply returns an empty plan when nothing changed`() {
    val presenter = presenter(initial = setOf(dep("cli")))
    val slots = listOf(nonModule(), moduleDep("cli"))

    val plan = presenter.planApply(uiChecked = setOf(dep("cli")), slots)

    assertTrue(plan.slotsToRemove.isEmpty())
    assertTrue(plan.toAdd.isEmpty())
  }

  @Test
  fun `planApply preserves slot positions in removal indices`() {
    val presenter = presenter(initial = setOf(dep("cli"), dep("web"), dep("lib")))
    val slots = listOf(
      moduleDep("cli"),
      nonModule(),
      moduleDep("web"),
      nonModule(),
      moduleDep("lib"),
    )

    val plan = presenter.planApply(uiChecked = setOf(dep("web")), slots)

    // Only positions 0 and 4 should be reported, preserving source order.
    assertEquals(listOf(0, 4), plan.slotsToRemove)
    assertTrue(plan.toAdd.isEmpty())
  }

  @Test
  fun `prepareApply resolves slot indices to live entries before removal order compacts them`() {
    val presenter = presenter(initial = setOf(dep("cli"), dep("web"), dep("lib")))
    // Fake order-entry list: SDK, cli, own-sources, web, lib. Pane removes cli and lib.
    val entries = listOf(
      FakeEntry("sdk", isModule = false),
      FakeEntry("cli", isModule = true),
      FakeEntry("own", isModule = false),
      FakeEntry("web", isModule = true),
      FakeEntry("lib", isModule = true),
    )

    val mutations = presenter.prepareApply(
      uiChecked = setOf(dep("web")),
      entries = entries,
      classify = { if (it.isModule) OrderEntrySlot.ModuleDependency(it.id) else OrderEntrySlot.NonModule },
    )

    assertEquals(listOf(entries[1], entries[4]), mutations.entriesToRemove)
    assertTrue(mutations.modulesToAdd.isEmpty())
    assertFalse(mutations.isEmpty)
  }

  @Test
  fun `prepareApply resolves additions through the presenter's candidate map`() {
    val presenter = presenter(initial = setOf(dep("cli")))
    val entries = listOf(FakeEntry("cli", isModule = true))

    val mutations = presenter.prepareApply(
      uiChecked = setOf(dep("cli"), dep("web"), dep("lib")),
      entries = entries,
      classify = { if (it.isModule) OrderEntrySlot.ModuleDependency(it.id) else OrderEntrySlot.NonModule },
    )

    assertTrue(mutations.entriesToRemove.isEmpty())
    assertEquals(setOf("web-module", "lib-module"), mutations.modulesToAdd.toSet())
  }

  @Test
  fun `prepareApply drops add ids with no backing candidate`() {
    val presenter = presenter(initial = emptySet(), candidates = emptyMap())

    val mutations = presenter.prepareApply(
      uiChecked = setOf(dep("ghost")),
      entries = emptyList<FakeEntry>(),
      classify = { OrderEntrySlot.NonModule },
    )

    assertTrue(mutations.modulesToAdd.isEmpty())
    assertTrue(mutations.entriesToRemove.isEmpty())
    assertTrue(mutations.isEmpty)
  }

  @Test
  fun `prepareApply is empty when nothing changed`() {
    val presenter = presenter(initial = setOf(dep("cli")))
    val entries = listOf(FakeEntry("cli", isModule = true))

    val mutations = presenter.prepareApply(
      uiChecked = setOf(dep("cli")),
      entries = entries,
      classify = { OrderEntrySlot.ModuleDependency(it.id) },
    )

    assertTrue(mutations.isEmpty)
  }

  @Test
  fun `apply feeds removals before additions and reports mutation`() {
    val presenter = presenter(initial = setOf(dep("cli"), dep("web")))
    val entries = listOf(
      FakeEntry("sdk", isModule = false),
      FakeEntry("cli", isModule = true),
      FakeEntry("web", isModule = true),
    )
    val events = mutableListOf<String>()

    val mutated = presenter.apply(
      uiChecked = setOf(dep("lib")),
      entries = entries,
      classify = { if (it.isModule) OrderEntrySlot.ModuleDependency(it.id) else OrderEntrySlot.NonModule },
      removeEntry = { events += "remove(${it.id})" },
      addModule = { events += "add($it)" },
    )

    assertTrue(mutated)
    assertEquals(listOf("remove(cli)", "remove(web)", "add(lib-module)"), events)
  }

  @Test
  fun `apply returns false and never calls callbacks when nothing changed`() {
    val presenter = presenter(initial = setOf(dep("cli")))
    val entries = listOf(FakeEntry("cli", isModule = true))
    val events = mutableListOf<String>()

    val mutated = presenter.apply(
      uiChecked = setOf(dep("cli")),
      entries = entries,
      classify = { OrderEntrySlot.ModuleDependency(it.id) },
      removeEntry = { events += "remove" },
      addModule = { events += "add" },
    )

    assertFalse(mutated)
    assertTrue(events.isEmpty())
  }

  @Test
  fun `candidates render in alphabetical order independent of map insertion order`() {
    val presenter = presenter(
      candidates = linkedMapOf(
        dep("web") to "web-module",
        dep("cli") to "cli-module",
        dep("lib") to "lib-module",
      ),
    )

    assertEquals(listOf(dep("cli"), dep("lib"), dep("web")), presenter.candidates)
  }

  private data class FakeEntry(val id: String, val isModule: Boolean)

  //region fixtures
  private fun moduleDep(name: String): OrderEntrySlot = OrderEntrySlot.ModuleDependency(name)
  private fun nonModule(): OrderEntrySlot = OrderEntrySlot.NonModule

  private fun dep(name: String): DependencyId = DependencyId(name)

  private fun presenter(
    initial: Set<DependencyId> = emptySet(),
    editable: Boolean = true,
    candidates: Map<DependencyId, String> = mapOf(
      dep("cli") to "cli-module",
      dep("web") to "web-module",
      dep("lib") to "lib-module",
    ),
  ): PyModuleDependenciesPresenter<String> = PyModuleDependenciesPresenter(
    candidates = candidates,
    initialDependencies = initial,
    isEditableProvider = { editable },
  )
  //endregion
}
