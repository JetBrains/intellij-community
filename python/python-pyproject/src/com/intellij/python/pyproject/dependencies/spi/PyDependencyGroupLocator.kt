// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.dependencies.spi

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.python.pyproject.dependencies.PyProjectDependencyGroupLocator
import com.intellij.python.pyproject.model.spi.PyProjectManager
import org.jetbrains.annotations.ApiStatus
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlTableHeader

/**
 * Resolves the dependency-group name a given `pyproject.toml` PSI element belongs to.
 *
 * One implementation per SDK / dependency manager (PEP 621 / PEP 735, Poetry, …). General code
 * (the line-marker provider) only sees the interface plus the module-internal [resolveGroupName]
 * dispatcher and stays format-agnostic.
 *
 * Implementations reason about two shapes only:
 *  - [resolveHeaderPath] for the last segment of `[section.header]`
 *  - [resolveInlineKey] for a key directly under a `[section]` table
 *
 * The PSI-unwrapping code that decides which of those to call lives in the top-level
 * [resolveGroupName] function; there is no dispatch method on the interface, so an SPI impl can't
 * accidentally bypass the shape contract by overriding one entry point and skipping the others.
 */
@ApiStatus.Internal
interface PyDependencyGroupLocator {
  /**
   * Called for section headers, e.g. for `[project.optional-dependencies.test]` the [path] is
   * `["project", "optional-dependencies", "test"]`. Return the group name or `null`.
   */
  fun resolveHeaderPath(path: List<String>): String? = null

  /**
   * Called for keys nested directly under a table, e.g. for `[dependency-groups]\ndev = [...]` the
   * [ownerPath] is `["dependency-groups"]` and [keyName] is `"dev"`. Return the group name or `null`.
   */
  fun resolveInlineKey(ownerPath: List<String>, keyName: String): String? = null
}

/**
 * PEP 621 / PEP 735 shapes are universal across every pyproject-based tool, so we consult this
 * locator unconditionally before iterating tool-specific locators — no manager has to opt in to
 * spec compliance.
 */
private val SPEC_LOCATOR: PyDependencyGroupLocator = PyProjectDependencyGroupLocator()

/**
 * Dispatches [segment] to the right [PyDependencyGroupLocator] entry point based on the PSI
 * shape around it, then returns the resolved dependency-group name.
 *
 * If [segment] is the last segment of a `[section.header]`, [PyDependencyGroupLocator.resolveHeaderPath]
 * is consulted with the dotted section path. If it is a key directly under a `[section]` table,
 * [PyDependencyGroupLocator.resolveInlineKey] is consulted with the owning section path and the
 * key name. Segments in any other position (inline tables, array elements, non-last segments)
 * return `null`.
 *
 * `@VisibleForTesting`-shaped: also called directly from unit tests that exercise a single
 * locator without going through the manager list; production code should go through
 * [resolveDependencyGroupName].
 */
internal fun PyDependencyGroupLocator.resolveGroupName(segment: TomlKeySegment): String? {
  val parent = segment.parent ?: return null
  val header = parent.parent as? TomlTableHeader
  if (header != null) {
    val segments = header.key?.segments ?: return null
    if (segments.lastOrNull() !== segment) return null
    return resolveHeaderPath(segments.map { it.name.orEmpty() })
  }
  val keyValue = PsiTreeUtil.getParentOfType(segment, TomlKeyValue::class.java) ?: return null
  if (keyValue.key.segments.lastOrNull() !== segment) return null
  // Direct parent must be the table itself — otherwise the segment is a key inside an inline
  // table / array element and is not a dependency group.
  val ownerTable = keyValue.parent as? TomlTable ?: return null
  val ownerPath = ownerTable.header.key?.segments?.map { it.name.orEmpty() } ?: return null
  return resolveInlineKey(ownerPath, segment.name.orEmpty())
}

/**
 * Resolves the dependency-group name a `pyproject.toml` PSI segment belongs to.
 *
 * Consults, in order:
 *  1. the PEP 621 / PEP 735 spec locator — covers `[project.optional-dependencies]`,
 *     `[dependency-groups]`, and the flat `[project]` `dependencies = [...]` form; universal
 *     across every tool so it doesn't need any registration.
 *  2. every registered [PyProjectManager] — each manager implements [PyDependencyGroupLocator]
 *     and contributes its tool-specific shapes (Poetry's `[tool.poetry.dependencies]` etc).
 *     Order among managers is EP order.
 *
 * Returns the first non-null match. `null` means no locator claims [segment] — the caller should
 * treat it as "not a dependency-group anchor" (e.g. skip drawing an inlay hint for it).
 *
 * This is the entry point production code (line markers, inlay hints, quick-doc, …) should use —
 * it hides both the manager iteration and the PSI-shape dispatch behind a single call.
 */
@ApiStatus.Internal
fun resolveDependencyGroupName(segment: TomlKeySegment): String? {
  SPEC_LOCATOR.resolveGroupName(segment)?.let { return it }
  return PyProjectManager.EP.extensionList.firstNotNullOfOrNull { it.resolveGroupName(segment) }
}
