package com.intellij.python.pyproject.model.api

import com.intellij.openapi.project.Project

fun interface ModelRebuiltListener {
  /**
   * Topic called right after project was rebuilt from pyproject.toml
   */
  fun modelRebuilt(project: Project)
}