// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.impl.PythonInterpreterStore
import com.intellij.python.sdk.common.PyInterpreterRef
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.project.PyProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * The interpreters of each [PyProject] of this project, and the one way to add or remove one.
 *
 * Each interpreter belongs to one [PyProject], its owner, see [PythonInterpreter.ownerDir]. A [PythonInterpreterKey]
 * names an interpreter: the owner directory and the [PyInterpreterRef].
 *
 * A flow builds an interpreter with [build]. That registers nothing. Only `PyProject.setPythonInterpreter` registers
 * it, through [register]. A [PyProject] can use an interpreter of another owner. For example, Bazel builds one
 * interpreter for each environment and sets it on many modules.
 *
 * The registry computes the list of its interpreters in the background. A suspending read waits for the first
 * computation. A read with the suffix `IfReady` does not wait, and it finds nothing before the first computation.
 *
 * This class is the API. [PythonInterpreterStore] keeps the state and the storage. In the interpreter library flow,
 * see [isInterpreterLibraryFlowEnabled], the storage is [PythonInterpreterRecords]. In the legacy flow, it is the SDK
 * table of the application.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class PythonInterpreterRegistry(project: Project, scope: CoroutineScope) {
  /** The state and the storage behind this API. */
  internal val store: PythonInterpreterStore = PythonInterpreterStore(project, scope)

  /**
   * The interpreters that [pyProject] owns. An interpreter of another owner that [pyProject] uses is not in the list.
   * In the legacy flow, two SDKs can have one ref, so the list can hold two equal interpreters.
   */
  suspend fun interpreters(pyProject: PyProject): List<PythonInterpreter> = store.interpretersFlow(pyProject).first()

  /** [interpreters] without the wait. The list is empty before the first computation. */
  fun interpretersIfReady(pyProject: PyProject): List<PythonInterpreter> = store.interpretersIfReady(pyProject)

  /**
   * The interpreter that [ref] names for [pyProject]. When [pyProject] already owns an interpreter for [ref], that
   * interpreter is the result. Otherwise the result is a new interpreter that is not registered. Register it with
   * [register].
   *
   * The file system of [ref] gives the Python binary and its data, see
   * [com.jetbrains.python.sdk.add.v2.FileSystem.sdkHomeAndData]. A failure when the environment of [ref] is not found.
   */
  suspend fun build(pyProject: PyProject, ref: PyInterpreterRef): PyResult<PythonInterpreter> =
    store.build(pyProject, ref)

  /**
   * Registers [interpreter] under its owner and returns the interpreter that the registry then holds. Only
   * `PyProject.setPythonInterpreter` calls it. The [PyProject] that sets [interpreter] does not have to be its owner.
   *
   * When the owner already has an interpreter for the ref of [interpreter], the result is that interpreter. In the
   * legacy flow, a local SDK of the same binary is adopted, when it has the same owner or no owner. When the setup of
   * the paths fails, the registry removes the interpreter again and the call throws.
   */
  suspend fun register(interpreter: PythonInterpreter): PythonInterpreter = store.register(interpreter)

  /**
   * Removes [interpreter]. Returns when the list no longer holds it. The registry does not check that a module uses
   * [interpreter], so the caller checks it.
   */
  suspend fun remove(interpreter: PythonInterpreter) {
    store.remove(interpreter)
  }

  /**
   * How many Python SDKs of the SDK table name no interpreter. No list shows them, so All Interpreters offers to remove
   * them. The interpreter library flow has none, so there it is `0`.
   */
  suspend fun brokenSdkCount(): Int = store.brokenTableSdks().size

  /** Removes the SDKs that [brokenSdkCount] counts. */
  suspend fun removeBrokenSdks() {
    store.removeBrokenTableSdks()
  }

  /**
   * The registered interpreter that [key] names, or `null`. A module of the legacy flow names an SDK instead, see
   * `findBySdkName` in `com.jetbrains.python.sdk.legacy`.
   */
  suspend fun find(key: PythonInterpreterKey): PythonInterpreter? = store.allFlow.first().firstOrNull { it.key == key }

  /** [find] without the wait. The result is `null` before the first computation. */
  fun findIfReady(key: PythonInterpreterKey): PythonInterpreter? = store.allIfReady?.firstOrNull { it.key == key }

  /** The keys of all the registered interpreters, each time the set of keys changes. */
  val keysFlow: Flow<Set<PythonInterpreterKey>>
    get() = store.allFlow.map { list -> list.mapTo(mutableSetOf()) { it.key } }.distinctUntilChanged()

  /**
   * Stores a record for [key] when [PythonInterpreterRecords] has none. The record holds the owner, the ref and a new
   * UUID. Returns `true` when it stored a record.
   *
   * A module names [key] by its interpreter library entry. A project without the record, such as a fresh checkout,
   * still gets the interpreter this way. The next computation builds the interpreter. While the environment is gone,
   * the record stays and the interpreter is not in the list. In the legacy flow, the call does nothing and returns
   * `false`.
   */
  fun ensureRecord(key: PythonInterpreterKey): Boolean = store.ensureRecord(key)

  /**
   * [find] for a [key] that a module names by its interpreter library entry. It calls [ensureRecord] first, and it
   * waits for the computation that reads the record. The result is `null` while the environment is gone.
   *
   * A caller that needs a true answer at this instant uses it, such as the configuration lock. The call writes the
   * records, so the computation of a snapshot does not call it.
   */
  suspend fun findEnsuringRecord(key: PythonInterpreterKey): PythonInterpreter? = store.findEnsuringRecord(key)

  /**
   * The interpreters of all the [PyProject]s of this project, for All Interpreters. That page shows them as a history.
   * An SDK that names no interpreter is not in the list. For the interpreters of one [PyProject], use [interpreters].
   */
  suspend fun allInterpreters(): List<PythonInterpreter> = store.allInterpreters()

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PythonInterpreterRegistry = project.service()
  }
}

/**
 * The owner directory and the ref of an interpreter. The key names the interpreter in a module, see
 * [PythonInterpreterRegistry.find]. A ref is unique only for one owner, so two owners can have one ref.
 */
@ApiStatus.Internal
data class PythonInterpreterKey(val ownerDir: Path, val ref: PyInterpreterRef)

/** The key of this interpreter, see [PythonInterpreterKey]. */
@get:ApiStatus.Internal
val PythonInterpreter.key: PythonInterpreterKey get() = PythonInterpreterKey(ownerDir, ref)
