// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.evolution

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntity
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.python.pyproject.model.internal.pyProject.awaitSynchronizationWithJpsModel
import com.intellij.python.sdk.backend.isInterpreterLibraryFlowEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val logger = fileLogger()

/**
 * Runs [migrateSdkReferences] in a coroutine of its own: once after the synchronization with the JPS model, and again
 * on each change of the workspace model that can bring a reference of an older build, such as an `.iml` from VCS. A
 * write of the migration can wait, for example for the end of a modal progress. Here that wait stops no snapshot of
 * [EvoPyProjectModel].
 *
 * The legacy flow reads the SDK of each module, see [isInterpreterLibraryFlowEnabled], so it migrates nothing.
 */
@Service(Service.Level.PROJECT)
internal class PySdkReferenceMigration(private val project: Project, scope: CoroutineScope) {
  private val mutex = Mutex()

  init {
    if (isInterpreterLibraryFlowEnabled()) {
      scope.launch {
        project.awaitSynchronizationWithJpsModel()
        // The migration of a change writes a change of its own, and that run finds nothing left.
        WorkspaceModel.getInstance(project).eventLog
          .filter { it.canBringSdkReference() }
          .map { }
          .onStart { emit(Unit) }
          .conflate()
          .collect { logger.runAndLogException { migrate() } }
      }
    }
  }

  /**
   * Runs the migration now, for a caller that waits for an interpreter: an SDK that was written a moment ago gives its
   * entry before the wait. It waits for the synchronization with the JPS model, so the SDK table is complete.
   */
  suspend fun migrateNow() {
    if (!isInterpreterLibraryFlowEnabled()) return
    project.awaitSynchronizationWithJpsModel()
    migrate()
  }

  private suspend fun migrate() {
    mutex.withLock { migrateSdkReferences(project) }
  }

  companion object {
    fun getInstance(project: Project): PySdkReferenceMigration = project.service()
  }
}

/** Whether this change can give a module an SDK reference, a Python facet or the Python type, or give the project an SDK. */
private fun VersionedStorageChange.canBringSdkReference(): Boolean =
  getChanges(ModuleEntity::class.java).isNotEmpty() ||
  getChanges(FacetEntity::class.java).isNotEmpty() ||
  getChanges(ProjectSettingsEntity::class.java).isNotEmpty()
