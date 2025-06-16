// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.poetry

import com.intellij.openapi.externalSystem.model.ProjectSystemId

object PoetryConstants {
  const val PYPROJECT_TOML: String = "pyproject.toml"
  const val POETRY_LOCK: String = "poetry.lock"
  val SYSTEM_ID: ProjectSystemId = ProjectSystemId("Poetry")
}