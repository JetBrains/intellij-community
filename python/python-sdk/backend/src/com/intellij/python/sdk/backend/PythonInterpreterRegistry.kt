// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.python.sdk.common.PyInterpreterRef
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.PyProject.Companion.getPyProjects
import com.jetbrains.python.project.project
import com.jetbrains.python.sdk.PyRemoteSdkAdditionalDataMarker
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.pythonBinaryPath
import com.jetbrains.python.sdk.readPythonSdkForConfiguration
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * The interpreters of each [PyProject] of this project, and the one way to add or remove a Python SDK.
 *
 * Every interpreter belongs to one [PyProject], see [PythonInterpreter.ownerDir]. There are no shared interpreters. An
 * interpreter is found by its [PyInterpreterRef].
 *
 * A flow builds an interpreter with [newPythonInterpreter], which adds nothing to the SDK table. Only
 * `PyProject.setPythonInterpreter` adds it, through [registerPythonInterpreter]. A [PyProject] can use an interpreter
 * of another owner. An importer such as Bazel does so: it builds one interpreter for each environment and sets it on
 * many modules. The UI lists only the interpreters a [PyProject] owns, see [interpreters].
 *
 * In the interpreter library flow, see [isInterpreterLibraryFlowEnabled], the [PythonInterpreterRecords] of the project
 * saves a record for each interpreter, and the registry builds its SDK in memory from the ref and that record, see
 * [PythonInterpreterSdks]. The SDKs are in no SDK table, and the key of each one gives its owner, so nothing is adopted
 * and no owner changes. The only read of the SDK table imports the SDK that a module of an older build names, see
 * [importTableSdk]. The SDK table keeps the SDKs of an older build as they are, so that build still opens the project.
 *
 * In the legacy flow the storage is the SDK table of the application. An SDK belongs to the [PyProject] whose base
 * directory is its associated path. An SDK with no associated path belongs to the [PyProject] whose base directory is
 * its working directory. Any other SDK has no owner: this registry does not show it, and adopts it for the first
 * [PyProject] that registers an interpreter for the same binary.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class PythonInterpreterRegistry(private val project: Project, private val scope: CoroutineScope) {
  /**
   * One computed list, with one interpreter for each SDK. It compares by reference, so each recompute emits. It is a
   * list because [PythonInterpreter] compares by its ref, and two SDKs can have one ref. A set keeps only one of them, so
   * a lookup by the other SDK finds nothing.
   */
  private class Computed(val interpreters: List<PythonInterpreter>)

  private val state = MutableStateFlow<Computed?>(null)

  /**
   * Makes the search for an SDK to adopt and the add one step. Without it, two parallel adds for one Python both find
   * nothing to adopt, and both add an SDK.
   */
  private val registerMutex = Mutex()

  init {
    // A change made outside this registry, for example by the platform Settings, reaches the list through this listener.
    scope.launch {
      sdkTableChanges()
        .onStart { emit(Unit) }
        .conflate()
        .collect {
          val interpreters = compute()
          state.value = Computed(interpreters)
          // The roots of an interpreter that the registry no longer holds are of no use, see PythonInterpreterDetails.
          project.serviceIfCreated<PythonInterpreterDetails>()?.retain(interpreters.mapTo(mutableSetOf()) { it.key })
        }
    }
  }

  /** Every Python SDK of the table, each time the table changes. The first value comes after the first computation. */
  private val tableFlow: Flow<List<PythonInterpreter>> = state.filterNotNull().map { it.interpreters }

  /**
   * The interpreters that [pyProject] owns, one for each SDK. Two SDKs can have one ref, so two entries can be equal.
   * An interpreter of another owner that [pyProject] uses is not here, so the UI does not show it. Waits for the first
   * computation.
   */
  suspend fun interpreters(pyProject: PyProject): List<PythonInterpreter> = interpretersFlow(pyProject).first()

  /** [interpreters] each time they change. The first value comes after the first computation. */
  fun interpretersFlow(pyProject: PyProject): Flow<List<PythonInterpreter>> =
    tableFlow.map { it.belongingTo(pyProject) }.distinctUntilChanged()

  /** The interpreter of [pyProject] that [ref] names, or `null` when [pyProject] has none. Waits for the first computation. */
  private suspend fun findPythonInterpreter(pyProject: PyProject, ref: PyInterpreterRef): PythonInterpreter? =
    interpreters(pyProject).firstOrNull { it.ref == ref }

  /**
   * The interpreter that [ref] names for [pyProject], before it is registered: its SDK is not in the SDK table yet,
   * unless [pyProject] already has an interpreter for [ref]. The file system of [ref] gives the SDK home path and the
   * SDK data, see [com.jetbrains.python.sdk.add.v2.FileSystem.sdkHomeAndData]. Register it with
   * [registerPythonInterpreter].
   */
  suspend fun newPythonInterpreter(pyProject: PyProject, ref: PyInterpreterRef): PyResult<PythonInterpreter> {
    findPythonInterpreter(pyProject, ref)?.let { return PyResult.success(it) }
    val fileSystem = ref.fileSystem(pyProject.project)
                     ?: return PyResult.localizedError(PySdkBundle.message("evolution.error.env.not.found", ref.toString()))
    val (homePath, sdkData) = fileSystem.sdkHomeAndData(pyProject, ref).getOr { return it }
    sdkData.associatedModulePath = pyProject.baseDir.toString()
    return newSdk(homePath, sdkData, suggestedName = null).pythonInterpreterResultAsync()
  }

  /**
   * The interpreter whose Python binary is [pythonBinary] with the data [data], for the PyProject at [ownerDir], before
   * it is registered: its SDK is not in the SDK table yet. [ownerDir] is the base directory of that PyProject. The
   * PyProject does not have to exist yet: the new project wizard passes the directory of the project it creates.
   *
   * A flow that has just created the environment builds it from what it knows, so the environment is not probed
   * again. Register it with [registerPythonInterpreter]. A failure when the binary is not found, or when [data] names no
   * interpreter.
   */
  suspend fun newPythonInterpreter(
    ownerDir: Path,
    pythonBinary: PathHolder,
    data: PythonSdkAdditionalData,
    suggestedName: String? = null,
  ): PyResult<PythonInterpreter> {
    val homePath = when (pythonBinary) {
      // The SDK home path is the path of the VFS file, so it matches what the SDK table holds for the same binary.
      is PathHolder.Eel -> withContext(Dispatchers.IO) { VirtualFileManager.getInstance().refreshAndFindFileByNioPath(pythonBinary.path) }?.path
                           ?: return PyResult.localizedError(PySdkBundle.message("evolution.error.env.not.found", pythonBinary.path.toString()))
      is PathHolder.Target -> pythonBinary.pathString
    }
    data.associatedModulePath = ownerDir.toString()
    return newSdk(homePath, data, suggestedName).pythonInterpreterResultAsync()
  }

  /**
   * Registers [interpreter] under its owner, see [PythonInterpreter.ownerDir], and returns the interpreter the registry
   * then holds. Only `PyProject.setPythonInterpreter` calls it. The [PyProject] that sets [interpreter] does not have to
   * be its owner: an importer such as Bazel builds one interpreter for each environment and sets it on many modules.
   *
   * The owner keeps an interpreter it already has for the ref of [interpreter]. Otherwise a local SDK for the same
   * binary is adopted, when it has the same owner or none. It takes the data of [interpreter] and keeps its name, and
   * [interpreter] then holds that SDK. Else the SDK of [interpreter] is added, and its paths are set up. When that fails,
   * the SDK is removed again.
   */
  suspend fun registerPythonInterpreter(interpreter: PythonInterpreter): PythonInterpreter {
    if (isRegistered(interpreter)) return interpreter
    tableFlow.first().firstOrNull { it.ownerDir == interpreter.ownerDir && it.ref == interpreter.ref }?.let { return it }
    return registerInterpreter(interpreter) { it == null || it == interpreter.ownerDir }
  }

  /** Whether the registry holds [interpreter]: its owner registered it. Waits for the first computation. */
  suspend fun isRegistered(interpreter: PythonInterpreter): Boolean = tableFlow.first().any { it.isFor(interpreter.sdk) }

  /** A Python SDK at [homePath] with the data [data] that is not in the SDK table. */
  private suspend fun newSdk(homePath: String, data: PythonSdkAdditionalData, suggestedName: String?, knownVersion: String? = null): Sdk {
    val sdkType = pythonSdkType()
    // In the library flow the SDK is in no table, so its name is unique among the stored SDKs only.
    val existing = if (isInterpreterLibraryFlowEnabled()) PythonInterpreterSdks.getInstance(project).sdks() else PythonSdkTable.tableSdks()
    @Suppress("SETUP_SDK_DIRECTLY") // The registry is the only place that creates a Python SDK.
    val sdk = SdkConfigurationUtil.createSdk(existing, homePath, sdkType, data, suggestedName)
    // A version that the store saved needs no probe, so a project opens without running its interpreters.
    if (knownVersion != null && sdk.versionString != knownVersion) {
      edtWriteAction {
        val modificator = sdk.sdkModificator
        modificator.versionString = knownVersion
        modificator.commitChanges()
      }
    }
    // The version of a target interpreter is not read from its home path, so the target answers for it. A target
    // interpreter has no detected environment, so this version is the only one it states before it is registered.
    if (sdk.versionString == null) {
      val version = withContext(Dispatchers.IO) { sdkType.getVersionString(sdk) } ?: return sdk
      edtWriteAction {
        val modificator = sdk.sdkModificator
        modificator.versionString = version
        modificator.commitChanges()
      }
    }
    return sdk
  }

  private fun pythonSdkType(): SdkType = SdkType.findByName(PyNames.PYTHON_SDK_ID_NAME) ?: error("The Python SDK type is not registered")

  /**
   * Adds the SDK of [interpreter] to the table, or adopts a local SDK whose owner directory passes [canAdopt], see
   * [ownerDirectory]. An adopted SDK takes the data of [interpreter], and [interpreter] then holds it.
   */
  private suspend fun registerInterpreter(interpreter: PythonInterpreter, canAdopt: (Path?) -> Boolean): PythonInterpreter {
    val sdk = interpreter.sdk
    val libraryFlow = isInterpreterLibraryFlowEnabled()
    // An interpreter that the registry already holds is registered: adding its SDK again would add it twice.
    val held = if (libraryFlow) PythonInterpreterSdks.getInstance(project).sdkOf(interpreter.key) === sdk else PythonSdkTable.pythonSdks().any { it === sdk }
    if (held) return awaitInterpreter(sdk)
    val data = sdk.sdkAdditionalData as PythonSdkAdditionalData
    val homePath = sdk.homePath ?: error("$interpreter has an SDK without a home path")
    val registered = registerMutex.withLock {
      // The library flow has no table to adopt from: the store holds one SDK for each key, and the key decides.
      if (!libraryFlow && data !is PyRemoteSdkAdditionalDataMarker) {
        val adoptable = PythonSdkTable.pythonSdks().filter { it !== sdk && canAdopt(it.ownerDirectory()) }
        findSdkToAdopt(Path.of(homePath), adoptable) { pythonSdkType().suggestSdkName(null, homePath) }?.let { adopted ->
          edtWriteAction {
            val modificator = adopted.sdkModificator
            modificator.sdkAdditionalData = data
            modificator.commitChanges()
          }
          interpreter.sdk = adopted
          return awaitInterpreter(adopted)
        }
      }
      addSdk(sdk)
    }
    try {
      pythonSdkType().setupSdkPaths(sdk)
    }
    catch (e: Throwable) {
      // Do not leave a broken SDK in the table.
      withContext(NonCancellable) { removeSdk(sdk) }
      throw e
    }
    return registered
  }

  /**
   * Adds a prebuilt mock [sdk] to [pyProject] for a test. A mock SDK has no real interpreter, so [registerPythonInterpreter]
   * cannot create it. It associates [sdk] with [pyProject]. Remove it with [removePythonInterpreter].
   */
  @TestOnly
  suspend fun addMockPythonInterpreter(pyProject: PyProject, sdk: Sdk): PythonInterpreter =
    addMockPythonInterpreter(pyProject.baseDir, sdk)

  /**
   * Adds a prebuilt mock [sdk] owned by [ownerDir] for a test, see [PythonInterpreter.ownerDir]. A test that has no
   * [PyProject] of its own passes the base directory of the project, so it gets no extra module. Remove it with
   * [removeTestInterpreter].
   */
  @TestOnly
  suspend fun addMockPythonInterpreter(ownerDir: Path, sdk: Sdk): PythonInterpreter {
    (sdk.sdkAdditionalData as? PythonSdkAdditionalData)?.associatedModulePath = ownerDir.toString()
    val interpreter = addSdk(sdk)
    // The legacy flow keeps the SDK in the SDK table, which outlives the project. It goes with the project, as an SDK of
    // the interpreter library flow does.
    if (!isInterpreterLibraryFlowEnabled()) {
      Disposer.register(PythonInterpreterSdks.getInstance(project)) {
        WriteAction.run<RuntimeException> {
          if (ProjectJdkTable.getInstance().allJdks.any { it === sdk }) ProjectJdkTable.getInstance().removeJdk(sdk)
        }
      }
    }
    return interpreter
  }

  /**
   * Holds [sdk] itself, owned by [ownerDir], for a test that makes its SDK itself, see `Module.setPythonSdkForTests`.
   * The test keeps the [Sdk] object it made and edits its roots, so the registry holds that object and does not build
   * one. Returns the key of the interpreter, or `null` outside the interpreter library flow, or for an SDK that names no
   * interpreter.
   */
  @TestOnly
  suspend fun putTestSdk(sdk: Sdk, ownerDir: Path): PythonInterpreterKey? {
    if (!isInterpreterLibraryFlowEnabled()) return null
    edtWriteAction {
      val modificator = sdk.sdkModificator
      (modificator.sdkAdditionalData as? PythonSdkAdditionalData)?.associatedModulePath = ownerDir.toString()
      modificator.commitChanges()
    }
    val key = sdk.pythonInterpreterResultAsync().successOrNull?.key ?: return null
    holdSdk(key, sdk)
    awaitInterpreter(sdk)
    return key
  }

  /**
   * Adds the SDK of [interpreter] to the SDK table for an old test that has no [PyProject], such as a JUnit4 test that
   * sets the SDK on its module through the platform. Production code registers only through
   * `PyProject.setPythonInterpreter`. Remove it with [removeTestInterpreter].
   */
  @TestOnly
  suspend fun addTestInterpreter(interpreter: PythonInterpreter): PythonInterpreter = addSdk(interpreter.sdk)

  /** Removes an [interpreter] that [addTestInterpreter] added. Returns when the registry no longer holds it. */
  @TestOnly
  suspend fun removeTestInterpreter(interpreter: PythonInterpreter) {
    removeSdk(interpreter.sdk)
  }

  /** Removes [interpreter] from [pyProject]. Returns when the interpreters of [pyProject] no longer hold it. */
  suspend fun removePythonInterpreter(@Suppress("UNUSED_PARAMETER") pyProject: PyProject, interpreter: PythonInterpreter) {
    removeSdk(interpreter.sdk)
  }

  /**
   * Adds [sdk] to the SDK table and returns its interpreter. The table event recomputes the list, and this function
   * returns when the list holds the interpreter, so a caller that reads the list next finds it.
   *
   * The name check and the add are one write action, so two parallel calls cannot add two SDKs with one name.
   */
  private suspend fun addSdk(sdk: Sdk): PythonInterpreter {
    if (isInterpreterLibraryFlowEnabled()) {
      // The registry built [sdk] with an owner and a home path, so it names an interpreter.
      val key = checkNotNull(sdk.pythonInterpreterResultAsync().successOrNull) { "$sdk names no interpreter" }.key
      holdSdk(key, sdk)
      return awaitInterpreter(sdk)
    }
    edtWriteAction {
      makeSureNameIsUnique(sdk)
      ProjectJdkTable.getInstance().addJdk(sdk)
    }
    return awaitInterpreter(sdk)
  }

  /**
   * Holds [sdk] as the SDK of [key] in [PythonInterpreterSdks]. The SDK takes the name of the interpreter library of
   * [key], see [PyInterpreterLibraryName]. That name is unique in the project, so no other SDK of the project has it.
   */
  private suspend fun holdSdk(key: PythonInterpreterKey, sdk: Sdk) {
    val name = project.basePath?.let { PyInterpreterLibraryName.of(Path.of(it), key).toString() }
    if (name != null && sdk.name != name) {
      edtWriteAction {
        val modificator = sdk.sdkModificator
        modificator.name = name
        // A caller can set the data in place, such as the owner of a mock SDK. The modificator copies the data of the
        // last commit, so it takes the data that the SDK holds now, or the commit drops that change.
        modificator.sdkAdditionalData = sdk.sdkAdditionalData
        modificator.commitChanges()
      }
    }
    PythonInterpreterSdks.getInstance(project).put(key, sdk)
  }

  /** The interpreter of [sdk], when the list holds it. */
  private suspend fun awaitInterpreter(sdk: Sdk): PythonInterpreter =
    tableFlow.mapNotNull { list -> list.firstOrNull { it.isFor(sdk) } }.first()

  /**
   * Removes [sdk] from the SDK table. Returns when the list no longer holds its interpreter.
   *
   * Takes an SDK, not an interpreter: the settings list a broken SDK too, one that has no ref, so no interpreter, and the
   * user can remove it.
   */
  suspend fun removeSdk(sdk: Sdk) {
    if (isInterpreterLibraryFlowEnabled()) {
      val sdks = PythonInterpreterSdks.getInstance(project)
      sdks.keyOf(sdk)?.let(sdks::remove)
      awaitRemoved(sdk)
      // Disposed only when the list no longer holds it, so no reader of the list meets a disposed SDK.
      edtWriteAction { sdks.dispose(sdk) }
      return
    }
    edtWriteAction { ProjectJdkTable.getInstance().removeJdk(sdk) }
    awaitRemoved(sdk)
  }

  /**
   * Waits until the list no longer holds the interpreter of [sdk]. A list that still holds it after [REMOVE_TIMEOUT] is
   * a bug, so the wait logs an error and returns, and the caller does not hang.
   */
  private suspend fun awaitRemoved(sdk: Sdk) {
    withTimeoutOrNull(REMOVE_TIMEOUT) { tableFlow.first { list -> list.none { it.isFor(sdk) } } }
    ?: thisLogger().error("The interpreter list still holds ${sdk.name} after $REMOVE_TIMEOUT")
  }

  /**
   * Every Python SDK that has a ref. A broken SDK, one without a home path or a target configuration, has none: no
   * [PyProject] can address it, so it is not an interpreter. The Settings page that lists the SDK table still shows it.
   */
  private suspend fun compute(): List<PythonInterpreter> {
    if (isInterpreterLibraryFlowEnabled()) {
      // The store holds the record of each interpreter, and the SDK is built from it. The key names the interpreter, so
      // nothing is adopted, and no change of the SDK data moves it.
      val store = PythonInterpreterRecords.getInstance(project)
      val sdks = PythonInterpreterSdks.getInstance(project)
      return store.keys().mapNotNull { key ->
        val sdk = sdks.sdkOf(key) ?: buildSdk(key, store.recordOf(key) ?: return@mapNotNull null) ?: return@mapNotNull null
        sdk.pythonInterpreterOfKey(key).successOrNull
      }
    }
    adoptOwnerlessModuleSdks()
    return PythonSdkTable.pythonSdks().mapNotNull { it.pythonInterpreterResultAsync().successOrNull }
  }

  /**
   * The SDK of [key], built from the ref of [key] and from [record], and held by [PythonInterpreterSdks]. `null` when
   * the PyProject that owns [key] is not there yet, or when the ref names no environment any more: the record stays,
   * so a later recompute builds the SDK, and a missing environment that comes back keeps the paths of the user.
   */
  private suspend fun buildSdk(key: PythonInterpreterKey, record: PythonInterpreterRecords.StoredInterpreter): Sdk? {
    val pyProject = project.getPyProjects().firstOrNull { it.baseDir == key.ownerDir } ?: return null
    val fileSystem = key.ref.fileSystem(project) ?: return null
    val (homePath, data) = fileSystem.sdkHomeAndData(pyProject, key.ref).successOrNull ?: return null
    data.associatedModulePath = key.ownerDir.toString()
    applyRecord(data, record)
    val sdk = newSdk(homePath, data, suggestedName = null, knownVersion = record.version)
    applySdkFields(sdk, record)
    holdSdk(key, sdk)
    return sdk
  }

  /**
   * Gives an owner to each SDK without one that exactly one [PyProject] of this project uses: that [PyProject]. An older
   * build kept such SDKs, for example a system Python added in the settings. The SDK then stays the interpreter of that
   * [PyProject]. An SDK that several [PyProject]s use stays without an owner, so no [PyProject] gets the interpreter of
   * another one.
   */
  private suspend fun adoptOwnerlessModuleSdks() {
    val users = mutableMapOf<Sdk, MutableList<PyProject>>()
    for (pyProject in project.getPyProjects()) {
      val sdk = pyProject.residesOnModule.readPythonSdkForConfiguration() ?: continue
      if (sdk.sdkAdditionalData !is PythonSdkAdditionalData || sdk.ownerDirectory() != null) continue
      users.getOrPut(sdk) { mutableListOf() } += pyProject
    }
    for ((sdk, pyProjects) in users) {
      val owner = pyProjects.singleOrNull() ?: continue
      registerMutex.withLock {
        edtWriteAction {
          val modificator = sdk.sdkModificator
          (modificator.sdkAdditionalData as PythonSdkAdditionalData).associatedModulePath = owner.baseDir.toString()
          modificator.commitChanges()
        }
      }
    }
  }

  /**
   * An event for each change of the data of an interpreter, see [PythonInterpreterDetails.changes], and for each module
   * change: the SDK of a stored record is built once the PyProject that owns it is there, see [buildSdk].
   *
   * In the legacy flow the data is the SDK table. A commit of an SDK can change what the interpreter is: the target of a
   * target interpreter, or the owner that [adoptOwnerlessModuleSdks] writes. A recompute after that owner write finds no
   * SDK without an owner, so it ends there.
   */
  private fun sdkTableChanges(): Flow<Unit> =
    merge(PythonInterpreterDetails.getInstance(project).changes, moduleChanges())

  private fun moduleChanges(): Flow<Unit> =
    project.workspaceModel.eventLog.filter { it.getChanges(ModuleEntity::class.java).isNotEmpty() }.map { }

  /**
   * The interpreter of [sdk], whichever [PyProject] it belongs to. Waits for the first computation.
   *
   * A bridge for a caller that the platform hands an [Sdk] and no [PyProject], such as an editor or a run
   * configuration. Move it to the interpreter of its [PyProject].
   */
  @ApiStatus.Obsolete
  suspend fun interpreterOf(sdk: Sdk): PythonInterpreter? = tableFlow.first().firstOrNull { it.isFor(sdk) }

  /** [interpreterOf] without the wait. `null` also before the first computation. */
  @ApiStatus.Obsolete
  fun interpreterOfIfReady(sdk: Sdk): PythonInterpreter? = state.value?.interpreters?.firstOrNull { it.isFor(sdk) }

  /**
   * The interpreter that [ref] names in the Python project at [baseDir], or `null`. Waits for the first computation.
   *
   * A ref means something only in the Python project that owns the interpreter, so it is looked up among the
   * interpreters whose owner directory is [baseDir]. For now it finds only an interpreter that is already registered:
   * the storage still holds the SDK of each interpreter. A module of the legacy flow names an SDK instead, see
   * [findByModuleSdkName].
   */
  suspend fun resolve(baseDir: Path, ref: PyInterpreterRef): PythonInterpreter? = resolve(PythonInterpreterKey(baseDir, ref))

  /** [resolve] for the owner directory and the ref that [key] holds. */
  suspend fun resolve(key: PythonInterpreterKey): PythonInterpreter? = tableFlow.first().firstOrNull { it.key == key }

  /** [resolve] without the wait: `null` also before the first computation. For code that cannot suspend. */
  fun resolveIfReady(key: PythonInterpreterKey): PythonInterpreter? = state.value?.interpreters?.firstOrNull { it.key == key }

  /** The key of each interpreter the registry holds, each time they change. See [resolve]. */
  val interpreterKeysFlow: Flow<Set<PythonInterpreterKey>>
    get() = tableFlow.map { list -> list.mapTo(mutableSetOf()) { it.key } }.distinctUntilChanged()

  /**
   * The interpreter whose SDK has the name [sdkName] that a module stores, or `null`. Waits for the first computation.
   * Only the legacy flow names an interpreter by its SDK name, see [isInterpreterLibraryFlowEnabled].
   */
  @ApiStatus.Obsolete
  suspend fun findByModuleSdkName(sdkName: String): PythonInterpreter? = tableFlow.first().firstOrNull { it.sdk.name == sdkName }


  /**
   * The key of the interpreter for the SDK named [sdkName], for the PyProject at [ownerDir], or `null` when there is no
   * such Python SDK. Only the migration of a module of an older build calls it, in the interpreter library flow, see
   * [isInterpreterLibraryFlowEnabled].
   *
   * The import saves the record of the SDK, see [PythonInterpreterRecords], and the registry builds the SDK of the
   * interpreter from its ref and that record. The key keeps the owner that the SDK names, see [tableSdkKeyOf]. The SDK
   * of the table stays as it is, so an older build that opens the project still finds it.
   *
   * The import does not wait for the interpreter. The registry cannot build it when its environment is gone, and the
   * record then stays until the environment comes back.
   */
  @ApiStatus.Obsolete
  fun importTableSdk(sdkName: String, ownerDir: Path): PythonInterpreterKey? {
    val (key, tableSdk) = tableSdkKeyOf(sdkName, ownerDir) ?: return null
    val store = PythonInterpreterRecords.getInstance(project)
    // An earlier import of the same SDK already stored it.
    if (store.recordOf(key) == null) store.put(key, recordOf(key, tableSdk))
    return key
  }

  /**
   * The URLs of the paths that the legacy flow transferred to a module from the SDK of the SDK table named [sdkName]: the
   * source roots that an `.iml` stores for it. Empty when the table has no such SDK. Only the migration of a module of an
   * older build calls it, which removes these roots: the interpreter library flow derives them, see
   * [PythonInterpreterDetails.sourceRoots].
   */
  @ApiStatus.Obsolete
  fun transferredPathsOfTableSdk(sdkName: String): Set<String> {
    val data = PythonSdkTable.tableFindByName(sdkName)?.sdkAdditionalData as? PythonSdkAdditionalData ?: return emptySet()
    return data.pathsToTransfer.mapTo(LinkedHashSet()) { it.url }
  }

  /**
   * The interpreter for the SDK named [sdkName] that a run configuration of an older build stores, without the wait:
   * `null` also before the first computation. In the interpreter library flow the SDK of the table gives the key, see
   * [tableSdkKeyOf], and [ownerDir] is the owner when the SDK names none. In the legacy flow the SDK name answers.
   */
  @ApiStatus.Obsolete
  fun findByLegacySdkNameIfReady(sdkName: String, ownerDir: Path?): PythonInterpreter? {
    if (!isInterpreterLibraryFlowEnabled()) return state.value?.interpreters?.firstOrNull { it.sdk.name == sdkName }
    val key = tableSdkKeyOf(sdkName, ownerDir ?: return null)?.first ?: return null
    return resolveIfReady(key)
  }

  /**
   * The key of the Python SDK of the SDK table named [sdkName], and that SDK, or `null` when the table has no such SDK
   * or the SDK names no interpreter. The key keeps the owner that the SDK names, and takes [ownerDir] only when the SDK
   * names none: PyProjects can share one interpreter, such as the members of a workspace.
   */
  private fun tableSdkKeyOf(sdkName: String, ownerDir: Path): Pair<PythonInterpreterKey, Sdk>? {
    val tableSdk = PythonSdkTable.tableFindByName(sdkName)?.takeIf { PythonSdkUtil.isPythonSdk(it, false) } ?: return null
    val data = tableSdk.sdkAdditionalData as? PythonSdkAdditionalData ?: return null
    val homePath = pathEnvRef(tableSdk) ?: return null
    val owner = tableSdk.ownerDirectory() ?: ownerDir
    val mode = when (data) {
      is PyTargetAwareAdditionalData -> PyInterpreterRef.Mode.Target(data.targetEnvironmentConfiguration?.uuid ?: return null)
      else -> PyInterpreterRef.Mode.Native
    }
    val binary = when (mode) {
      is PyInterpreterRef.Mode.Target -> PathHolder.Target(homePath)
      PyInterpreterRef.Mode.Native -> PathHolder.Eel(homePath.toNioPathOrNull() ?: return null)
    }
    return PythonInterpreterKey(owner, interpreterRefOf(binary, data, mode)) to tableSdk
  }

  /** [interpreters] without the wait: empty also before the first computation. For code that cannot suspend. */
  fun interpretersIfReady(pyProject: PyProject): List<PythonInterpreter> = state.value?.interpreters.orEmpty().belongingTo(pyProject)

  /**
   * Calls [listener] on the EDT each time the interpreters of [pyProject] change, until [parentDisposable] is disposed.
   * It does not call it for the current list. For a Java caller that cannot collect [interpretersFlow].
   */
  fun addChangeListener(pyProject: PyProject, parentDisposable: Disposable, listener: Runnable) {
    val job = scope.launch(Dispatchers.EDT) {
      interpretersFlow(pyProject).drop(1).collect { listener.run() }
    }
    Disposer.register(parentDisposable) { job.cancel() }
  }

  /**
   * Calls [listener] on the EDT each time an interpreter of any [PyProject] is added or removed, until
   * [parentDisposable] is disposed. For a Java form that lists the interpreters of a module it can change.
   */
  fun addTableChangeListener(parentDisposable: Disposable, listener: Runnable) {
    val job = scope.launch(Dispatchers.EDT) {
      tableFlow.drop(1).collect { listener.run() }
    }
    Disposer.register(parentDisposable) { job.cancel() }
  }

  companion object {
    private val REMOVE_TIMEOUT = 30.seconds

    fun getInstance(project: Project): PythonInterpreterRegistry = project.service()
  }
}

/**
 * The SDK table as the Python code reads it. It is the only reader of the table outside [PythonInterpreterRegistry], and it
 * lives here so that every table access is in this file. The table is application-wide, so this needs no project.
 *
 * It holds every Python SDK, broken ones included: a list in the settings shows a broken SDK, so the user can remove it.
 * A caller that needs an interpreter reads [PythonInterpreterRegistry] instead.
 */
@ApiStatus.Internal
object PythonSdkTable {
  /**
   * The SDKs this build uses. In the interpreter library flow they are the SDKs of the open projects, see
   * [PythonInterpreterSdks], and the SDK table is not read. Otherwise they are the SDKs of the table.
   */
  private fun usedSdks(): List<Sdk> =
    if (isInterpreterLibraryFlowEnabled()) {
      ProjectManager.getInstance().openProjects.flatMap { it.serviceIfCreated<PythonInterpreterSdks>()?.sdks().orEmpty() }
    }
    else {
      tableSdks()
    }

  /** Every Python SDK this build uses, see [PythonSdkUtil.isPythonSdk]. */
  @JvmStatic
  fun pythonSdks(): List<Sdk> = usedSdks().filter { PythonSdkUtil.isPythonSdk(it, false) }

  /**
   * Every Python SDK this build uses, also a remote one in the free tier, which [pythonSdks] leaves out. For code that
   * handles the stored data of every SDK, such as its credentials or its user data.
   */
  @JvmStatic
  fun pythonSdksOfEveryTier(): List<Sdk> = usedSdks().filter { PythonSdkUtil.isPythonSdk(it, true) }

  /** Every SDK this build uses, of every type, for a filter that picks the Python ones itself. */
  @JvmStatic
  fun allSdks(): List<Sdk> = usedSdks()

  /**
   * [allSdks] for [project]. In the interpreter library flow they are the SDKs of [project], also when it is not open,
   * such as a project of a test.
   */
  @JvmStatic
  fun allSdks(project: Project): List<Sdk> =
    if (isInterpreterLibraryFlowEnabled()) PythonInterpreterSdks.getInstance(project).sdks() else tableSdks()

  /** The SDK named [name] that this build uses, of any type. */
  @JvmStatic
  fun findByName(name: String): Sdk? = usedSdks().firstOrNull { it.name == name }

  /** Every SDK of the SDK table. Only the registry reads the table itself: the legacy flow, and the migration. */
  internal fun tableSdks(): List<Sdk> = ProjectJdkTable.getInstance().allJdks.asList()

  /** The SDK of the SDK table named [name]. Only the registry reads the table itself. */
  internal fun tableFindByName(name: String): Sdk? = ProjectJdkTable.getInstance().findJdk(name)
}

/** The interpreters that belong to [pyProject]. See [PythonInterpreterRegistry]. */
private fun List<PythonInterpreter>.belongingTo(pyProject: PyProject): List<PythonInterpreter> {
  val baseDir = pyProject.baseDir
  return filter { it.sdk.ownerDirectory() == baseDir }
}

/** The base directory of the [PyProject] this SDK belongs to, see [projectDirOf]. */
/**
 * The owner directory and the ref of an interpreter: what names it in a module, see [PythonInterpreterRegistry.resolve].
 * A ref means something only in the directory of its owner, so two owners can have one ref for two interpreters.
 */
@ApiStatus.Internal
data class PythonInterpreterKey(val ownerDir: Path, val ref: PyInterpreterRef)

/** The key of this interpreter, see [PythonInterpreterKey]. */
internal val PythonInterpreter.key: PythonInterpreterKey get() = PythonInterpreterKey(ownerDir, ref)

private fun Sdk.ownerDirectory(): Path? = (sdkAdditionalData as? PythonSdkAdditionalData)?.let { projectDirOf(it) }

/** See [PythonInterpreterRegistry.interpreterOf]. Move the caller to the interpreter of its `PyProject`. */
@ApiStatus.Internal
@ApiStatus.Obsolete
suspend fun Project.findPythonInterpreter(sdk: Sdk): PythonInterpreter? =
  PythonInterpreterRegistry.getInstance(this).interpreterOf(sdk)

/** See [PythonInterpreterRegistry.interpreterOfIfReady]. Move the caller to the interpreter of its `PyProject`. */
@ApiStatus.Internal
@ApiStatus.Obsolete
fun Project.findPythonInterpreterIfReady(sdk: Sdk): PythonInterpreter? =
  PythonInterpreterRegistry.getInstance(this).interpreterOfIfReady(sdk)

/**
 * The usual SDK that already stands for the interpreter at [pythonBinaryPath], or `null` when none does. A usual SDK is
 * one whose data carries no [PyRemoteSdkAdditionalDataMarker].
 *
 * The IDE keeps one usual SDK for each interpreter, not one for each interpreter and tool. A `.venv` that poetry
 * created, and uv then adopted, is one environment. A second SDK for it reads as a second interpreter in every list the
 * IDE shows.
 *
 * Older builds also keyed on the tool, so one path can carry several SDKs already. The SDK named [preferredName] wins,
 * because that is the name a new SDK gets here. If no SDK has that name, the first one wins, so the answer is stable.
 * [preferredName] is read only when there is more than one candidate, because it reads the file system.
 */
private fun findSdkToAdopt(pythonBinaryPath: PythonBinary, existingSdks: List<Sdk>, preferredName: () -> String): Sdk? {
  // Compared as paths, not as strings, because `c:\windows` and `c:/Windows` name one file.
  val candidates = existingSdks.filter {
    it.sdkAdditionalData !is PyRemoteSdkAdditionalDataMarker && it.pythonBinaryPath().successOrNull == pythonBinaryPath
  }
  if (candidates.size < 2) return candidates.firstOrNull()
  val name = preferredName()
  return candidates.firstOrNull { it.name == name } ?: candidates.first()
}

/**
 * Renames [sdk] until no other SDK of the table has its name. The table can hold [sdk] itself, which keeps its name.
 */
@RequiresWriteLock
private fun makeSureNameIsUnique(sdk: Sdk) {
  val name = sdk.name
  var i = 1
  while (PythonSdkTable.tableFindByName(sdk.name).let { it != null && it !== sdk }) {
    val m = sdk.sdkModificator
    m.name = "$name@$i"
    i += 1
    m.commitChanges()
  }
}


/**
 * Removes [sdk] for a test that holds only the SDK, such as the SDK a flow created. In the interpreter library flow it
 * leaves each open project that holds it. Otherwise it leaves the SDK table.
 */
@TestOnly
suspend fun removeTestSdkFromOpenProjects(sdk: Sdk) {
  if (!isInterpreterLibraryFlowEnabled()) {
    edtWriteAction { ProjectJdkTable.getInstance().removeJdk(sdk) }
    return
  }
  for (project in ProjectManager.getInstance().openProjects) {
    val sdks = project.serviceIfCreated<PythonInterpreterSdks>() ?: continue
    if (sdks.keyOf(sdk) != null) PythonInterpreterRegistry.getInstance(project).removeSdk(sdk)
  }
}

/**
 * The interpreter of [sdk] in the registry of any open project, or `null`. It does not wait. For a holder of run
 * parameters that has no project, such as the console settings, which still gets an SDK from older code.
 */
@ApiStatus.Internal
@ApiStatus.Obsolete
fun findPythonInterpreterInOpenProjectsIfReady(sdk: Sdk): PythonInterpreter? =
  ProjectManager.getInstance().openProjects.firstNotNullOfOrNull { it.findPythonInterpreterIfReady(sdk) }
