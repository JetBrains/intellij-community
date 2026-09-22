// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.flow.drop
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.Disposable
import com.intellij.python.sdk.common.PyInterpreterRef
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.sdk.PyRemoteSdkAdditionalDataMarker
import com.jetbrains.python.sdk.pythonBinaryPath
import kotlinx.coroutines.flow.distinctUntilChanged
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.PyProject.Companion.getPyProjects
import com.jetbrains.python.sdk.findPythonSdk
import com.jetbrains.python.project.project
import kotlinx.coroutines.Dispatchers
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.intellij.openapi.vfs.VirtualFileManager
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.nio.file.Path
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

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
 * For now the storage is the SDK table of the application. An SDK belongs to the [PyProject] whose base directory is its
 * associated path. An SDK with no associated path belongs to the [PyProject] whose base directory is its working
 * directory. Any other SDK has no owner: this registry does not show it, and adopts it for the first [PyProject] that
 * registers an interpreter for the same binary.
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
        .collect { state.value = Computed(compute()) }
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
  private suspend fun newSdk(homePath: String, data: PythonSdkAdditionalData, suggestedName: String?): Sdk {
    val sdkType = pythonSdkType()
    @Suppress("SETUP_SDK_DIRECTLY") // The registry is the only place that creates a Python SDK.
    val sdk = SdkConfigurationUtil.createSdk(PythonSdkUtil.getAllSdks(), homePath, sdkType, data, suggestedName)
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
    val data = sdk.sdkAdditionalData as PythonSdkAdditionalData
    val homePath = sdk.homePath ?: error("$interpreter has an SDK without a home path")
    val registered = registerMutex.withLock {
      if (data !is PyRemoteSdkAdditionalDataMarker) {
        val adoptable = PythonSdkUtil.getAllSdks().filter { it !== sdk && canAdopt(it.ownerDirectory()) }
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
    return addSdk(sdk)
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
    edtWriteAction {
      makeSureNameIsUnique(sdk)
      ProjectJdkTable.getInstance().addJdk(sdk)
    }
    return awaitInterpreter(sdk)
  }

  /** The interpreter of [sdk], when the list holds it. */
  private suspend fun awaitInterpreter(sdk: Sdk): PythonInterpreter =
    tableFlow.mapNotNull { list -> list.firstOrNull { it.isFor(sdk) } }.first()

  /** Removes [sdk] from the SDK table. Returns when the list no longer holds its interpreter. */
  private suspend fun removeSdk(sdk: Sdk) {
    edtWriteAction { ProjectJdkTable.getInstance().removeJdk(sdk) }
    tableFlow.first { list -> list.none { it.isFor(sdk) } }
  }

  /**
   * Every Python SDK that has a ref. A broken SDK, one without a home path or a target configuration, has none: no
   * [PyProject] can address it, so it is not an interpreter. The Settings page that lists the SDK table still shows it.
   */
  private suspend fun compute(): List<PythonInterpreter> {
    adoptOwnerlessModuleSdks()
    return PythonSdkUtil.getAllSdks().mapNotNull { it.pythonInterpreterResultAsync().successOrNull }
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
      val sdk = pyProject.residesOnModule.findPythonSdk() ?: continue
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
   * The interpreter whose SDK has the name [sdkName] that a module stores, or `null`. Waits for the first computation.
   * For the model that reads the module storage. Remove it when a module stores a [PyInterpreterRef].
   */
  @ApiStatus.Obsolete
  suspend fun findByModuleSdkName(sdkName: String): PythonInterpreter? = tableFlow.first().firstOrNull { it.sdk.name == sdkName }

  /** The SDK names of the table, each time they change. See [findByModuleSdkName]. */
  @get:ApiStatus.Obsolete
  val moduleSdkNamesFlow: Flow<Set<String>>
    get() = tableFlow.map { list -> list.mapTo(mutableSetOf()) { it.sdk.name } }.distinctUntilChanged()

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
    fun getInstance(project: Project): PythonInterpreterRegistry = project.service()
  }
}

/** The interpreters that belong to [pyProject]. See [PythonInterpreterRegistry]. */
private fun List<PythonInterpreter>.belongingTo(pyProject: PyProject): List<PythonInterpreter> {
  val baseDir = pyProject.baseDir
  return filter { it.sdk.ownerDirectory() == baseDir }
}

/** The base directory of the [PyProject] this SDK belongs to, see [projectDirOf]. */
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

@RequiresWriteLock
private fun makeSureNameIsUnique(sdk: Sdk) {
  val name = sdk.name
  var i = 1
  while (ProjectJdkTable.getInstance().findJdk(sdk.name) != null) {
    val m = sdk.sdkModificator
    m.name = "$name@$i"
    i += 1
    m.commitChanges()
  }
}

