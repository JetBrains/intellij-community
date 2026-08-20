package com.intellij.python.pyproject

import org.jetbrains.annotations.ApiStatus

/**
 * Represents the dependencies of the project.
 */
@ConsistentCopyVisibility
@ApiStatus.Internal
data class PyProjectDependencies internal constructor(
  /**
   * Dependencies provided in `project.dependencies`.
   */
  val required: List<String> = listOf(),

  /**
   * Extras provided in `project.optional-dependencies`.
   */
  val extras: PyProjectDependencyTable = PyProjectDependencyTable(emptyMap()),
) {
  /**
   * Flatten set of [required] + [extras]: an extra is a declared dependency like any other (PY-91629).
   */
  val requiredAndExtras: Set<String> = required.toSet() + extras.allDeps
}

/**
 * Given the following file
 * ```toml
 * [some-dependency-table]
 * key = ["v1", "v2"]
 * key2 = ["v3"]
 * ```
 * this class represents such a table. Each key might have 0 or more values.
 * Both PEP 621 `project.optional-dependencies` (extras) and PEP 735 `dependency-groups` have this shape.
 *
 * Use [groupNames] to get possible keys, [get]/[contains] to look one up, or [allDeps] to get a flat set.
 *
 * [groupNames] and [allDeps] are recomputed on each access, since a value class can't cache anything.
 * To get every dependency of a whole file, use the precomputed [PyProjectToml.allDeclaredDeps] instead.
 */
@ApiStatus.Internal
@JvmInline
value class PyProjectDependencyTable internal constructor(internal val depsByGroup: Map<String, List<String>>) {
  /**
   * All keys of this table, e.g. extra names or dependency group names.
   */
  val groupNames: Set<String> get() = depsByGroup.keys

  /**
   * Flatten set of all deps (e.g. `["v1", "v2", "v3"]` given an example in a class doc.)
   */
  val allDeps: Set<String> get() = depsByGroup.values.flatten().toSet()

  /**
   * Dependencies declared under [groupName], or an empty list if this table has no such key.
   */
  operator fun get(groupName: String): List<String> = depsByGroup[groupName] ?: emptyList()

  /**
   * Whether [groupName] is declared at all. A declared key may still map to an empty list.
   */
  operator fun contains(groupName: String): Boolean = groupName in depsByGroup
}
