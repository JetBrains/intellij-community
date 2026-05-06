// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.settings.impl

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.annotations.NativePath
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.project.projectId
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * One-time Split-Mode only per-project migration of [TerminalTabsStorage] state from the backend to the frontend.
 *
 * Until recently the persisted terminal tabs were stored on the backend.
 * With the frontend-only terminal rework, tabs are now persisted and restored only on the frontend,
 * leaving any pre-existing tabs on the backend inaccessible to the user.
 * This service allows [TerminalTabsStorage] to request the backend's currently stored tabs and apply
 * them to the local state once per project.
 *
 * The migration is launched on the service [coroutineScope] from the first [getBackendTabsOnce] call.
 * Concurrent callers await the same [Deferred] so the RPC is performed at most once per project lifecycle.
 */
@Service(Service.Level.PROJECT)
internal class TerminalTabsStorageMigration(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {
  @Volatile
  private var migrationJob: Deferred<List<TerminalSessionPersistedTab>?>? = null

  fun getBackendTabsOnce(): List<TerminalSessionPersistedTab>? {
    if (!IdeProductMode.isFrontend) return null // Migration is needed only for Split-Mode frontend
    val props = PropertiesComponent.getInstance(project)
    if (props.getBoolean(MIGRATION_ATTEMPTED_KEY, false)) return null

    if (PlatformProjectOpenProcessor.isNewProject(project)) {
      props.setValue(MIGRATION_ATTEMPTED_KEY, true)
      LOG.info("Terminal tabs storage migration skipped for project $project: it's a new project")
      return null
    }

    val job = migrationJob ?: synchronized(this) {
      migrationJob ?: coroutineScope.async {
        getTabsFromBackend(props)
      }.also { migrationJob = it }
    }

    return if (EDT.isCurrentThreadEdt()) {
      runWithModalProgressBlocking(project, "") {
        job.await()
      }
    }
    else runBlockingMaybeCancellable {
      job.await()
    }
  }

  private suspend fun getTabsFromBackend(props: PropertiesComponent): List<TerminalSessionPersistedTab>? {
    return try {
      val dtos = withTimeoutOrNull(MIGRATION_TIMEOUT) {
        TerminalTabsStorageRemoteApi.getInstance().getStoredTabs(project.projectId())
      }
      if (dtos != null) {
        LOG.info("Terminal tabs storage migration completed for $project, migrated tabs: $dtos")
        dtos.map { migrateTab(it) }
      }
      else {
        LOG.warn("Terminal tabs storage migration failed for $project: backend call timed out")
        null
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      LOG.warn("Terminal tabs storage migration failed for $project", e)
      null
    }
    finally {
      props.setValue(MIGRATION_ATTEMPTED_KEY, true)
    }
  }

  private fun migrateTab(dto: TerminalSessionPersistedTabDto): TerminalSessionPersistedTab {
    val workingDirectory = dto.workingDirectory?.let { localPath ->
      // The provided path is local to the remote environment, but we need to store the absolute remote path in the frontend state.
      try {
        getRemoteCurrentDirectory(localPath)
      }
      catch (e: Exception) {
        LOG.warn("Failed to migrate working directory for $project: $localPath", e)
        null
      }
    }
    return TerminalSessionPersistedTab(
      name = dto.name,
      isUserDefinedName = dto.isUserDefinedName,
      shellCommand = dto.shellCommand,
      workingDirectory = workingDirectory,
      envVariables = dto.envVariables,
      processType = dto.processType,
    )
  }

  @Throws(EelPathException::class)
  private fun getRemoteCurrentDirectory(localPath: @NativePath String): @MultiRoutingFileSystemPath String {
    val eelDescriptor = project.getEelDescriptor()
    val eelPath = EelPath.parse(localPath, eelDescriptor)
    return eelPath.asNioPath().toString()
  }

  companion object {
    private val LOG = logger<TerminalTabsStorageMigration>()
    private const val MIGRATION_ATTEMPTED_KEY = "terminal.tabs.storage.migrated.from.backend.2026.2"
    private val MIGRATION_TIMEOUT = 2.seconds

    fun getInstance(project: Project): TerminalTabsStorageMigration = project.service()
  }
}