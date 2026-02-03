package com.intellij.python.pyproject.model.spi

/**
 * module to its deps
 */
@JvmInline
value class ProjectDependencies(val map: Map<ProjectName, Set<ProjectName>>)

internal operator fun ProjectDependencies.plus(other: ProjectDependencies): ProjectDependencies =
  ProjectDependencies((map.keys + other.map.keys).associateWith { (map[it] ?: emptySet()) + (other.map[it] ?: emptySet()) })
