// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.uv

import com.intellij.openapi.externalSystem.model.ProjectSystemId

object UvConstants {
  const val UV_LOCK: String = "uv.lock"
  const val PYPROJECT_TOML: String = "pyproject.toml"
  val SYSTEM_ID: ProjectSystemId = ProjectSystemId("uv")
}