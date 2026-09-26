// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.psi.spi

import com.intellij.python.pyproject.model.spi.TomlDependencySpecification
import org.jetbrains.annotations.ApiStatus

/**
 * A `pyproject.toml` string value that names a path on disk, as reported by [PyProjectTomlPathLocator].
 *
 * @property baseSiblingKey key of a sibling entry naming the directory the value is relative to
 *   (Poetry's `packages = [{ include = "pkg", from = "src" }]`); `null` means the directory of the
 *   `pyproject.toml` itself, which is what uv and Poetry use everywhere else.
 * @property acceptFiles whether the value may name a file rather than a directory (a Poetry single-module
 *   `include`, a uv path source pointing at an sdist / wheel). Affects completion only; resolution of an
 *   existing file always works.
 */
@ApiStatus.Internal
class PyProjectTomlPathValue(
  internal val baseSiblingKey: String? = null,
  internal val acceptFiles: Boolean = false,
)

/**
 * Reports which `pyproject.toml` keys hold filesystem paths, so that `PyProjectTomlPathReferenceContributor`
 * can make them navigable (PY-90384).
 *
 * One implementation per dependency manager: uv knows about `[tool.uv.workspace] members`, Poetry about
 * `[tool.poetry] packages`, and neither has to know about the other. General code only sees this interface
 * and the [resolvePyProjectTomlPath] dispatcher, and stays tool-agnostic — the same split as
 * [com.intellij.python.pyproject.dependencies.spi.PyDependencyGroupLocator].
 *
 * Implementations answer about key paths only; locating the base directory, cutting glob patterns off and
 * building the references is the contributor's job, so no implementation touches PSI.
 */
@ApiStatus.Internal
@ApiStatus.OverrideOnly
interface PyProjectTomlPathLocator {
  /**
   * Called with the dotted key path of a string value, with array indices and inline tables collapsed:
   * `[tool.uv.workspace] members = ["pkg"]`, `[tool.uv] workspace = { members = ["pkg"] }` and
   * `[tool.uv] workspace.members = ["pkg"]` all arrive as `["tool", "uv", "workspace", "members"]`.
   *
   * Return `null` for anything that is not a path — `null` is also the right answer for a value this tool
   * does own but that is not a path, e.g. Poetry's `[tool.poetry] include` sdist patterns.
   */
  fun resolveTomlPath(keyPath: List<String>): PyProjectTomlPathValue? = null
}

/**
 * Whether [keyPath] addresses the `path` of a path dependency declared in one of [this] sections, e.g.
 * `tool.poetry.dependencies.<dep>.path` or `tool.poetry.group.<group>.dependencies.<dep>.path`.
 *
 * Shared so that a manager can answer [PyProjectTomlPathLocator.resolveTomlPath] for its path dependencies
 * straight from the sections it already declares in
 * [com.intellij.python.pyproject.model.spi.PyProjectManager.getTomlDependencySpecifications], instead of
 * spelling the same section names out twice.
 */
@ApiStatus.Internal
fun List<TomlDependencySpecification>.isPathDependencyKey(keyPath: List<String>): Boolean {
  if (keyPath.lastOrNull() != PATH_KEY) return false
  // Drop the trailing `<dep>.path` to get the section declaring the dependency.
  val section = keyPath.dropLast(2)
  return any { specification ->
    when (specification) {
      is TomlDependencySpecification.PathDependency -> section == specification.tomlKey.split('.')
      // `<prefix>.<group name>.<suffix>`, e.g. tool.poetry.group.dev.dependencies
      is TomlDependencySpecification.GroupPathDependency -> {
        val prefix = specification.tomlKeyToGroup.split('.')
        val suffix = specification.tomlKeyFromGroupToPath.split('.')
        section.size == prefix.size + 1 + suffix.size &&
        section.subList(0, prefix.size) == prefix &&
        section.subList(section.size - suffix.size, section.size) == suffix
      }
      // Dependency strings, not paths: handled by the injected `Requirements` language.
      is TomlDependencySpecification.Pep621Dependency,
      is TomlDependencySpecification.GroupPep621Dependency,
        -> false
    }
  }
}

private const val PATH_KEY = "path"
