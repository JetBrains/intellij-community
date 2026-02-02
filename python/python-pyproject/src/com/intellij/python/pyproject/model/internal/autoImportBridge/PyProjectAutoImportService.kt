package com.intellij.python.pyproject.model.internal.autoImportBridge

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker
import com.intellij.openapi.project.Project

/**
 * [project] can't be default, check for it
 */
@Service(Service.Level.PROJECT)
internal class PyProjectAutoImportService(private val project: Project) : Disposable {
  init {
    assert(!project.isDefault) { "Default project not supported" }
  }

  @Volatile
  private var projectId: ExternalSystemProjectId? = null


  /**
   * Starts auto-import (`builds project module on any pyproject.toml` change). To be called only once!
   */
  internal suspend fun start() {
    assert(projectId == null) { "Already started, do not call second time" }
    val tracker = getTracker()
    val projectAware = PyExternalSystemProjectAware.create(project)
    val projectId = projectAware.projectId
    this.projectId = projectId
    tracker.register(projectAware)
    tracker.activate(projectId)
    tracker.markDirty(projectId)
    tracker.scheduleProjectRefresh()
  }


  override fun dispose() {
    projectId?.let {
      getTracker().remove(it)
      projectId = null
    }
  }


  private fun getTracker(): ExternalSystemProjectTracker = ExternalSystemProjectTracker.getInstance(project)
}