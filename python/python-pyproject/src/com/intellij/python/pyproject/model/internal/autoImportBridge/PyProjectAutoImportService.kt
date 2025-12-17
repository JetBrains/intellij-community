package com.intellij.python.pyproject.model.internal.autoImportBridge

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class PyProjectAutoImportService(private val project: Project, internal val scope: CoroutineScope) : Disposable {
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