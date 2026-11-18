// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend.impl

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.projectRoots.impl.createSdkForEnvironment
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.python.community.execService.python.validatePythonAndGetInfo
import com.intellij.python.sdk.backend.PyInterpreterLibraryName
import com.intellij.python.sdk.backend.PyInterpreterOwnerDir
import com.intellij.python.sdk.backend.PySdkBundle
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterDetails
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.python.sdk.backend.PythonInterpreterPathsSetup
import com.intellij.python.sdk.backend.PythonInterpreterRecords
import com.intellij.python.sdk.backend.PythonInterpreterRoots
import com.intellij.python.sdk.backend.PythonInterpreterSdks
import com.intellij.python.sdk.backend.applyRecord
import com.intellij.python.sdk.backend.detectPythonEnvironment
import com.intellij.python.sdk.backend.fileSystem
import com.intellij.python.sdk.backend.interpreterOwnerProjectDir
import com.intellij.python.sdk.backend.interpreterRefOf
import com.intellij.python.sdk.backend.isFor
import com.intellij.python.sdk.backend.isInterpreterLibraryFlowEnabled
import com.intellij.python.sdk.backend.key
import com.intellij.python.sdk.backend.pathEnvRef
import com.intellij.python.sdk.backend.projectDirOf
import com.intellij.python.sdk.backend.pythonInterpreterOfKey
import com.intellij.python.sdk.backend.pythonInterpreterResultAsync
import com.intellij.python.sdk.backend.recordOf
import com.intellij.python.sdk.common.PyInterpreterRef
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.getPyProjectsWithoutJpsSync
import com.jetbrains.python.project.project
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.PyRemoteSdkAdditionalDataMarker
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.add.v2.EelOrTarget
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.FileSystemFactory
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.filter.filterAssignablePythonSdks
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.pythonBinaryPath
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
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
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * The machinery behind [com.intellij.python.sdk.backend.PythonInterpreterRegistry]: the list of interpreters, and the
 * SDK of each one. The registry is the API, and this store keeps the state, computes the list, and builds, holds, adds
 * and removes the SDKs. The registry creates it with its own coroutine scope, and the other code of this module reaches
 * it through [com.intellij.python.sdk.backend.PythonInterpreterRegistry.store].
 *
 * In the interpreter library flow, see [isInterpreterLibraryFlowEnabled], the [PythonInterpreterRecords] of the project
 * saves a record for each interpreter, and the store builds its SDK in memory from the ref and that record, see
 * [PythonInterpreterSdks]. In the legacy flow the storage is the SDK table of the application.
 */
internal class PythonInterpreterStore(val project: Project, scope: CoroutineScope) {
  /**
   * One computed list, with one interpreter for each SDK. It compares by reference, so each recompute emits. It is a
   * list because [PythonInterpreter] compares by its ref, and two SDKs can have one ref. A set keeps only one of them, so
   * a lookup by the other SDK finds nothing. [recordsVersion] is the change count of [PythonInterpreterRecords] that the
   * computation read, so a caller that changed the store can wait for a list that holds the change.
   */
  private class Computed(val interpreters: List<PythonInterpreter>, val recordsVersion: Long)

  private val state = MutableStateFlow<Computed?>(null)

  /**
   * Makes the search for an SDK to adopt and the add one step. Without it, two parallel adds for one Python both find
   * nothing to adopt, and both add an SDK.
   */
  private val registerMutex = Mutex()

  init {
    // A change made outside the registry, for example by the platform Settings, reaches the list through this listener.
    scope.launch {
      sdkTableChanges()
        .onStart { emit(Unit) }
        .conflate()
        .collect {
          val recordsVersion = PythonInterpreterRecords.getInstance(project).versions.value
          val interpreters = compute()
          state.value = Computed(interpreters, recordsVersion)
          // The roots of an interpreter that the registry no longer holds are of no use, see PythonInterpreterDetails.
          project.serviceIfCreated<PythonInterpreterRoots>()?.retain(interpreters.mapTo(mutableSetOf()) { it.key })
        }
    }
  }

  /** Every interpreter, each time the list changes. The first value comes after the first computation. */
  val allFlow: Flow<List<PythonInterpreter>> = state.filterNotNull().map { it.interpreters }

  /** The last list, or `null` before the first computation. */
  val allIfReady: List<PythonInterpreter>?
    get() = state.value?.interpreters

  /** The interpreters that [pyProject] owns, each time they change. The first value comes after the first computation. */
  fun interpretersFlow(pyProject: PyProject): Flow<List<PythonInterpreter>> =
    allFlow.map { it.belongingTo(pyProject) }.distinctUntilChanged()

  /** The interpreters that [pyProject] owns in the last list, empty before the first computation. */
  fun interpretersIfReady(pyProject: PyProject): List<PythonInterpreter> = allIfReady.orEmpty().belongingTo(pyProject)

  /** For tests: the last computation of the list against the store, for the report of a wait that does not end. */
  @TestOnly
  fun describeForTests(): String {
    val computed = state.value
    val records = PythonInterpreterRecords.getInstance(project)
    return "registry: computed at records version ${computed?.recordsVersion} of ${records.versions.value}, " +
           "interpreters ${computed?.interpreters?.map { it.key }}, records ${records.keys()}"
  }

  /** See [com.intellij.python.sdk.backend.PythonInterpreterRegistry.build]. */
  suspend fun build(pyProject: PyProject, ref: PyInterpreterRef): PyResult<PythonInterpreter> {
    interpretersFlow(pyProject).first().firstOrNull { it.ref == ref }?.let { return PyResult.success(it) }
    val fileSystem = ref.fileSystem(project)
                     ?: return PyResult.localizedError(PySdkBundle.message("evolution.error.env.not.found", ref.toString()))
    val (homePath, data) = fileSystem.sdkHomeAndData(pyProject, ref).getOr { return it }
    data.associatedModulePath = pyProject.baseDir.toString()
    return build(homePath, data, suggestedName = null)
  }

  /**
   * The interpreter of the Python binary at [homePath] with the data [data], before it is registered: its SDK is in no
   * table and no store yet. A failure when [data] names no interpreter.
   */
  private suspend fun build(homePath: String, data: PythonSdkAdditionalData, suggestedName: String?): PyResult<PythonInterpreter> =
    newSdk(homePath, data, suggestedName, knownVersion = null).pythonInterpreterResultAsync()

  /**
   * A new interpreter of [pythonBinary] with the data [data], for the [PyProject] at [ownerDir]. It is not registered.
   * A flow that has just created an environment calls it through `createSdk`, see `com.jetbrains.python.sdk`.
   *
   * [ownerDir] is the base directory of the owner. The owner does not have to exist yet. For example, the new project
   * wizard passes the directory of the project that it creates.
   *
   * Only the legacy flow uses [suggestedName]. In the interpreter library flow, the interpreter takes the name of its
   * library when it is registered. A failure when [pythonBinary] is not found, or when [data] names no interpreter.
   */
  suspend fun build(
    ownerDir: Path,
    pythonBinary: PathHolder,
    data: PythonSdkAdditionalData,
    suggestedName: String?,
  ): PyResult<PythonInterpreter> {
    val homePath = when (pythonBinary) {
      // The SDK home path is the path of the VFS file, so it matches what the SDK table holds for the same binary.
      is PathHolder.Eel -> withContext(Dispatchers.IO) { VirtualFileManager.getInstance().refreshAndFindFileByNioPath(pythonBinary.path) }?.path
                           ?: return PyResult.localizedError(PySdkBundle.message("evolution.error.env.not.found", pythonBinary.path.toString()))
      is PathHolder.Target -> pythonBinary.pathString
    }
    data.associatedModulePath = ownerDir.toString()
    return build(homePath, data, suggestedName)
  }

  /**
   * A Python SDK at [homePath] with the data [data] that is in no table. Its changes need no write action: the first
   * computation builds SDKs, and a caller under a modal progress waits for it, while a write action on the EDT waits for
   * the modal progress (PY-93821). The legacy flow adds the SDK to the table later, in a write action, see [addSdk].
   *
   * Only the legacy flow needs a name that no other SDK of the table has. In the interpreter library flow the SDK takes
   * the name of its interpreter library when the store holds it, see [holdSdk].
   */
  private suspend fun newSdk(homePath: String, data: PythonSdkAdditionalData, suggestedName: String?, knownVersion: String?): Sdk {
    val sdkType = pythonSdkType()
    val name = when {
      isInterpreterLibraryFlowEnabled() -> suggestedName ?: sdkType.suggestSdkName(null, homePath)
      suggestedName == null -> SdkConfigurationUtil.createUniqueSdkName(sdkType, homePath, PythonSdkTable.tableSdks())
      else -> SdkConfigurationUtil.createUniqueSdkName(suggestedName, PythonSdkTable.tableSdks())
    }
    @Suppress("SETUP_SDK_DIRECTLY") // The store is the only place that creates a Python SDK.
    val sdk = ProjectJdkTable.getInstance().createSdkForEnvironment(project, name, sdkType, homePath)
    val parsedHomePath = Path.of(homePath)
    val fileSystem = FileSystemFactory.createForPythonSdkAdditionalData(data, parsedHomePath)
    // A native interpreter is detected from the layout of its environment, which starts no process. The environment
    // records its version, except for a system Python, so the version is probed only when the environment states none,
    // and for a target, which has no environment to detect.
    val environment = when (fileSystem.eelOrTarget) {
      is EelOrTarget.IsEel -> withContext(Dispatchers.IO) { parsedHomePath.detectPythonEnvironment() }
      is EelOrTarget.IsTarget -> null
    }
    val version = knownVersion
                  ?: environment?.successOrNull?.version?.let { "Python $it" }
                  ?: probeVersionString(fileSystem, parsedHomePath)
    sdk.sdkModificator.apply {
      sdkAdditionalData = data
      this.homePath = homePath
      versionString = version
    }.applyChangesWithoutWriteAction()
    // The interpreter of the SDK reads this detection, so the environment is detected once.
    environment?.let(sdk::cachePythonEnvironmentResult)
    return sdk
  }

  /**
   * The version of the interpreter at [homePath] on [fileSystem], or `null` when [fileSystem] cannot tell it. An Eel
   * machine reads the version from [homePath]. A target runs the interpreter, and the probe keeps the modality of the
   * caller. A target interpreter has no detected environment, so this version is the only one it states before it is
   * registered.
   */
  private suspend fun probeVersionString(fileSystem: FileSystem<*>, homePath: Path): String? = withContext(Dispatchers.IO) {
    when (fileSystem.eelOrTarget) {
      is EelOrTarget.IsEel -> pythonSdkType().getVersionString(homePath.toString())
      is EelOrTarget.IsTarget -> when (val version = fileSystem.pythonLanguageLevel(homePath.toString())) {
        is Result.Success -> version.result.toPythonVersion()
        is Result.Failure -> {
          // An unreachable target says nothing about the interpreter, so the SDK keeps no version.
          thisLogger().info("Cannot read the version of python at \"${homePath}\": ${version.error.message}")
          null
        }
      }
    }
  }

  private suspend fun <P : PathHolder> FileSystem<P>.pythonLanguageLevel(pythonBinary: String): PyResult<LanguageLevel> {
    val path = parsePath(pythonBinary).getOr { return it }
    return getBinaryToExec(path).validatePythonAndGetInfo().mapSuccess { it.languageLevel }
  }

  private fun pythonSdkType(): SdkType = SdkType.findByName(PyNames.PYTHON_SDK_ID_NAME) ?: error("The Python SDK type is not registered")

  /** Whether the list holds [interpreter]: its owner registered it. Waits for the first computation. */
  suspend fun isRegistered(interpreter: PythonInterpreter): Boolean = allFlow.first().any { it.isFor(interpreter.sdk) }

  /**
   * Registers [interpreter], see [com.intellij.python.sdk.backend.PythonInterpreterRegistry.register].
   * The owner keeps an interpreter it already has for the ref of [interpreter]. Otherwise a local SDK for the same
   * binary is adopted, when it has the same owner or none.
   */
  suspend fun register(interpreter: PythonInterpreter): PythonInterpreter {
    if (isRegistered(interpreter)) return interpreter
    allFlow.first().firstOrNull { it.ownerDir == interpreter.ownerDir && it.ref == interpreter.ref }?.let { return it }
    return registerInterpreter(interpreter) { it == null || it == interpreter.ownerDir }
  }

  /**
   * Adds the SDK of [interpreter] to the table, or adopts a local SDK whose owner directory passes [canAdopt], see
   * [ownerDirectory]. An adopted SDK takes the data of [interpreter], and [interpreter] then holds it. When the setup of
   * the paths fails, the SDK is removed again.
   */
  private suspend fun registerInterpreter(interpreter: PythonInterpreter, canAdopt: (Path?) -> Boolean): PythonInterpreter {
    val sdk = interpreter.sdk
    val libraryFlow = isInterpreterLibraryFlowEnabled()
    // An interpreter that the store already holds is registered: adding its SDK again would add it twice.
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
      PythonInterpreterPathsSetup.getInstance(project).setUpPaths(registered)
    }
    catch (e: Throwable) {
      // Do not leave a broken SDK in the table.
      withContext(NonCancellable) { removeSdk(sdk) }
      throw e
    }
    return registered
  }

  /**
   * Adds [sdk] to the SDK table and returns its interpreter. The table event recomputes the list, and this function
   * returns when the list holds the interpreter, so a caller that reads the list next finds it.
   *
   * The name check and the add are one write action, so two parallel calls cannot add two SDKs with one name.
   */
  private suspend fun addSdk(sdk: Sdk): PythonInterpreter {
    if (isInterpreterLibraryFlowEnabled()) {
      // The store built [sdk] with an owner and a home path, so it names an interpreter.
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
  private suspend fun holdSdk(key: PythonInterpreterKey, sdk: Sdk, fromRecord: Boolean = false) {
    val name = project.interpreterOwnerProjectDir?.let { PyInterpreterLibraryName.of(it, key).toString() }
    if (name != null && sdk.name != name) {
      val rename = { modificator: SdkModificator ->
        modificator.name = name
        // A caller can set the data in place, such as the owner of a mock SDK. The modificator copies the data of the
        // last commit, so it takes the data that the SDK holds now, or the commit drops that change.
        modificator.sdkAdditionalData = sdk.sdkAdditionalData
      }
      // A held SDK is in no table, so its change needs no write action: the first computation holds SDKs, see [newSdk].
      // A test can hold an SDK of the table, see `putTestSdk`, and a change of a table SDK is a write of the model.
      if (PythonSdkTable.tableSdks().none { it === sdk }) sdk.sdkModificator.apply(rename).applyChangesWithoutWriteAction()
      else edtWriteAction { sdk.sdkModificator.apply(rename).commitChanges() }
    }
    PythonInterpreterSdks.getInstance(project).put(key, sdk, fromRecord)
  }

  /** The interpreter of [sdk], when the list holds it. */
  private suspend fun awaitInterpreter(sdk: Sdk): PythonInterpreter =
    allFlow.mapNotNull { list -> list.firstOrNull { it.isFor(sdk) } }.first()

  /** Removes [interpreter]. Returns when the list no longer holds it. */
  suspend fun remove(interpreter: PythonInterpreter) {
    removeSdk(interpreter.sdk)
  }

  /**
   * Adds [interpreter] to the list without the setup of a registration, for a test that has no [PyProject], see
   * [com.intellij.python.sdk.backend.addTestInterpreter]. Returns the interpreter the list then holds.
   */
  @TestOnly
  suspend fun addForTests(interpreter: PythonInterpreter): PythonInterpreter = addSdk(interpreter.sdk)

  /**
   * Adds [sdk], a mock SDK or an SDK that a test made itself, to the list, see
   * [com.intellij.python.sdk.backend.addMockPythonInterpreter]. Returns its interpreter.
   */
  @TestOnly
  suspend fun addSdkForTests(sdk: Sdk): PythonInterpreter = addSdk(sdk)

  /**
   * Holds [sdk] itself as the SDK of [key], for a test that keeps the [Sdk] object it made and edits its roots, see
   * [com.intellij.python.sdk.backend.putTestSdk]. Returns when the list holds its interpreter.
   */
  @TestOnly
  suspend fun holdSdkForTests(key: PythonInterpreterKey, sdk: Sdk) {
    holdSdk(key, sdk)
    awaitInterpreter(sdk)
  }

  /** Removes [sdk], an SDK that a test holds itself. Returns when the list no longer holds its interpreter. */
  @TestOnly
  suspend fun removeSdkForTests(sdk: Sdk) {
    removeSdk(sdk)
  }

  /** Removes [sdk] from the SDK table. Returns when the list no longer holds its interpreter. */
  private suspend fun removeSdk(sdk: Sdk) {
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
    withTimeoutOrNull(REMOVE_TIMEOUT) { allFlow.first { list -> list.none { it.isFor(sdk) } } }
    ?: thisLogger().error("The interpreter list still holds ${sdk.name} after $REMOVE_TIMEOUT")
  }

  /**
   * Every Python SDK that has a ref. A broken SDK, one without a home path or a target configuration, has none: no
   * [PyProject] can address it, so it is not an interpreter.
   */
  private suspend fun compute(): List<PythonInterpreter> {
    if (isInterpreterLibraryFlowEnabled()) {
      // The store holds the record of each interpreter, and the SDK is built from it. The key names the interpreter, so
      // nothing is adopted, and no change of the SDK data moves it.
      val records = PythonInterpreterRecords.getInstance(project)
      val sdks = PythonInterpreterSdks.getInstance(project)
      return records.keys().mapNotNull { key ->
        val sdk = sdks.sdkOf(key) ?: buildSdk(key, records.recordOf(key) ?: return@mapNotNull null) ?: return@mapNotNull null
        sdk.pythonInterpreterOfKey(key).successOrNull
      }
    }
    // The computation writes nothing, so a caller under a modal progress can wait for it (PY-93821). An SDK of an older
    // build gets its owner from PythonSdkAdditionalDataMigrationActivity, and the commit of that owner recomputes the list.
    return PythonSdkTable.pythonSdks().mapNotNull { it.pythonInterpreterResultAsync().successOrNull }
  }

  /**
   * The SDK of [key], built from the ref of [key] and from [record], and held by [PythonInterpreterSdks]. `null` when
   * the PyProject that owns [key] is not there yet, or when the ref names no environment any more: the record stays,
   * so a later recompute builds the SDK, and a missing environment that comes back keeps the paths of the user.
   */
  private suspend fun buildSdk(key: PythonInterpreterKey, record: PythonInterpreterRecords.StoredInterpreter): Sdk? {
    // Not `getPyProjects`: the model waits for the first computation, and a modal dialog holds the JPS model back. A
    // module change recomputes, so the synchronization with the JPS model corrects this read.
    val pyProject = project.getPyProjectsWithoutJpsSync().firstOrNull { it.baseDir == key.ownerDir } ?: return null
    val fileSystem = key.ref.fileSystem(project) ?: return null
    val (homePath, data) = fileSystem.sdkHomeAndData(pyProject, key.ref).successOrNull ?: return null
    data.associatedModulePath = key.ownerDir.toString()
    applyRecord(data, record)
    val sdk = newSdk(homePath, data, suggestedName = null, knownVersion = record.version)
    holdSdk(key, sdk, fromRecord = true)
    return sdk
  }

  /**
   * An event for each change of the data of an interpreter, see [PythonInterpreterDetails.changes], and for each module
   * change: the SDK of a stored record is built once the PyProject that owns it is there, see [buildSdk].
   *
   * In the legacy flow the data is the SDK table. A commit of an SDK can change what the interpreter is: the target of a
   * target interpreter, or the owner that PythonSdkAdditionalDataMigrationActivity writes for an SDK of an older build.
   */
  private fun sdkTableChanges(): Flow<Unit> =
    merge(PythonInterpreterDetails.getInstance(project).changes.map { }, moduleChanges(), recordChanges())

  /**
   * The changes of the records in the interpreter library flow. The details report them as events, and the loop of the
   * list subscribes to those only after its first computation. A record put in between, such as by the first set of an
   * interpreter, would be lost, and [findEnsuringRecord] would wait for it forever. The records are a state, so the first
   * value covers that gap.
   */
  private fun recordChanges(): Flow<Unit> =
    if (isInterpreterLibraryFlowEnabled()) PythonInterpreterRecords.getInstance(project).changes else emptyFlow()

  private fun moduleChanges(): Flow<Unit> =
    project.workspaceModel.eventLog.filter { it.getChanges(ModuleEntity::class.java).isNotEmpty() }.map { }

  /** The interpreter of [sdk], whichever [PyProject] it belongs to. Waits for the first computation. */
  suspend fun findBySdk(sdk: Sdk): PythonInterpreter? = allFlow.first().firstOrNull { it.isFor(sdk) }

  /** [findBySdk] without the wait. `null` also before the first computation. */
  fun findBySdkIfReady(sdk: Sdk): PythonInterpreter? = allIfReady?.firstOrNull { it.isFor(sdk) }

  /** See [com.intellij.python.sdk.backend.PythonInterpreterRegistry.ensureRecord]. */
  fun ensureRecord(key: PythonInterpreterKey): Boolean {
    if (!isInterpreterLibraryFlowEnabled()) return false
    val projectDir = project.interpreterOwnerProjectDir ?: return false
    val record = PythonInterpreterRecords.StoredInterpreter().apply {
      // In the form that `recordOf` writes, relative to the project, so the record follows the project when it moves.
      ownerDir = PyInterpreterOwnerDir.of(projectDir, key.ownerDir).toString()
      ref = key.ref.toString()
      uuid = UUID.randomUUID().toString()
    }
    return PythonInterpreterRecords.getInstance(project).putIfAbsent(key, record)
  }

  /**
   * See [com.intellij.python.sdk.backend.PythonInterpreterRegistry.findEnsuringRecord]. A computation that does not read
   * the record after [RESOLVE_TIMEOUT] is a bug. The wait then logs an error and answers from the last computation, so a
   * caller that holds a lock does not hang.
   */
  suspend fun findEnsuringRecord(key: PythonInterpreterKey): PythonInterpreter? {
    ensureRecord(key)
    val needed = PythonInterpreterRecords.getInstance(project).versions.value
    val computed = withTimeoutOrNull(RESOLVE_TIMEOUT) { state.filterNotNull().first { it.recordsVersion >= needed } }
    if (computed == null) {
      thisLogger().error("No computation read the records at version $needed after $RESOLVE_TIMEOUT, the last one read ${state.value?.recordsVersion}")
    }
    return (computed ?: state.value)?.interpreters?.firstOrNull { it.key == key }
  }

  /** The interpreter whose SDK has the name [sdkName] that a module stores, or `null`. Waits for the first computation. */
  suspend fun findBySdkName(sdkName: String): PythonInterpreter? = allFlow.first().firstOrNull { it.sdk.name == sdkName }

  /** See [com.jetbrains.python.sdk.legacy.importTableSdk]. */
  fun importTableSdk(sdkName: String, ownerDir: Path): PythonInterpreterKey? {
    val (key, tableSdk) = tableSdkKeyOf(sdkName, ownerDir) ?: return null
    val projectDir = project.interpreterOwnerProjectDir ?: return null
    val records = PythonInterpreterRecords.getInstance(project)
    // An earlier import of the same SDK already stored it.
    if (records.recordOf(key) == null) records.put(key, recordOf(projectDir, key, tableSdk))
    return key
  }

  /** See [com.jetbrains.python.sdk.legacy.transferredPathsOfTableSdk]. */
  fun transferredPathsOfTableSdk(sdkName: String): Set<String> {
    val data = PythonSdkTable.tableFindByName(sdkName)?.sdkAdditionalData as? PythonSdkAdditionalData ?: return emptySet()
    return data.pathsToTransfer.mapTo(LinkedHashSet()) { it.url }
  }

  /** See [com.jetbrains.python.sdk.legacy.findBySdkNameIfReady]. */
  fun findBySdkNameIfReady(sdkName: String, ownerDir: Path?): PythonInterpreter? {
    if (!isInterpreterLibraryFlowEnabled()) return allIfReady?.firstOrNull { it.sdk.name == sdkName }
    val key = tableSdkKeyOf(sdkName, ownerDir ?: return null)?.first ?: return null
    return allIfReady?.firstOrNull { it.key == key }
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

  /**
   * See [com.intellij.python.sdk.backend.PythonInterpreterRegistry.allInterpreters]: the interpreters of the SDKs of this
   * project that it can use. An SDK that names no interpreter is not listed.
   */
  suspend fun allInterpreters(): List<PythonInterpreter> =
    ModuleOrProject.ProjectOnly(project).filterAssignablePythonSdks(projectSdks()).mapNotNull { it.pythonInterpreterResultAsync().successOrNull }

  /**
   * Every SDK of this project, also a broken one. In the interpreter library flow they are the SDKs of this project.
   * Otherwise they are the SDKs of the SDK table.
   */
  fun projectSdks(): List<Sdk> = PythonSdkTable.allSdks(project)

  companion object {
    private val REMOVE_TIMEOUT = 30.seconds
    private val RESOLVE_TIMEOUT = 30.seconds

    /** Every SDK that this build uses, of every type and tier, see [PythonSdkTable.allSdks]. */
    fun usedSdks(): List<Sdk> = PythonSdkTable.allSdks()
  }
}

/** The interpreters that belong to [pyProject]: their owner directory is its base directory. */
private fun List<PythonInterpreter>.belongingTo(pyProject: PyProject): List<PythonInterpreter> {
  val baseDir = pyProject.baseDir
  return filter { it.sdk.ownerDirectory() == baseDir }
}

/**
 * The SDK table as the store reads it. Only this file reads the table.
 *
 * It holds every Python SDK, broken ones included: a list in the settings shows a broken SDK, so the user can remove it.
 */
private object PythonSdkTable {
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
  fun pythonSdks(): List<Sdk> = usedSdks().filter { PythonSdkUtil.isPythonSdk(it, false) }

  /** Every SDK this build uses, of every type, for a filter that picks the Python ones itself. */
  fun allSdks(): List<Sdk> = usedSdks()

  /**
   * [allSdks] for [project]. In the interpreter library flow they are the SDKs of [project], also when it is not open,
   * such as a project of a test.
   */
  fun allSdks(project: Project): List<Sdk> =
    if (isInterpreterLibraryFlowEnabled()) PythonInterpreterSdks.getInstance(project).sdks() else tableSdks()

  /** Every SDK of the SDK table: the legacy flow, and the migration. */
  fun tableSdks(): List<Sdk> = ProjectJdkTable.getInstance().allJdks.asList()

  /** The SDK of the SDK table named [name]. */
  fun tableFindByName(name: String): Sdk? = ProjectJdkTable.getInstance().findJdk(name)
}

/** The base directory of the [PyProject] this SDK belongs to, see [projectDirOf]. */
private fun Sdk.ownerDirectory(): Path? = (sdkAdditionalData as? PythonSdkAdditionalData)?.let { projectDirOf(it) }

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

/** Renames [sdk] until no other SDK of the table has its name. The table can hold [sdk] itself, which keeps its name. */
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
