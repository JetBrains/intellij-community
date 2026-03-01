package com.intellij.python.pyproject.model.internal.autoImportBridge

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * [project] can't be default, check for it
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class PyProjectAutoImportService(private val project: Project) : Disposable {
  private val m = Any()

  init {
    assert(!project.isDefault) { "Default project not supported" }
  }

  private var projectId: ExternalSystemProjectId? = null

  @get:TestOnly
  internal val initialized: Boolean get() = synchronized(m) { projectId != null }


  /**
   * Starts auto-import (`builds project module on any pyproject.toml` change). Does nothing if already started. Method is synchronized.
   * You can always [stop] it, so does [dispose]
   */
  fun start(): Unit = synchronized(m) {
    if (projectId != null) {
      log.info("PyProject already started")
      return@synchronized
    }
    else {
      val tracker = getTracker()
      val projectAware = PyExternalSystemProjectAware.create(project)
      val projectId = projectAware.projectId
      this.projectId = projectId
      tracker.register(projectAware)
      tracker.activate(projectId)
      tracker.markDirty(projectId)
      tracker.scheduleProjectRefresh()
      log.info("PyProject started")
    }
  }

  /**
   * Stop auto-import (started by [start]) does nothing if already stopped. Method is synchronized.
   */
  fun stop(): Unit = synchronized(m) {
    log.info("PyProject stopped")
    projectId?.let {
      getTracker().remove(it)
      projectId = null
    }
  }


  override fun dispose() {
    stop()
  }


  private fun getTracker(): ExternalSystemProjectTracker = ExternalSystemProjectTracker.getInstance(project)

  private companion object {
    val log = fileLogger()
  }
}
