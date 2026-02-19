// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.injection

internal object TomlRequirementsInjectionSupport {
  fun isSupported(sectionName: String, fieldName: String): Boolean = templates.any { template ->
    template.sectionName == sectionName &&
    (template.fieldName == null || template.fieldName == fieldName)
  }


  private val templates = listOf(
    TomlDependencyTemplate("project", "dependencies"),
    TomlDependencyTemplate("build-system", "requires"),
    TomlDependencyTemplate("project.optional-dependencies", null),
    TomlDependencyTemplate("dependency-groups", null),
    TomlDependencyTemplate("tool.uv", "dev-dependencies"),
    TomlDependencyTemplate("tool.hatch.envs.default", "dependencies"),
    TomlDependencyTemplate("tool.hatch.envs.default.overrides", "dev"),
  )

  private data class TomlDependencyTemplate(val sectionName: String, val fieldName: String?)
}