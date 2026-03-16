// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import org.jetbrains.annotations.ApiStatus

/**
 * A package name that is guaranteed to be normalized and not a module name.
 * Use [create] to construct — returns `null` if the package belongs to the module.
 */
@ApiStatus.Internal
class NonModulePackageName private constructor(val name: String) {
  companion object {
    private fun moduleNames(project: Project): Set<String> =
      project.modules.mapTo(mutableSetOf()) { PyPackageName.normalizePackageName(it.name) }

    fun create(packageName: String, project: Project): NonModulePackageName? {
      val normalized = PyPackageName.normalizePackageName(packageName)
      if (normalized in moduleNames(project)) return null
      return NonModulePackageName(normalized)
    }
  }
}
