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

  override fun getPyProjectsWithoutJpsSync(project: Project): List<PyProject> = project.getPyProjectsWithCurrentStorage().first
}

/** Every `PyProject` of [this] project, and the storage they were read from. Read more data from the same storage. */
internal suspend fun Project.getPyProjectsWithStorage(): Pair<List<PyProjectImpl>, ImmutableEntityStorage> {
  val storage = getSnapshot()
  return modules.mapNotNull { PyProjectImpl.create(it, storage) } to storage
}

/**
 * [getPyProjectsWithStorage] without the wait for the synchronization with the JPS model. While the project opens, the
 * storage can be the workspace model cache of the last session. For a reader that recomputes on each change of the
 * workspace model, such as `EvoPyProjectModel`: the synchronization is such a change.
 */
internal fun Project.getPyProjectsWithCurrentStorage(): Pair<List<PyProjectImpl>, ImmutableEntityStorage> {
  val storage = WorkspaceModel.getInstance(this).currentSnapshot
  return modules.mapNotNull { PyProjectImpl.create(it, storage) } to storage
}

private suspend fun Project.getSnapshot(): ImmutableEntityStorage {
  @Suppress("UnsafeOpenServiceCast") // See IJPL-249625
  val workspaceModel = WorkspaceModel.getInstance(this) as WorkspaceModelInternal
  workspaceModel.awaitSynchronizationWithJpsModel()
  return workspaceModel.currentSnapshot
}
