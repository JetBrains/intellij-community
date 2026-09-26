package com.intellij.python.pyproject.model.internal.pyProject

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.impl.PyProjectService

internal class PyProjectServiceImpl : PyProjectService {
  override suspend fun getPyProject(module: Module): PyProject? = PyProjectImpl.create(module, module.project.getSnapshot())
  override suspend fun getPyProjects(project: Project): List<PyProject> {
    val storage = project.getSnapshot()
    return project.modules.mapNotNull { PyProjectImpl.create(it, storage) }
  }
}

private suspend fun Project.getSnapshot(): ImmutableEntityStorage {
  @Suppress("UnsafeOpenServiceCast") // See IJPL-249625
  val workspaceModel = WorkspaceModel.getInstance(this) as WorkspaceModelInternal
  workspaceModel.awaitSynchronizationWithJpsModel()
  return workspaceModel.currentSnapshot
}
