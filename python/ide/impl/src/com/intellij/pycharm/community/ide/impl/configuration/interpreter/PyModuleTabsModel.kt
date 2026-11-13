// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import org.jetbrains.annotations.ApiStatus
import java.util.EnumSet

/**
 * Tabs the per-module editor of [PyModuleDetailsPane] shows.
 *
 * - [Sources]: content roots for the module.
 * - [Dependencies]: the Python packages installed into the module's interpreter. User-facing label
 *   is "Dependencies" (the Packages tree panel lives behind it). Historical name stayed `Packages`
 *   until the redesign — the enum name reflects the current label.
 * - [ProjectLinks]: optional tab that lets a multi-module project declare module-to-module
 *   dependencies. Hidden when pyproject.toml auto-configure owns the dep graph, or when the project
 *   has no sibling to link against.
 *
 * Declaration order is the display order: a visible-tabs [EnumSet] iterates in this order, so the
 * UI never has to pick a "position" for the optional tab.
 */
@ApiStatus.Internal
enum class PyModuleTab { Sources, Dependencies, ProjectLinks }

/**
 * All the "which tabs are visible" logic for [PyModuleDetailsPane]. Kept Swing-free so
 * [com.intellij.python.junit5Tests.unit.PyModuleTabsModelTest] can pin the rules without spinning
 * up an IDE. The providers are polled lazily on every [visibleTabs] call — the editor evaluates
 * them each time the user flips the page-level auto-configure checkbox or switches module.
 *
 * Rules:
 *  - [PyModuleTab.Sources], [PyModuleTab.Dependencies]: always present.
 *  - [PyModuleTab.ProjectLinks]: visible only when the project has sibling modules
 *    (multi-module) **and** the `Auto-configure from pyproject.toml` toggle is OFF. Under
 *    pyproject.toml auto-configure, the file is the source of truth for project links.
 */
@ApiStatus.Internal
class PyModuleTabsModel(
  private val isMultiModule: () -> Boolean,
  private val usePyprojectToml: () -> Boolean,
) {
  fun visibleTabs(): EnumSet<PyModuleTab> {
    val tabs = EnumSet.of(PyModuleTab.Sources, PyModuleTab.Dependencies)
    if (isMultiModule() && !usePyprojectToml()) tabs.add(PyModuleTab.ProjectLinks)
    return tabs
  }
}
