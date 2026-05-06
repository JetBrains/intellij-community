// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.settings.impl

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
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
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import kotlin.time.Duration.Companion.seconds

/**
 * One-time Split-Mode only per-project migration of [org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider] state from the backend to the frontend.
 *
 * Until recently the terminal project options were stored on the backend.
 * With the frontend-only terminal rework, settings are now modified and used only on the frontend,
 * leaving any pre-existing custom values on the backend inaccessible to the user.
 * This service requests the backend's current values via [TerminalProjectOptionsRemoteApi] and applies
 * them to the local state once per project.
 *
 * The migration is launched on the service [coroutineScope] from the first [getBackendStateOnce] call.
 * Concurrent callers await the same [Deferred] so the RPC is performed at most once per project lifecycle.
 */
@Service(Service.Level.PROJECT)
internal class TerminalProjectOptionsMigration(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {
  @Volatile
  private var migrationJob: Deferred<TerminalProjectOptionsProvider.State?>? = null

  fun getBackendStateOnce(): TerminalProjectOptionsProvider.State? {
    if (!IdeProductMode.isFrontend) return null // Migration is needed only for Split-Mode frontend
    val props = PropertiesComponent.getInstance(project)
    if (props.getBoolean(MIGRATION_ATTEMPTED_KEY, false)) return null

    if (PlatformProjectOpenProcessor.isNewProject(project)) {
      props.setValue(MIGRATION_ATTEMPTED_KEY, true)
      LOG.info("Terminal project options migration skipped for project $project: it's a new project")
      return null
    }

    val job = migrationJob ?: synchronized(this) {
      migrationJob ?: coroutineScope.async {
        getStateFromBackend(props)
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

  private suspend fun getStateFromBackend(props: PropertiesComponent): TerminalProjectOptionsProvider.State? {
    return try {
      val dto = withTimeoutOrNull(MIGRATION_TIMEOUT) {
        TerminalProjectOptionsRemoteApi.getInstance().getProjectOptions(project.projectId())
      }
      if (dto != null) {
        LOG.info("Terminal project options migration completed for $project, migrated: $dto")
        migrateState(dto)
      }
      else {
        LOG.warn("Terminal project options migration failed for $project: backend call timed out")
        null
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      LOG.warn("Terminal project options migration failed for $project", e)
      null
    }
    finally {
      props.setValue(MIGRATION_ATTEMPTED_KEY, true)
    }
  }

  private fun migrateState(fromState: TerminalProjectOptionsDto): TerminalProjectOptionsProvider.State {
    val toState = TerminalProjectOptionsProvider.State()

    fromState.shellPath?.let {
      toState.shellPath = it
    }
    fromState.envData?.let {
      toState.envDataOptions.set(EnvironmentVariablesData.create(it.envs, it.isPassParentEnvs))
    }
    fromState.startingDirectory?.let { localPath ->
      // The provided path is local to the remote environment, but we need to store the absolute remote path in the state.
      val remotePath = try {
        getRemoteCurrentDirectory(localPath)
      }
      catch (e: Exception) {
        LOG.warn("Failed to migrate starting directory for $project: $localPath", e)
        null
      }
      if (remotePath != null) {
        toState.startingDirectory = remotePath
      }
    }

    return toState
  }

  @Throws(EelPathException::class)
  private fun getRemoteCurrentDirectory(localPath: @NativePath String): @MultiRoutingFileSystemPath String {
    val eelDescriptor = project.getEelDescriptor()
    val eelPath = EelPath.parse(localPath, eelDescriptor)
    return eelPath.asNioPath().toString()
  }

  companion object {
    private val LOG = Logger.getInstance(TerminalProjectOptionsMigration::class.java)
    private const val MIGRATION_ATTEMPTED_KEY = "terminal.project.options.migrated.from.backend.2026.2"
    private val MIGRATION_TIMEOUT = 2.seconds

    fun getInstance(project: Project): TerminalProjectOptionsMigration = project.service()
  }
}