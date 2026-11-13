// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.pycharm.community.ide.impl.configuration.interpreter.PyModuleTab
import com.intellij.pycharm.community.ide.impl.configuration.interpreter.PyModuleTabsModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.EnumSet

/**
 * Pins the tab-visibility rules for `PyModuleDetailsPane`:
 *  - Sources / Dependencies always present.
 *  - Project Links present only in a multi-module project with pyproject auto-configure OFF.
 *  - The optional Project Links tab iterates last (enum declaration order), so Sources and
 *    Dependencies indices never shift when it appears or disappears.
 */
class PyModuleTabsModelTest {

  @Test
  fun `single module, toml off -- no project links tab`() {
    assertEquals(
      EnumSet.of(PyModuleTab.Sources, PyModuleTab.Dependencies),
      model(isMultiModule = false, usePyprojectToml = false).visibleTabs(),
    )
  }

  @Test
  fun `single module, toml on -- no project links tab`() {
    assertEquals(
      EnumSet.of(PyModuleTab.Sources, PyModuleTab.Dependencies),
      model(isMultiModule = false, usePyprojectToml = true).visibleTabs(),
    )
  }

  @Test
  fun `multi module, toml off -- project links appended last`() {
    assertEquals(
      EnumSet.of(PyModuleTab.Sources, PyModuleTab.Dependencies, PyModuleTab.ProjectLinks),
      model(isMultiModule = true, usePyprojectToml = false).visibleTabs(),
    )
  }

  @Test
  fun `multi module, toml on -- project links hidden (pyproject owns the links)`() {
    assertEquals(
      EnumSet.of(PyModuleTab.Sources, PyModuleTab.Dependencies),
      model(isMultiModule = true, usePyprojectToml = true).visibleTabs(),
    )
  }

  @Test
  fun `project links tab iterates last when visible`() {
    val tabs = model(isMultiModule = true, usePyprojectToml = false).visibleTabs().toList()
    assertEquals(2, tabs.indexOf(PyModuleTab.ProjectLinks))
  }

  @Test
  fun `leading two tabs keep stable iteration order regardless of the optional tab`() {
    val withOptional = model(isMultiModule = true, usePyprojectToml = false).visibleTabs().toList()
    val withoutOptional = model(isMultiModule = true, usePyprojectToml = true).visibleTabs().toList()
    assertEquals(PyModuleTab.Sources, withOptional[0])
    assertEquals(PyModuleTab.Dependencies, withOptional[1])
    assertEquals(PyModuleTab.Sources, withoutOptional[0])
    assertEquals(PyModuleTab.Dependencies, withoutOptional[1])
  }

  @Test
  fun `providers are polled on every call`() {
    var multi = false
    var toml = false
    val subject = PyModuleTabsModel(isMultiModule = { multi }, usePyprojectToml = { toml })
    assertEquals(EnumSet.of(PyModuleTab.Sources, PyModuleTab.Dependencies), subject.visibleTabs())
    multi = true
    assertEquals(
      EnumSet.of(PyModuleTab.Sources, PyModuleTab.Dependencies, PyModuleTab.ProjectLinks),
      subject.visibleTabs(),
    )
    toml = true
    assertEquals(EnumSet.of(PyModuleTab.Sources, PyModuleTab.Dependencies), subject.visibleTabs())
  }

  private fun model(isMultiModule: Boolean, usePyprojectToml: Boolean): PyModuleTabsModel =
    PyModuleTabsModel(isMultiModule = { isMultiModule }, usePyprojectToml = { usePyprojectToml })
}
