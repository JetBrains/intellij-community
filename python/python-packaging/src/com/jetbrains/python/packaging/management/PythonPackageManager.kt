// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION", "removal")

package com.jetbrains.python.packaging.management

import com.intellij.execution.ExecutionException
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.isModalAwareContext
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.eel.EelApi
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiFile
import com.intellij.python.pyproject.PyDependencyGroup
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.sdk.backend.NativePythonInterpreter
import com.intellij.python.sdk.backend.PythonEnvironmentKey
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterDetails
import com.intellij.python.sdk.backend.TargetPythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.sdk.backend.isReadOnly
import com.intellij.python.sdk.backend.readOnlyErrorMessage
import com.intellij.python.sdk.backend.refreshPaths
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.onFailure
import com.jetbrains.python.packaging.InstalledPackage
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PythonInstalledPackagesCache
import com.jetbrains.python.packaging.common.PyDependencyGroupName
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.common.PythonPackageMetadata
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.common.loadInstalledPackagesMetadata
import com.jetbrains.python.packaging.packageRequirements.DependencyTreeProvider
import com.jetbrains.python.packaging.packageRequirements.FlatPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageStructureNode
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.requirements.PyDependenciesFile
import com.jetbrains.python.requirements.PyDependenciesFileProvider
import com.jetbrains.python.sdk.associatedModuleDir
import com.jetbrains.python.sdk.pySdkAdditionalData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue
import org.jetbrains.annotations.Nls
import java.net.URI
import java.nio.file.Path
import java.util.SequencedMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException


/**
 * Represents a Python package manager for a specific Python SDK. Encapsulate main operations with package managers
 * @see com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI to execute commands with UI handlers
 */
@ApiStatus.Experimental
abstract class PythonPackageManager @ApiStatus.Internal constructor(
  val project: Project,
  /** The interpreter whose environment this manager manages. It is detected, so its environment is known. */
  @ApiStatus.Internal
  val interpreter: PythonInterpreter,
) : Disposable {
  /** The SDK of [interpreter], for the subclasses that still call an SDK API. */
  @Suppress("DEPRECATION")
  protected val sdk: Sdk get() = interpreter.getSdkAPI()

  /**
   * Whether this manager has an explicit list of top-level dependencies (e.g. from pyproject.toml).
   * When true, only packages from [listDeclaredPackagesCached] are treated as "declared" in the UI,
   * and the rest are shown as transitive.
   * When false (default), all installed packages are considered declared.
   */
  @ApiStatus.Internal
  open val installedPackagesIncludeTransitive: Boolean = false

  /**
   * Whether this manager is still the one for its [sdk]. [PythonPackageManager.forPythonInterpreter] replaces a manager that says no.
   * The default is yes. A manager whose class the stored data of the SDK selects answers from that data.
   */
  @ApiStatus.Internal
  open fun matchesSdk(): Boolean = true

  @get:ApiStatus.Internal
  protected abstract val dependenciesFilesRelativePaths: List<Path>

  /**
   * Lifetime of every coroutine this manager starts. A child of the packaging scope of the project.
   */
  @ApiStatus.Internal
  protected val managerScope: CoroutineScope = PyPackageCoroutine.getScope(project).childScope("PythonPackageManager for ${sdk.name}")

  private val isInited = AtomicBoolean(false)

  /** Whether the installed packages came from the cache, and no list checked the outdated packages since. */
  private val outdatedPending = AtomicBoolean(false)
  private val packageReloadMutex = Mutex()

  private val dependencyCache = DependencyCache()

  private val initializationJob = managerScope.launch(
    NON_INTERACTIVE_ROOT_TRACE_CONTEXT + ModalityState.any().asContextElement(),
    start = CoroutineStart.LAZY,
  ) {
    initInstalledPackages()
  }

  /**
   * Cancels [this] together with [managerScope]. Use it for a job that this manager did not start itself.
   */
  @ApiStatus.Internal
  protected fun <T : Job> T.cancelWithManager(): T = also { job ->
    managerScope.coroutineContext.job.invokeOnCompletion { job.cancel() }
  }

  @ApiStatus.Internal
  override fun dispose() {
    // Not inherited from Disposable.Default: the scope's parent is the project, so without this it would outlive
    // the manager and keep running work for an SDK that is already gone.
    managerScope.cancel()
  }


  @ApiStatus.Internal
  @Volatile
  protected var installedPackages: List<PythonPackage>? = null

  @ApiStatus.Internal
  @Volatile
  protected var outdatedPackages: Map<String, PythonOutdatedPackage> = emptyMap()

  @ApiStatus.Internal
  @Volatile
  private var installedPackagesMetadata: Map<PyPackageName, PythonPackageMetadata> = emptyMap()

  @Volatile
  private var installedPackagesMetadataLoad: Deferred<Map<PyPackageName, PythonPackageMetadata>> = CompletableDeferred(emptyMap())

  /** The last check of the outdated packages, see [awaitOutdatedPackages]. */
  @Volatile
  private var outdatedPackagesLoad: Job = Job().apply { complete() }

  @get:ApiStatus.Internal
  open val treeProvider: DependencyTreeProvider? = null

  @get:ApiStatus.Internal
  abstract val repositoryManager: PythonRepositoryManager

  @get:ApiStatus.Internal
  open val dependenciesExporter: DependenciesExporter? = null

  @ApiStatus.Internal
  suspend fun syncLocked(): PyResult<List<PythonPackage>> {
    if (interpreter.isReadOnly) {
      return PyResult.localizedError(interpreter.readOnlyErrorMessage)
    }
    syncLockedCommand().getOr { return it }
    return reloadPackages()
  }

  /**
   * Installs the packages of [installRequest] and reloads the installed packages. The VFS guarantee of [reloadPackages]
   * holds only in a modal-aware context.
   */
  @ApiStatus.Internal
  suspend fun installPackage(
    installRequest: PythonPackageInstallRequest,
    options: List<String> = emptyList(),
    module: Module? = null,
    dependencyGroup: PyDependencyGroup? = null,
  ): PyResult<List<PythonPackage>> {
    if (interpreter.isReadOnly) {
      return PyResult.localizedError(interpreter.readOnlyErrorMessage)
    }
    waitForInit()
    installPackageCommand(installRequest, options, module, dependencyGroup).getOr { return it }

    return reloadPackages()
  }

  @ApiStatus.Internal
  suspend fun updatePackages(vararg packages: PythonRepositoryPackageSpecification): PyResult<List<PythonPackage>> {
    if (interpreter.isReadOnly) {
      return PyResult.localizedError(interpreter.readOnlyErrorMessage)
    }
    waitForInit()
    updatePackageCommand(*packages).getOr { return it }

    return reloadPackages()
  }

  @ApiStatus.Internal
  suspend fun uninstallPackage(
    vararg packages: String,
    workspaceMember: PyWorkspaceMember? = null,
    dependencyGroup: PyDependencyGroup? = null,
  ): PyResult<List<PythonPackage>> {
    if (interpreter.isReadOnly) {
      return PyResult.localizedError(interpreter.readOnlyErrorMessage)
    }
    if (packages.isEmpty()) {
      return PyResult.success(listInstalledPackagesSnapshot())
    }

    waitForInit()

    val normalizedPackagesNames = packages.map { PyPackageName.normalizePackageName(it) }
    uninstallPackageCommand(*normalizedPackagesNames.toTypedArray(),
                            workspaceMember = workspaceMember,
                            dependencyGroup = dependencyGroup).getOr { return it }
    return reloadPackages()
  }

  /**
   * Reloads the installed packages and refreshes the interpreter paths in the VFS. The call waits for the refresh only
   * in a modal-aware context. Under [ModalityState.any] the refresh runs async, so the VFS can still hold the old paths
   * when the call returns.
   */
  @ApiStatus.Internal
  open suspend fun reloadPackages(): PyResult<List<PythonPackage>> {
    treeProvider?.invalidateCache()
    // [dependencyCache] is keyed by the modification stamp of each dependency file, so a sync that
    // writes only a lock file does not change that key. Without this the manager keeps a failure
    // that the sync just fixed (PY-90174).
    dependencyCache.invalidate()
    return loadPackagesImpl(isInit = false)
  }

  private suspend fun loadPackagesImpl(isInit: Boolean): PyResult<List<PythonPackage>> {
    val packages = packageReloadMutex.withLock {
      // Cancellable: external process call.
      val packages = loadPackagesCommand().getOr { return it }

      val changed = packages != installedPackages
      if (!changed) {
        // A list from the cache did not check the outdated packages, see [publishInstalledPackages]. The first list of the
        // process does it, also when it gives the same packages.
        if (outdatedPending.compareAndSet(true, false)) startOutdatedPackagesLoad()
        return PyResult.success(listInstalledPackagesSnapshot())
      }

      // Transactional commit: state mutation + listener notification
      // must complete atomically even if the caller is cancelled.
      withContext(NonCancellable) {
        storeInstalledPackages(packages)
        publishInstalledPackages(packages)
      }
      packages
    }

    // Outside the mutex: in a headless run the SDK update is synchronous, and the update it queues reloads the packages.
    if (!isInit) {
      withContext(NonCancellable) {
        // The suspending refresh does not accept ModalityState.any(): the refresh then runs async, without a wait.
        refreshPaths(project, interpreter, async = !isModalAwareContext())
      }
    }

    return PyResult.success(packages)
  }

  /**
   * Makes [packages] the installed packages of this manager and tells the listeners, see [PACKAGE_MANAGEMENT_TOPIC].
   * Always call under [packageReloadMutex].
   *
   * A list that the process gave starts the metadata and the outdated packages at once. A list from the cache, see
   * [cachedInstalledPackages], starts none of them: the metadata loads when a reader awaits it, see
   * [awaitInstalledPackagesMetadata], and the outdated packages with the next list, also one with the same packages. So
   * a start runs no process.
   */
  private suspend fun publishInstalledPackages(packages: List<PythonPackage>, fromCache: Boolean = false) {
    withContext(NonCancellable) {
      this@PythonPackageManager.installedPackages = packages
      // Replaced before `packagesChanged`, so a listener that reads the metadata awaits the new package list.
      installedPackagesMetadataLoad = managerScope.async(
        NON_INTERACTIVE_ROOT_TRACE_CONTEXT,
        start = if (fromCache) CoroutineStart.LAZY else CoroutineStart.DEFAULT,
      ) {
        // A load that a newer reload replaced must not overwrite the newer snapshot.
        loadMetadata(packages).also { if (installedPackages === packages) installedPackagesMetadata = it }
      }

      project.messageBus.syncPublisher(PACKAGE_MANAGEMENT_TOPIC).packagesChanged(interpreter)
      ApplicationManager.getApplication().messageBus.syncPublisher(PyPackageManager.PACKAGE_MANAGER_TOPIC).packagesRefreshed(sdk)

      outdatedPending.set(fromCache)
      if (!fromCache) startOutdatedPackagesLoad()
    }
  }

  /**
   * The installed packages of the last list of the environment by a manager of this class, when its fingerprint did not
   * change since, see [PythonInstalledPackagesCache]. `null` for an environment that the cache cannot tell, such as one
   * on a target.
   */
  private suspend fun cachedInstalledPackages(): List<PythonPackage>? {
    val [environment, fingerprint] = environmentFingerprint() ?: return null
    return PythonInstalledPackagesCache.getInstance().packagesOf(environment, javaClass.name, fingerprint)?.map { it.toPythonPackage() }
  }

  /**
   * Keeps [packages] as the installed packages of the environment, for the next start, see [cachedInstalledPackages]. A
   * list with a package of a subclass, such as a conda package, has data that the cache does not keep, so the cache drops
   * the environment instead.
   */
  private suspend fun storeInstalledPackages(packages: List<PythonPackage>) {
    val [environment, fingerprint] = environmentFingerprint() ?: return
    val cache = PythonInstalledPackagesCache.getInstance()
    if (packages.any { it.javaClass != PythonPackage::class.java }) cache.drop(environment, javaClass.name)
    else cache.store(environment, javaClass.name, fingerprint, packages.map { it.toInstalledPackage() })
  }

  /** The environment of [interpreter] and its fingerprint now, see [PythonInstalledPackagesCache.fingerprintOf]. */
  private suspend fun environmentFingerprint(): Pair<PythonEnvironmentKey, String>? {
    // The cache reads the site-packages directories on the disk of the IDE, so a target environment has no fingerprint.
    when (interpreter) {
      is NativePythonInterpreter -> Unit
      is TargetPythonInterpreter -> return null
    }
    val environment = PythonEnvironmentKey.of(sdk) ?: return null
    val sysPath = PythonInterpreterDetails.getInstance(project).facts(interpreter).sysPath
    val fingerprint = withContext(Dispatchers.IO) { PythonInstalledPackagesCache.fingerprintOf(sysPath) } ?: return null
    return environment to fingerprint
  }


  @ApiStatus.Experimental
  suspend fun listInstalledPackages(): List<PythonPackage> {
    waitForInit()
    return listInstalledPackagesSnapshot()
  }

  @ApiStatus.Experimental
  fun listInstalledPackagesSnapshot(): List<PythonPackage> {
    return installedPackages ?: emptyList()
  }

  /**
   * The declared dependencies of the last load. `null` before the first load ends. The init loads them, and so does each
   * change of a dependency file, so a reader that must not wait, such as an inspection, reads this. A load that changes
   * them publishes [PythonPackageManagementListener.declaredPackagesChanged].
   */
  @ApiStatus.Experimental
  fun listDeclaredPackagesSnapshot(): List<PythonPackage>? = dependencyCache.snapshot.value

  /**
   * Whether [file] is one of the dependency files this manager currently tracks for the active
   * interpreter — i.e. it is present in the cached dependency-file tree (the root plus, e.g., uv
   * workspace members), not merely a file that happens to share a name. Non-blocking: reflects the
   * last cache refresh and is `false` until the manager has been initialized.
   */
  @ApiStatus.Internal
  fun tracksDependencyFile(file: PsiFile): Boolean {
    val virtualFile = file.originalFile.virtualFile ?: return false
    return dependencyCache.trackedFilesSnapshot().any { it.virtualFile == virtualFile }
  }

  @ApiStatus.Experimental
  suspend fun listOutdatedPackages(): Map<String, PythonOutdatedPackage> {
    waitForInit()
    return listOutdatedPackagesSnapshot()
  }


  @ApiStatus.Experimental
  fun listOutdatedPackagesSnapshot(): Map<String, PythonOutdatedPackage> {
    return outdatedPackages
  }

  /**
   * Returns Core Metadata (PEP 643) read from `<dist-info>/METADATA` for every package
   * installed in the active interpreter, keyed by PEP 503-normalized name. Lifecycle mirrors
   * `outdatedPackages`: `loadPackagesImpl` rebuilds the snapshot in the background each time it
   * observes a package-list change, in a single helper invocation.
   *
   * Does not wait for that rebuild, so right after a change the result can be empty or belong to
   * the previous package list. Use [awaitInstalledPackagesMetadata] when it must match [listInstalledPackages].
   */
  @ApiStatus.Internal
  suspend fun listInstalledPackagesMetadata(): Map<PyPackageName, PythonPackageMetadata> {
    waitForInit()
    return listInstalledPackagesMetadataSnapshot()
  }

  /**
   * Same as [listInstalledPackagesMetadata], but waits for the metadata load of the current
   * package list, so the result belongs to the same package list as [listInstalledPackages].
   */
  @ApiStatus.Internal
  suspend fun awaitInstalledPackagesMetadata(): Map<PyPackageName, PythonPackageMetadata> {
    waitForInit()
    // A load of a list from the cache starts here, see [publishInstalledPackages].
    return installedPackagesMetadataLoad.await()
  }

  @ApiStatus.Internal
  fun listInstalledPackagesMetadataSnapshot(): Map<PyPackageName, PythonPackageMetadata> = installedPackagesMetadata

  /** Starts a check of the outdated packages in the background, see [awaitOutdatedPackages]. */
  private fun startOutdatedPackagesLoad() {
    outdatedPackagesLoad = managerScope.launch(NON_INTERACTIVE_ROOT_TRACE_CONTEXT) { reloadOutdatedPackages() }
  }

  /** Suspends until the last check of the outdated packages that this manager started ends. */
  @ApiStatus.Internal
  suspend fun awaitOutdatedPackages() {
    outdatedPackagesLoad.join()
  }

  private suspend fun reloadOutdatedPackages() {
    if (listInstalledPackagesSnapshot().isEmpty()) {
      outdatedPackages = emptyMap()
      return
    }
    val loadedPackages = loadOutdatedPackagesCommand().onFailure {
      thisLogger().warn("Failed to load outdated packages $it")
    }.getOrNull() ?: emptyList()

    val packageMap = loadedPackages.associateBy { it.name }
    if (outdatedPackages == packageMap) return

    outdatedPackages = packageMap
    project.messageBus.syncPublisher(PACKAGE_MANAGEMENT_TOPIC).outdatedPackagesChanged(interpreter)
  }

  /**
   * Reads the helper's single-shot dump of every installed distribution's METADATA file. Skipped
   * silently when there are no installed packages so a fresh / mock SDK doesn't pay the helper-startup cost.
   */
  private suspend fun loadMetadata(packages: List<PythonPackage>): Map<PyPackageName, PythonPackageMetadata> {
    if (packages.isEmpty()) return emptyMap()

    return sdk.loadInstalledPackagesMetadata().onFailure {
      thisLogger().warn("Failed to load installed package metadata $it")
    }.getOrNull() ?: emptyMap()
  }

  @ApiStatus.Internal
  @CheckReturnValue
  protected abstract suspend fun syncLockedCommand(): PyResult<Unit>

  @ApiStatus.Internal
  open fun updateLockedAction(): (suspend () -> PyResult<Unit>)? = null

  /** The name of [updateLockedAction] in the packages view. `null` gives the generic name. */
  @ApiStatus.Internal
  open fun updateLockedActionName(): @Nls String? = null

  /**
   * @param module the target workspace member module for workspace-aware package managers (e.g., UV).
   *   When provided, the package is added as a dependency of this specific workspace member
   *   rather than the root project. Package managers that do not support workspaces ignore this parameter.
   */
  @ApiStatus.Internal
  @CheckReturnValue
  abstract suspend fun installPackageCommand(
    installRequest: PythonPackageInstallRequest,
    options: List<String>,
    module: Module? = null,
    dependencyGroup: PyDependencyGroup? = null,
  ): PyResult<Unit>

  @ApiStatus.Internal
  @CheckReturnValue
  open suspend fun installPackageDetachedCommand(
    installRequest: PythonPackageInstallRequest,
    options: List<String>,
  ): PyResult<Unit> = installPackageCommand(installRequest, options)

  @ApiStatus.Internal
  @CheckReturnValue
  protected abstract suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit>

  @ApiStatus.Internal
  @CheckReturnValue
  protected abstract suspend fun uninstallPackageCommand(
    vararg pythonPackages: String,
    workspaceMember: PyWorkspaceMember? = null,
    dependencyGroup: PyDependencyGroup? = null,
  ): PyResult<Unit>

  @ApiStatus.Internal
  protected abstract suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>>

  @ApiStatus.Internal
  protected abstract suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>>

  /**
   * Lists project top-level (declared) dependencies.
   * Returns null by default — this manager doesn't support dependency extraction.
   */
  @ApiStatus.Internal
  open suspend fun listDeclaredPackages(): PyResult<List<PythonPackage>>? = null

  /**
   * Extracts the complete package tree structure for the tool window.
   * Returns either a workspace member tree, a flat collection of packages,
   * or [FlatPackageStructureNode] if this manager doesn't distinguish declared from transitive.
   */
  @ApiStatus.Internal
  open suspend fun getPackageTree(): PackageStructureNode = FlatPackageStructureNode

  /**
   * Returns workspace support if this manager supports multi-project workspaces or dependency groups,
   * `null` otherwise.
   */
  @get:ApiStatus.Internal
  open val workspaceSupport: PythonWorkspaceSupport? get() = null

  /**
   * CLI tools exposed by this manager, used for command-mode completion and dispatch in the install dialog.
   */
  @ApiStatus.Internal
  open fun getCliSpecs(eelApi: EelApi): List<PythonManagerCliSpec> = emptyList()

  /**
   * Lists project top-level (declared) dependencies with caching based on dependency file modification time.
   * Returns cached result if dependency file hasn't changed since last call.
   *
   * @return null if this package manager doesn't support dependency listing,
   *         PyResult.Failure if listing is supported but failed (e.g., parsing error),
   *         PyResult.Success with the list of dependencies if listing succeeded.
   */
  @ApiStatus.Internal
  suspend fun listDeclaredPackagesCached(): PyResult<List<PythonPackage>>? = dependencyCache.awaitLatest()

  /**
   * Subclass extension point: returns the complete dependency files tree the manager currently
   * uses (existing files only, validated).
   *
   * Called from inside the cache on every read so it must be safe to invoke frequently.
   */
  @ApiStatus.Internal
  protected open suspend fun resolveDependencyFilesTree(): List<PyDependenciesFile> {
    return getRootDependenciesFile()?.let { listOf(it) } ?: emptyList()
  }


  @ApiStatus.Internal
  suspend fun getRootDependenciesFile(): PyDependenciesFile? {
    val requirementsPath = sdk.pySdkAdditionalData.requirementsPath
    val virtualFile = if (requirementsPath != null) {
      // An explicitly stored path wins over (and does not fall back to) the manager-specific defaults.
      VirtualFileManager.getInstance().findFileByNioPath(requirementsPath)
    }
    else {
      sdk.associatedModuleDir?.let { baseDir ->
        dependenciesFilesRelativePaths.firstNotNullOfOrNull { path ->
          baseDir.findFileByRelativePath(FileUtil.toSystemIndependentName(path.toString()))
        }
      }
    }
    return virtualFile?.let { PyDependenciesFileProvider.resolve(it) }
  }

  /**
   * Adds a dependency to the project's dependency declaration file.
   * Returns true if the dependency was successfully added, false if the operation
   * is not supported or failed.
   */
  @ApiStatus.Internal
  suspend fun addDependencyToFile(requirement: PyRequirement): Boolean {
    return getRootDependenciesFile() != null && addDependencyImpl(requirement)
  }

  /**
   * Implementation of adding a dependency to the file.
   * Only called when getDependencyFile() returns non-null.
   * @param requirement The requirement to add
   */
  @ApiStatus.Internal
  protected open suspend fun addDependencyImpl(requirement: PyRequirement): Boolean = false

  @ApiStatus.Internal
  suspend fun waitForInit() {
    initializationJob.join()
  }

  /** Starts the init of this manager, see [waitForInit], and does not wait for it. */
  @ApiStatus.Internal
  fun startInit() {
    initializationJob.start()
  }


  private suspend fun initInstalledPackages() {
    try {
      if (isInited.getAndSet(true)) return
      // An environment that did not change since its last list starts no process: the cache gives its packages.
      val cached = cachedInstalledPackages()
      if (cached != null) packageReloadMutex.withLock { publishInstalledPackages(cached, fromCache = true) }
      else loadPackagesImpl(isInit = true)
      // The first load of the declared packages, which also starts the watch of their files, see [DependencyCache]. The
      // init does not wait for it: the declared packages can need a process, such as `uv tree`, and the derived roots need
      // only the installed packages. A reader that must have them awaits [listDeclaredPackagesCached].
      managerScope.launch(NON_INTERACTIVE_ROOT_TRACE_CONTEXT) { loadDeclaredPackages() }
    }
    catch (t: CancellationException) {
      throw t
    }
    catch (t: ExecutionException) {
      thisLogger().warn("Failed to initialize PythonPackageManager for $sdk", t)
    }
  }

  /** Loads the declared packages into their snapshot, see [listDeclaredPackagesSnapshot]. */
  private suspend fun loadDeclaredPackages() {
    dependencyCache.awaitLatest()?.onFailure { thisLogger().warn("Failed to read the declared packages of $sdk: $it") }
  }

  /**
   * Caches [listDeclaredPackages] keyed by the `(file -> modification stamp)` map produced
   * by [resolveDependencyFilesTree]. Each [awaitLatest] reuses the entry on a Map.equals hit
   * and publishes a new one otherwise; [snapshot] mirrors the latest successful result for
   * non-suspend readers.
   */
  private inner class DependencyCache {
    /** Replaced whenever the file/stamp map differs from the previous one. */
    @Volatile
    private var entry: Entry? = null

    /**
     * Latest successfully computed declared-packages list (`null` until the current entry's
     * deferred completes — or for entries with no dependency files). Stale entry callbacks
     * don't write. The outer manager exposes this typed as [StateFlow] so external callers
     * can't mutate it.
     */
    val snapshot = MutableStateFlow<List<PythonPackage>?>(null)

    /** Serializes refreshes so two concurrent callers don't both spawn redundant computes. */
    private val refreshMutex = Mutex()

    /** The dependency files of the latest refresh. Outlives the [entry] that [invalidate] drops. */
    @Volatile
    private var trackedFiles: List<PyDependenciesFile> = emptyList()

    /** A value for each change of a tracked dependency file, see [watchDependencyFiles]. */
    private val fileChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
      // A reader only reads [snapshot]. A change of a dependency file starts the next load, so a reader, such as an
      // inspection during highlighting, never starts one. A burst of changes gives one load.
      managerScope.launch(NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
        fileChanges.conflate().collect { loadDeclaredPackages() }
      }
      watchDependencyFiles()
    }

    /** Asks for a load after each change of a tracked dependency file. A load that finds the same stamps runs nothing. */
    private fun watchDependencyFiles() {
      project.messageBus.connect(managerScope).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
        override fun after(events: List<VFileEvent>) {
          val tracked = trackedFiles.mapTo(HashSet()) { it.virtualFile }
          val relevant = events.any { it.file in tracked }
          if (relevant) fileChanges.tryEmit(Unit)
        }
      })
    }

    /**
     * Returns the cached entry on a Map.equals hit, otherwise builds and publishes a fresh
     * one. Map equality is order-insensitive — subclasses needn't return a stable order.
     *
     * The new entry is published into [entry] *before* attaching `invokeOnCompletion`, so
     * the pre-completed `CompletableDeferred(null)` we use for the empty-files case fires
     * its callback with `entry === newEntry` and can clear the snapshot.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    suspend fun ensureFreshEntry(): Entry = refreshMutex.withLock {
      val files = resolveDependencyFilesTree()
      val filesWithStamps = files.associateWithTo(LinkedHashMap()) { it.virtualFile.modificationStamp }

      val current = entry
      if (current?.files == filesWithStamps) return@withLock current

      val deferred: Deferred<PyResult<List<PythonPackage>>?> = if (filesWithStamps.isEmpty()) {
        CompletableDeferred(value = null)
      }
      else {
        managerScope.async(NON_INTERACTIVE_ROOT_TRACE_CONTEXT, start = CoroutineStart.LAZY) {
          listDeclaredPackages()
        }
      }

      val newEntry = Entry(filesWithStamps, deferred).also {
        this.entry = it
        this.trackedFiles = filesWithStamps.keys.toList()
      }

      deferred.invokeOnCompletion { cause ->
        // Stale completion (a newer entry replaced this one) — leave the snapshot alone.
        if (entry !== newEntry) return@invokeOnCompletion
        // A load that did not complete was cancelled with the manager or its project, so there is nothing to publish. A
        // load that fails completes with its failure.
        if (cause != null) return@invokeOnCompletion
        // StateFlow deduplicates by .equals — assigning the same list is a no-op.
        val declared = deferred.getCompleted()?.getOrNull()
        if (snapshot.value != declared) {
          snapshot.value = declared
          // The init does not wait for this read, so a reader that ran before it reads again. A coroutine publishes it: the
          // project can start its dispose before it cancels the manager scope, and the bus of a disposed project throws
          // `AlreadyDisposedException`. A coroutine ends quietly with it, and a completion handler reports it.
          managerScope.launch(NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
            project.messageBus.syncPublisher(PACKAGE_MANAGEMENT_TOPIC).declaredPackagesChanged(interpreter)
          }
        }
      }
      newEntry
    }

    /** Refreshes if needed and awaits the entry's deferred; `await()` starts the LAZY async on first call. */
    suspend fun awaitLatest(): PyResult<List<PythonPackage>>? = ensureFreshEntry().deferred.await()

    /** Forces the next [awaitLatest] to recompute, whatever the dependency files' stamps say. */
    fun invalidate() {
      entry = null
    }

    /**
     * Non-blocking view of the dependency files the latest refresh tracks (root plus, e.g., uv
     * workspace members). Empty until the cache has been seeded by [initInstalledPackages] or a
     * [listDeclaredPackagesCached] refresh. An [invalidate] keeps it, so the surfaces gated on
     * [tracksDependencyFile] stay available after a reload.
     */
    fun trackedFilesSnapshot(): List<PyDependenciesFile> = trackedFiles

    /**
     * [files] is the `(file -> modification stamp)` cache key; [deferred] is the
     * `listDeclaredPackages` result (pre-completed `CompletableDeferred(null)` when there
     * are no files, otherwise a `LAZY` async on [managerScope]).
     */
    private inner class Entry(
      val files: SequencedMap<PyDependenciesFile, Long>,
      val deferred: Deferred<PyResult<List<PythonPackage>>?>,
    )
  }

  companion object {
    /** The manager of the environment [interpreter] runs in. */
    @ApiStatus.Internal
    @Throws(AlreadyDisposedException::class)
    fun forPythonInterpreter(project: Project, interpreter: PythonInterpreter): PythonPackageManager =
      project.service<PythonPackageManagerService>().forPythonInterpreter(project, interpreter)


    /** The changes of the packages of the interpreters of a project. The manager publishes them on the bus of its project. */
    @Topic.ProjectLevel
    val PACKAGE_MANAGEMENT_TOPIC: Topic<PythonPackageManagementListener> =
      Topic(PythonPackageManagementListener::class.java, Topic.BroadcastDirection.NONE)
  }
}


/**
 * Lists installed packages, awaiting initial loading if necessary.
 *
 * Use this from non-suspending background contexts (e.g. inspection visitors) instead of
 * [PythonPackageManager.listInstalledPackagesSnapshot] when freshness matters — the snapshot
 * may be stale or empty before the initial reload finishes, which causes false-positive
 * "requirement is not satisfied" diagnostics right after PPTW package operations
 * (PY-89774).
 */
@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
@ApiStatus.Internal
fun PythonPackageManager.listInstalledPackagesAsync(): List<PythonPackage> = runBlockingMaybeCancellable {
  listInstalledPackages()
}

@ApiStatus.Internal
@JvmInline
value class PyWorkspaceMember(val name: String)

/**
 * Defines behavior that generates a dependencies file (e.g., `requirements.txt` for pip or `environment.yml` for conda).
 */
@ApiStatus.Internal
interface DependenciesExporter {
  @RequiresEdt
  fun export(file: PsiFile)
}

/**
 * Workspace support for package managers that handle multi-project workspaces (e.g. uv)
 * or dependency groups (e.g. Poetry).
 *
 * Obtain via [PythonPackageManager.workspaceSupport]; `null` for managers without workspace support.
 */
@ApiStatus.Internal
interface PythonWorkspaceSupport {
  /**
   * Returns workspace members for multi-project workspaces.
   * Single-project managers with dependency group support return a list with just [projectName].
   */
  suspend fun getWorkspaceMembers(projectName: ProjectName): List<PyWorkspaceMember>

  /**
   * Returns dependency groups per workspace member.
   * Each key is a workspace member, each value is the list of dependency groups from that member's pyproject.toml.
   */
  suspend fun getDependencyGroups(projectName: ProjectName): Map<PyWorkspaceMember, List<PyDependencyGroup>> = emptyMap()

  /**
   * Resolves the IntelliJ [Module] that corresponds to the given workspace member.
   * Returns null if the mapping cannot be determined (e.g. single-project managers).
   */
  suspend fun resolveModule(member: PyWorkspaceMember): Module? = null
}

private fun PythonPackage.toInstalledPackage(): InstalledPackage =
  InstalledPackage(name, version, isEditableMode, dependencyGroup?.name, editableLocation?.toString())

private fun InstalledPackage.toPythonPackage(): PythonPackage =
  PythonPackage(name, version, editable, dependencyGroup?.let(::PyDependencyGroupName)).also { pkg ->
    pkg.editableLocation = editableLocation?.let(URI::create)
  }
