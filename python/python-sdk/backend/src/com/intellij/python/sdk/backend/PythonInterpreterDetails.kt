// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.jetbrains.python.PyNames
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap

/**
 * What this project knows about each of its interpreters, in both flows, see [isInterpreterLibraryFlowEnabled]: the
 * version, the class roots and the source roots. Every reader asks this service and follows [changes],
 * so it does not depend on where the flow keeps each value:
 * * the interpreter library flow keeps the class roots and the source roots here, in memory. The updater writes them
 *   again after each restart. [PythonInterpreterRecords] saves the rest;
 * * the legacy flow keeps all of it in the SDK of the SDK table. This service reports each change of that table.
 *
 * Each flow follows only its own source, so [changes] in the interpreter library flow reports no change of the SDK table.
 *
 * The SDK of an interpreter also holds the version and the class roots, for the code that still reads them from it. In
 * the interpreter library flow that SDK lives in memory only, see [PythonInterpreterSdks].
 *
 * The roots of an interpreter that [PythonInterpreterRegistry] no longer holds are dropped, see [retain]. All of them
 * go when the project closes, with this service.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class PythonInterpreterDetails(private val project: Project, scope: CoroutineScope) {
  private val classRoots = ConcurrentHashMap<PythonInterpreterKey, List<VirtualFile>>()
  private val sourceRoots = ConcurrentHashMap<PythonInterpreterKey, Set<VirtualFile>>()
  private val changeCount = MutableStateFlow(0L)

  init {
    scope.launch {
      // Only the source of the flow of this session, which a restart changes: the interpreter library flow ignores the SDK
      // table, which also holds other SDKs, such as a Java one.
      val source = if (isInterpreterLibraryFlowEnabled()) PythonInterpreterRecords.getInstance(project).changes else sdkTableChanges()
      source.collect { changed() }
    }
  }

  /**
   * A value for each change of the data of an interpreter: a record, the roots, or an SDK of the SDK table. It is a
   * state, not an event: a collector that starts after a change still gets it. The first value comes at once.
   */
  val changes: Flow<Unit> get() = changeCount.map { }

  /** For tests: how many changes this service has reported, see [changes]. */
  @get:TestOnly
  val changeCountForTests: Long get() = changeCount.value

  /**
   * The version of [interpreter], as the SDK updater recorded it, such as `Python 3.12.4`, or `null` before the first
   * record. The interpreter library flow reads its record, which the project saves. The legacy flow reads its SDK.
   */
  fun version(interpreter: PythonInterpreter): String? =
    if (isInterpreterLibraryFlowEnabled()) PythonInterpreterRecords.getInstance(project).recordOf(interpreter.key)?.version ?: interpreter.sdk.versionString
    else interpreter.sdk.versionString

  /** The class roots of [interpreter]: the directories of `sys.path`, the skeletons and the bundled stubs. */
  fun classRoots(interpreter: PythonInterpreter): List<VirtualFile> =
    classRoots[interpreter.key] ?: PythonSdkUtil.getPythonClassRoots(interpreter.sdk).toList()

  /** Whether the updater wrote the class roots of [interpreter] in this project. The legacy flow keeps none here. */
  fun hasClassRoots(interpreter: PythonInterpreter): Boolean = classRoots.containsKey(interpreter.key)

  /**
   * Stores [roots] as the class roots of [interpreter]. Returns whether they differ from the stored ones. Only the
   * interpreter library flow stores them here: the legacy flow keeps them in the SDK.
   */
  fun setClassRoots(interpreter: PythonInterpreter, roots: List<VirtualFile>): Boolean {
    if (classRoots.put(interpreter.key, roots.toList()) == roots) return false
    changed()
    return true
  }

  /**
   * The source roots of the interpreter of [key]: the directories of `sys.path` and the added paths that are inside a
   * module of the project, but not inside the environment. A module that uses the interpreter gets them as source roots,
   * which no `.iml` stores. Empty before the updater writes them, and in the legacy flow.
   */
  fun sourceRoots(key: PythonInterpreterKey): Set<VirtualFile> = sourceRoots[key].orEmpty()

  /** Stores [roots] as the source roots of [interpreter]. Returns whether they differ from the stored ones. */
  fun setSourceRoots(interpreter: PythonInterpreter, roots: Set<VirtualFile>): Boolean {
    if (sourceRoots.put(interpreter.key, roots.toSet()).orEmpty() == roots) return false
    changed()
    return true
  }

  /** Drops the class roots of [interpreter], so [classRoots] reads the roots of its SDK again. */
  fun clear(interpreter: PythonInterpreter) {
    if (classRoots.remove(interpreter.key) != null) changed()
  }

  /** Drops the roots of every interpreter whose key is not in [keys]. The registry calls it after each recompute. */
  internal fun retain(keys: Set<PythonInterpreterKey>) {
    classRoots.keys.retainAll(keys)
    sourceRoots.keys.retainAll(keys)
  }

  private fun changed() {
    changeCount.update { it + 1 }
  }

  /**
   * An event for each change of the SDK table and of an SDK in it, which the legacy flow reads. A commit of an SDK reports
   * no table event, only a change of its entity. Only the legacy flow follows it.
   */
  private fun sdkTableChanges(): Flow<Unit> = merge(
    callbackFlow {
      val connection = ApplicationManager.getApplication().messageBus.connect(this)
      connection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, object : ProjectJdkTable.Listener {
        override fun jdkAdded(jdk: Sdk) = sendFor(jdk)

        override fun jdkRemoved(jdk: Sdk) = sendFor(jdk)

        override fun jdkNameChanged(jdk: Sdk, previousName: String) = sendFor(jdk)

        private fun sendFor(jdk: Sdk) {
          if (PythonSdkUtil.isPythonSdk(jdk, true)) trySend(Unit)
        }
      })
      awaitClose { connection.disconnect() }
    },
    project.workspaceModel.eventLog
      .filter { event -> event.getChanges(SdkEntity::class.java).any { (it.newEntity ?: it.oldEntity)?.type == PyNames.PYTHON_SDK_ID_NAME } }
      .map { },
  )

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PythonInterpreterDetails = project.service()

    /** The class roots that the SDK of [interpreter] holds, for a test that checks what the legacy write leaves there. */
    @TestOnly
    fun sdkClassRootsForTests(interpreter: PythonInterpreter): Array<VirtualFile> = PythonSdkUtil.getPythonClassRoots(interpreter.sdk)

    /**
     * Drops the roots of [interpreter] in every open project, for a writer that has no project and writes only the
     * SDK. A reader then sees the fresh SDK roots, and never the roots stored before.
     */
    @JvmStatic
    fun clearInOpenProjects(interpreter: PythonInterpreter) {
      for (project in ProjectManager.getInstance().openProjects) {
        project.serviceIfCreated<PythonInterpreterDetails>()?.clear(interpreter)
      }
    }
  }
}
