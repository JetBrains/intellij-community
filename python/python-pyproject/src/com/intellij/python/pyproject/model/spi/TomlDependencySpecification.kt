package com.intellij.python.pyproject.model.spi

sealed interface TomlDependencySpecification {
  data class PathDependency(val tomlKey: String) : TomlDependencySpecification
  data class Pep621Dependency(val tomlKey: String) : TomlDependencySpecification
  data class GroupPathDependency(val tomlKeyToGroup: String, val tomlKeyFromGroupToPath: String) : TomlDependencySpecification
}