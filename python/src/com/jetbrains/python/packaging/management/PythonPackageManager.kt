// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION", "removal")

package com.jetbrains.python.packaging.management

import com.intellij.execution.ExecutionException
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.PyProjectTomlFile
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.messages.Topic
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.onFailure
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyRequirement
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
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.isReadOnly
import com.jetbrains.python.sdk.readOnlyErrorMessage
import com.jetbrains.python.sdk.refreshPaths
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException


/**
 * Represents a Python package manager for a specific Python SDK. Encapsulate main operations with package managers
 * @see com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI to execute commands with UI handlers
 */
@ApiStatus.Experimental
abstract class PythonPackageManager @ApiStatus.Internal constructor(
  val project: Project,
  val sdk: Sdk,
) : Disposable.Default {
  /**
   * Whether this manager has an explicit list of top-level dependencies (e.g. from pyproject.toml).
   * When true, only packages from [listDeclaredPackagesCached] are treated as "declared" in the UI,
   * and the rest are shown as transitive.
   * When false (default), all installed packages are considered declared.
   */
  internal open val installedPackagesIncludeTransitive: Boolean = false

  val isInstalledPackagesLoaded: Boolean
    @ApiStatus.Internal
    get() = installedPackages != null

  private val isInited = AtomicBoolean(false)
  private val packageReloadMutex = Mutex()

  private val dependencyCache = DependencyCache()

  private val initializationJob = PyPackageCoroutine.launch(project,
                                                            NON_INTERACTIVE_ROOT_TRACE_CONTEXT + ModalityState.any().asContextElement(),
                                                            start = CoroutineStart.LAZY) {
    initInstalledPackages()
  }.also {
    it.cancelOnDispose(this)
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

  @ApiStatus.Internal
  internal open val treeProvider: DependencyTreeProvider? = null

  abstract val repositoryManager: PythonRepositoryManager

  @ApiStatus.Internal
  suspend fun syncLocked(): PyResult<List<PythonPackage>> {
    if (sdk.isReadOnly) {
      return PyResult.localizedError(sdk.readOnlyErrorMessage)
    }
    syncLockedCommand().getOr { return it }
    return reloadPackages()
  }

  @ApiStatus.Internal
  suspend fun installPackage(
    installRequest: PythonPackageInstallRequest,
    options: List<String> = emptyList(),
    module: Module? = null,
  ): PyResult<List<PythonPackage>> {
    if (sdk.isReadOnly) {
      return PyResult.localizedError(sdk.readOnlyErrorMessage)
    }
    waitForInit()
    installPackageCommand(installRequest, options, module).getOr { return it }

    return reloadPackages()
  }

  @ApiStatus.Internal
  suspend fun updatePackages(vararg packages: PythonRepositoryPackageSpecification): PyResult<List<PythonPackage>> {
    if (sdk.isReadOnly) {
      return PyResult.localizedError(sdk.readOnlyErrorMessage)
    }
    waitForInit()
    updatePackageCommand(*packages).getOr { return it }

    return reloadPackages()
  }

  @ApiStatus.Internal
  suspend fun uninstallPackage(vararg packages: String, workspaceMember: PyWorkspaceMember? = null): PyResult<List<PythonPackage>> {
    if (sdk.isReadOnly) {
      return PyResult.localizedError(sdk.readOnlyErrorMessage)
    }
    if (packages.isEmpty()) {
      return PyResult.success(listInstalledPackagesSnapshot())
    }

    waitForInit()

    val normalizedPackagesNames = packages.map { PyPackageName.normalizePackageName(it) }
    uninstallPackageCommand(*normalizedPackagesNames.toTypedArray(), workspaceMember = workspaceMember).getOr { return it }
    return reloadPackages()
  }

  @ApiStatus.Internal
  open suspend fun reloadPackages(): PyResult<List<PythonPackage>> {
    treeProvider?.invalidateCache()
    return loadPackagesImpl(isInit = false)
  }

  private suspend fun loadPackagesImpl(isInit: Boolean): PyResult<List<PythonPackage>> = packageReloadMutex.withLock {
    // Cancellable: external process call.
    val packages = loadPackagesCommand().getOr { return it }

    val changed = packages != installedPackages
    if (!changed) return PyResult.success(listInstalledPackagesSnapshot())

    // Transactional commit: state mutation + listener notification + scheduled refresh
    // must complete atomically even if the caller is cancelled.
    withContext(NonCancellable) {
      this@PythonPackageManager.installedPackages = packages

      ApplicationManager.getApplication().messageBus.apply {
        syncPublisher(PACKAGE_MANAGEMENT_TOPIC).packagesChanged(sdk)
        syncPublisher(PyPackageManager.PACKAGE_MANAGER_TOPIC).packagesRefreshed(sdk)
      }

      PyPackageCoroutine.launch(project, NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
        reloadOutdatedPackages()
      }.cancelOnDispose(this@PythonPackageManager)
      PyPackageCoroutine.launch(project, NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
        reloadInstalledPackagesMetadata()
      }.cancelOnDispose(this@PythonPackageManager)

      if (!isInit) {
        refreshPaths(project, sdk)
      }
    }

    return PyResult.success(packages)
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
   * `outdatedPackages`: the snapshot is rebuilt by [reloadInstalledPackagesMetadata] each time
   * `loadPackagesImpl` observes a package-list change, in a single helper invocation.
   */
  @ApiStatus.Internal
  suspend fun listInstalledPackagesMetadata(): Map<PyPackageName, PythonPackageMetadata> {
    waitForInit()
    return listInstalledPackagesMetadataSnapshot()
  }

  @ApiStatus.Internal
  fun listInstalledPackagesMetadataSnapshot(): Map<PyPackageName, PythonPackageMetadata> = installedPackagesMetadata

  private suspend fun reloadOutdatedPackages() {
    if (listInstalledPackagesSnapshot().isEmpty()) {
      outdatedPackages = emptyMap()
      return
    }
    val loadedPackages = loadOutdatedPackagesCommand().onFailure {
      thisLogger().warn("Failed to load outdated packages $it")
    }.getOrNull() ?: emptyList()

    val packageMap = loadedPackages.associateBy { it.name }
    if (outdatedPackages == packageMap)
      return

    outdatedPackages = packageMap
    ApplicationManager.getApplication().messageBus.apply {
      syncPublisher(PACKAGE_MANAGEMENT_TOPIC).outdatedPackagesChanged(sdk)
    }
  }

  /**
   * Mirror of [reloadOutdatedPackages]: rebuilds [installedPackagesMetadata] from the helper's
   * single-shot dump of every installed distribution's METADATA file. Skipped silently when
   * there are no installed packages so a fresh / mock SDK doesn't pay the helper-startup cost.
   */
  private suspend fun reloadInstalledPackagesMetadata() {
    if (listInstalledPackagesSnapshot().isEmpty()) {
      installedPackagesMetadata = emptyMap()
      return
    }

    installedPackagesMetadata = sdk.loadInstalledPackagesMetadata().onFailure {
      thisLogger().warn("Failed to load installed package metadata $it")
    }.getOrNull() ?: emptyMap()
  }

  @ApiStatus.Internal
  @CheckReturnValue
  protected abstract suspend fun syncLockedCommand(): PyResult<Unit>

  @ApiStatus.Internal
  open fun updateLockedAction(): (suspend () -> PyResult<Unit>)? = null

  @ApiStatus.Internal
  open fun syncErrorMessage(): PackageManagerErrorMessage? = null

  /**
   * @param module the target workspace member module for workspace-aware package managers (e.g., UV).
   *   When provided, the package is added as a dependency of this specific workspace member
   *   rather than the root project. Package managers that do not support workspaces ignore this parameter.
   */
  @ApiStatus.Internal
  @CheckReturnValue
  protected abstract suspend fun installPackageCommand(
    installRequest: PythonPackageInstallRequest,
    options: List<String>,
    module: Module? = null,
  ): PyResult<Unit>

  @ApiStatus.Internal
  @CheckReturnValue
  protected open suspend fun installPackageDetachedCommand(
    installRequest: PythonPackageInstallRequest,
    options: List<String>,
  ): PyResult<Unit> =
    installPackageCommand(installRequest, options)

  @ApiStatus.Internal
  @CheckReturnValue
  protected abstract suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit>

  @ApiStatus.Internal
  @CheckReturnValue
  protected abstract suspend fun uninstallPackageCommand(
    vararg pythonPackages: String,
    workspaceMember: PyWorkspaceMember? = null,
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
   * Lists project top-level (declared) dependencies with caching based on dependency file modification time.
   * Returns cached result if dependency file hasn't changed since last call.
   *
   * @return null if this package manager doesn't support dependency listing,
   *         PyResult.Failure if listing is supported but failed (e.g., parsing error),
   *         PyResult.Success with the list of dependencies if listing succeeded.
   */
  @ApiStatus.Internal
  suspend fun listDeclaredPackagesCached(): PyResult<List<PythonPackage>>? {
    val stamps = getDependencyFiles()
      .map { it.virtualFile }
      .sortedBy { it.path }
      .map { it to it.modificationStamp }
    if (stamps.isEmpty()) return null
    return dependencyCache.getOrCompute(stamps).await()
  }

  /**
   * Returns the dependency declaration file (e.g., requirements.txt, Pipfile.lock, environment.yml).
   * Returns null if no dependency file is associated with this package manager.
   */
  @ApiStatus.Internal
  @RequiresBackgroundThread
  open fun getDependencyFile(): PyDependenciesFile? = null

  /**
   * Returns every file whose modification should invalidate the declared-packages cache.
   *
   * Defaults to `listOfNotNull(getDependencyFile())`. Workspace-aware managers (e.g. uv)
   * override this to include member pyproject.toml files, so that edits to any member
   * file invalidate the cached declared-packages list.
   */
  @ApiStatus.Internal
  protected open suspend fun getDependencyFiles(): List<PyDependenciesFile> = listOfNotNull(getDependencyFile())

  /**
   * Adds a dependency to the project's dependency declaration file.
   * Returns true if the dependency was successfully added, false if the operation
   * is not supported or failed.
   */
  @ApiStatus.Internal
  suspend fun addDependencyToFile(requirement: PyRequirement): Boolean {
    return getDependencyFile() != null && addDependencyImpl(requirement)
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

  private suspend fun initInstalledPackages() {
    try {
      if (isInited.getAndSet(true))
        return
      if (!PythonSdkType.isMock(sdk)) {
        loadPackagesImpl(isInit = true)
      }
    }
    catch (t: CancellationException) {
      throw t
    }
    catch (t: ExecutionException) {
      thisLogger().warn("Failed to initialize PythonPackageManager for $sdk", t)
    }
  }

  private inner class DependencyCache {
    private var entry: Entry? = null

    @Synchronized
    fun getOrCompute(stamps: List<Pair<VirtualFile, Long>>): Deferred<PyResult<List<PythonPackage>>?> {
      val cached = entry?.takeIf { it.stamps == stamps }
      return cached?.deferred ?: run {
        PyPackageCoroutine.getScope(project).async(NON_INTERACTIVE_ROOT_TRACE_CONTEXT, start = CoroutineStart.LAZY) {
          listDeclaredPackages()
        }.also { entry = Entry(stamps, it) }
      }
    }

    private inner class Entry(
      val stamps: List<Pair<VirtualFile, Long>>,
      val deferred: Deferred<PyResult<List<PythonPackage>>?>,
    )
  }

  companion object {
    @Throws(AlreadyDisposedException::class)
    fun forSdk(project: Project, sdk: Sdk): PythonPackageManager {
      val pythonPackageManagerService = project.service<PythonPackageManagerService>()
      return pythonPackageManagerService.forSdk(project, sdk)
    }

    @Topic.AppLevel
    val PACKAGE_MANAGEMENT_TOPIC: Topic<PythonPackageManagementListener> =
      Topic(PythonPackageManagementListener::class.java, Topic.BroadcastDirection.TO_DIRECT_CHILDREN)
    val RUNNING_PACKAGING_TASKS: Key<Boolean> = Key.create("PyPackageRequirementsInspection.RunningPackagingTasks")

    @ApiStatus.Internal
    data class PackageManagerErrorMessage(
      @param:Nls val descriptionMessage: String,
      @param:Nls val fixCommandMessage: String,
    )
  }
}


/**
 * Lists declared project top-level dependencies (blocking version for Java interop).
 * Returns null by default — this manager doesn't support dependency extraction.
 */
@ApiStatus.Internal
@RequiresBackgroundThread
fun PythonPackageManager.listDeclaredPackagesAsync(): List<PythonPackage>? = runBlockingMaybeCancellable {
  listDeclaredPackagesCached()
}?.getOrNull()

/**
 * Resolves pyproject.toml file from a working directory path.
 * Used by pyproject.toml-based package managers (Poetry, Hatch, UV).
 *
 * @param workingDirectory The directory path where pyproject.toml is expected
 * @return VirtualFile for pyproject.toml, or null if not found
 */
@ApiStatus.Internal
@RequiresBackgroundThread
internal fun resolvePyProjectToml(workingDirectory: Path): PyProjectTomlFile? {
  val pyprojectPath = workingDirectory.resolve(PY_PROJECT_TOML)
  val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(pyprojectPath) ?: return null
  return PyProjectTomlFile(virtualFile)
}

@ApiStatus.Internal
@JvmInline
value class PyWorkspaceMember(val name: String)
