// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.project.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.python.project.PyProject
import org.jetbrains.annotations.ApiStatus

// Implementation detail, do not use
@ApiStatus.Internal
@ApiStatus.NonExtendable
interface PyProjectService {
  suspend fun getPyProject(module: Module): PyProject?
  suspend fun getPyProjects(project: Project): List<PyProject>
}
