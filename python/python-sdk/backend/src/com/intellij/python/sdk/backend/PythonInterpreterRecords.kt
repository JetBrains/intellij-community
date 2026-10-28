// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.python.sdk.common.PyInterpreterRef
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * What this project knows about each of its interpreters, in the interpreter library flow, see
 * [isInterpreterLibraryFlowEnabled]. The store saves it in the workspace file of the project, by [PythonInterpreterKey].
 * It holds no SDK: [PythonInterpreterRegistry] builds the SDK of each interpreter in memory from its record.
 *
 * The key gives the interpreter: [PythonInterpreterRegistry] builds its SDK from the ref, see
 * `FileSystem.sdkHomeAndData`. A record holds only what the ref cannot give: the UUID that names the remote sources
 * directory, the requirements path, the added and excluded paths, the version, the `sys.path`, and the path mappings of
 * a target interpreter. The project so opens without a detection.
 *
 * A target interpreter names its target by its id only. The target is a project setting, and its credentials are in
 * the credential store of the IDE.
 *
 * The workspace file is local, because a record holds local paths, like the interpreter libraries do.
 */
@Service(Service.Level.PROJECT)
@State(name = "PythonInterpreterRecords", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
@ApiStatus.Internal
class PythonInterpreterRecords : PersistentStateComponent<PythonInterpreterRecords.State> {
  /** The saved form: one record for each key. */
  class State {
    @XCollection(style = XCollection.Style.v2)
    var interpreters: MutableList<StoredInterpreter> = mutableListOf()
  }

  /** The record of one interpreter, see [PythonInterpreterRecords]. */
  @Tag("interpreter")
  class StoredInterpreter {
    @Attribute("owner")
    var ownerDir: String = ""

    @Attribute("ref")
    var ref: String = ""

    @Attribute("version")
    var version: String? = null

    @Attribute("uuid")
    var uuid: String = ""

    @Attribute("requirements")
    var requirementsFile: String? = null

    @XCollection(propertyElementName = "added", elementName = "path", valueAttributeName = "url")
    var addedPaths: MutableList<String> = mutableListOf()

    @XCollection(propertyElementName = "excluded", elementName = "path", valueAttributeName = "url")
    var excludedPaths: MutableList<String> = mutableListOf()

    /**
     * The `sys.path` of the interpreter, as the SDK updater read it last, see [PythonInterpreterSdks.sysPathOf].
     */
    @XCollection(propertyElementName = "sysPath", elementName = "path", valueAttributeName = "value")
    var sysPath: MutableList<String> = mutableListOf()

    /** The path mappings of a target interpreter, as pairs of a local and a remote root. */
    @XCollection(propertyElementName = "mappings")
    var pathMappings: MutableList<StoredPathMapping> = mutableListOf()

    /** The key of this record, or `null` when its ref does not parse. */
    fun key(): PythonInterpreterKey? = PyInterpreterRef.parse(ref)?.let { PythonInterpreterKey(Path.of(ownerDir), it) }
  }

  /** One path mapping of a target interpreter, see [StoredInterpreter.pathMappings]. */
  @Tag("mapping")
  class StoredPathMapping {
    @Attribute("local")
    var local: String = ""

    @Attribute("remote")
    var remote: String = ""
  }

  private val records = ConcurrentHashMap<PythonInterpreterKey, StoredInterpreter>()

  private val version = MutableStateFlow(0L)

  /**
   * A value for each change of the store: a record is put or removed. A change of the SDK of an interpreter reaches
   * the store through [PythonInterpreterSdks.sdkChanged], which puts its record again.
   *
   * It is a state, not an event: a collector that starts after a change still gets it, so a change made before the
   * registry subscribes is not lost.
   */
  val changes: Flow<Unit> get() = version.map { }

  /** For tests: how many changes the store has reported, see [changes]. */
  @get:TestOnly
  val changeCount: Long get() = version.value

  /** The record of [key], or `null`. */
  fun recordOf(key: PythonInterpreterKey): StoredInterpreter? = records[key]

  /** Every key that has a record. */
  fun keys(): Set<PythonInterpreterKey> = records.keys.toSet()

  /** Stores [record] as the record of [key], and replaces the one stored before. Reports a change when it differs. */
  fun put(key: PythonInterpreterKey, record: StoredInterpreter) {
    val before = records.put(key, record)
    if (before == null || !before.sameAs(record)) version.update { it + 1 }
  }

  /** Removes the record of [key]. Returns it, or `null` when there was none. */
  fun remove(key: PythonInterpreterKey): StoredInterpreter? = records.remove(key)?.also { version.update { it + 1 } }

  override fun getState(): State = State().apply { interpreters += records.values }

  override fun loadState(state: State) {
    records.clear()
    for (record in state.interpreters) {
      val key = record.key() ?: continue
      records[key] = record
    }
    version.update { it + 1 }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PythonInterpreterRecords = project.service()
  }
}

/** Whether this record states the same as [other], so a put of [other] changes nothing. */
private fun PythonInterpreterRecords.StoredInterpreter.sameAs(other: PythonInterpreterRecords.StoredInterpreter): Boolean =
  ownerDir == other.ownerDir && ref == other.ref && uuid == other.uuid && requirementsFile == other.requirementsFile &&
  addedPaths == other.addedPaths && excludedPaths == other.excludedPaths &&
  version == other.version && sysPath == other.sysPath &&
  pathMappings.map { it.local to it.remote } == other.pathMappings.map { it.local to it.remote }
