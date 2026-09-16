// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.backend

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.python.pytools.common.PyToolDependencyGroupDto
import com.intellij.python.pytools.common.PyToolSdkInstallRequest
import com.intellij.python.pytools.common.PyToolSdkOperationResultDto
import com.intellij.python.pytools.common.PyToolSdkRequest
import com.intellij.python.pytools.common.PyToolSdkStateDto
import org.jetbrains.annotations.ApiStatus

/** Backend SDK integration kept outside this module to preserve one-way module dependencies. */
@ApiStatus.Internal
interface PyToolSdkBackendService {
  suspend fun getStates(project: Project, tool: PyTool<*>): List<PyToolSdkStateDto>
  suspend fun getDependencyGroups(project: Project, request: PyToolSdkRequest): List<PyToolDependencyGroupDto>
  suspend fun install(project: Project, tool: PyTool<*>, request: PyToolSdkInstallRequest): PyToolSdkOperationResultDto

  companion object {
    fun getInstance(): PyToolSdkBackendService = service()
  }
}
