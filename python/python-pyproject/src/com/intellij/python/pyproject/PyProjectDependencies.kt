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
  val project: List<String> = listOf(),

  /**
   * Dependencies provided in `project.optional-dependencies`.
   */
  val optional: Map<String, List<String>> = mapOf(),
  /**
   * Groups (`dependency-groups.`)
   */
  val depGroupsToDeps: Map<String, List<String>> = mapOf(),
) {
  /**
   * Values of [depGroupsToDeps]
   */
  val allDepsFromGroups: Set<String> = depGroupsToDeps.values.flatten().toSet()
}