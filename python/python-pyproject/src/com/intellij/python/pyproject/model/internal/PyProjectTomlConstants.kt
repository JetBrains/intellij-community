package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.externalSystem.model.ProjectSystemId

@Suppress("DialogTitleCapitalization") //pyproject.toml can't be capitalized
internal val PY_PROJECT_SYSTEM_ID = ProjectSystemId("pyproject.toml", PyProjectTomlBundle.message("intellij.python.pyproject.system.name"))
