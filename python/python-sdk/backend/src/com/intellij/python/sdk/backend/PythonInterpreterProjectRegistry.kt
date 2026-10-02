// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.jetbrains.python.project.PyProject.Companion.getPyProjects
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.associatedModuleNioPath
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * The interpreters this project can use.
 *
 * The set holds the interpreters of the SDK table that are shared, with no associated module, or that are associated
 * with a Python project of this one. An SDK whose interpreter is not here is broken or belongs to another project.
 *
 * For now it is a view of the SDK table. It will become the storage of the interpreters of this project. A global
 * registry will hold the shared ones.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class PythonInterpreterProjectRegistry(private val project: Project, scope: CoroutineScope) {
  private val state = MutableStateFlow<Set<PythonInterpreter>?>(null)

  init {
    scope.launch {
      // A module or a content root change can move a base dir, so it changes which associated SDKs this project can use.
      val baseDirChanges = WorkspaceModel.getInstance(project).eventLog
        .filter { change -> BASE_DIR_ENTITIES.any { change.getChanges(it).isNotEmpty() } }
        .map { }
      merge(baseDirChanges, sdkTableChanges())
        .onStart { emit(Unit) }
        .conflate()
        .collect { state.value = compute() }
    }
  }

  /** The current set, awaiting the first computation when it has not landed yet. */
  suspend fun interpreters(): Set<PythonInterpreter> = state.filterNotNull().first()

  /** The current set, or `null` while the first computation is still running. */
  fun interpretersOrNull(): Set<PythonInterpreter>? = state.value

  private suspend fun compute(): Set<PythonInterpreter> {
    val baseDirs = project.getPyProjects().mapTo(mutableSetOf()) { it.baseDir }
    return PythonSdkUtil.getAllSdks()
      .filter { it.sdkAdditionalData is PythonSdkAdditionalData && it.isAvailableFor(baseDirs) }
      .mapTo(mutableSetOf()) { it.pythonInterpreterAsync() }
  }

  /** An event for each change of the SDK table. The table is small, so every change starts a recompute. */
  private fun sdkTableChanges(): Flow<Unit> = callbackFlow {
    val connection = ApplicationManager.getApplication().messageBus.connect(this)
    connection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, object : ProjectJdkTable.Listener {
      override fun jdkAdded(jdk: Sdk) = trySend(Unit).let { }

      override fun jdkRemoved(jdk: Sdk) = trySend(Unit).let { }

      override fun jdkNameChanged(jdk: Sdk, previousName: String) = trySend(Unit).let { }
    })
    awaitClose { connection.disconnect() }
  }

  companion object {
    fun getInstance(project: Project): PythonInterpreterProjectRegistry = project.service()

    private val BASE_DIR_ENTITIES = listOf(ModuleEntity::class.java, ContentRootEntity::class.java)
  }
}

/** Whether this SDK is shared, with no associated module, or is associated with one of [baseDirs]. */
private fun Sdk.isAvailableFor(baseDirs: Set<Path>): Boolean {
  val associatedPath = associatedModuleNioPath
  return associatedPath == null || associatedPath in baseDirs
}

/**
 * The interpreter of [sdk], from [PythonInterpreterProjectRegistry]. Waits for the first computation.
 * For a caller that the platform passes an [Sdk]. `null` means that this project cannot use [sdk]: it is broken, or it
 * is associated with another project.
 */
@ApiStatus.Internal
suspend fun Project.findPythonInterpreter(sdk: Sdk): PythonInterpreter? =
  PythonInterpreterProjectRegistry.getInstance(this).interpreters().firstOrNull { it.isFor(sdk) }

/** [findPythonInterpreter] for [sdk] without the wait. `null` also before the first computation. */
@ApiStatus.Internal
fun Project.findPythonInterpreterIfReady(sdk: Sdk): PythonInterpreter? =
  PythonInterpreterProjectRegistry.getInstance(this).interpretersOrNull()?.firstOrNull { it.isFor(sdk) }
