package com.intellij.python.pyproject.model.spi

import com.intellij.python.pyproject.PyProjectToml
import com.jetbrains.python.venvReader.Directory

/**
 * Particular project described by `pyproject.toml`
 */
interface PyProjectTomlProject {
  /**
   * Wrapper over toml file
   */
  val pyProjectToml: PyProjectToml

  /**
   * Project root directory: `pyproject.toml` sits in it
   */
  val root: Directory
}