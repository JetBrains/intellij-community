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
  private lateinit var projectId: ExternalSystemProjectId


  suspend fun start() {
    val tracker = getTracker()
    val projectAware = PyExternalSystemProjectAware.create(project)
    projectId = projectAware.projectId
    tracker.register(projectAware)
    tracker.activate(projectId)
    refresh()
  }

  fun refresh() {
    val tracker = getTracker()
    tracker.markDirty(projectId)
    tracker.scheduleProjectRefresh()
  }

  override fun dispose() {
    getTracker().remove(projectId)
  }


  private fun getTracker(): ExternalSystemProjectTracker = ExternalSystemProjectTracker.getInstance(project)
}