// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION", "removal")

package com.jetbrains.python.packaging.management

import com.intellij.execution.ExecutionException
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.messages.Topic
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.extensions.toPsi
import com.jetbrains.python.getOrNull
import com.jetbrains.python.onFailure
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.isReadOnly
import com.jetbrains.python.sdk.readOnlyErrorMessage
import com.jetbrains.python.sdk.refreshPaths
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.Throws


/**
 * Represents a Python package manager for a specific Python SDK. Encapsulate main operations with package managers
 * @see com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI to execute commands with UI handlers
 */
@ApiStatus.Experimental
abstract class PythonPackageManager(val project: Project, val sdk: Sdk) : Disposable.Default {
  private val isInited = AtomicBoolean(false)
  private val initializationJob = if (!shouldBeInitInstantly()) {
    PyPackageCoroutine.launch(project, NON_INTERACTIVE_ROOT_TRACE_CONTEXT, start = CoroutineStart.LAZY) {
      initInstalledPackages()
    }.also {
      it.cancelOnDispose(this)
    }
  }
  else {
    null
  }


  @ApiStatus.Internal
  @Volatile
  protected var installedPackages: List<PythonPackage> = emptyList()

  @ApiStatus.Internal
  @Volatile
  protected var outdatedPackages: Map<String, PythonOutdatedPackage> = emptyMap()

  private suspend fun createCachedDependencies(dependencyFile: VirtualFile): Deferred<PyResult<List<PythonPackage>>?> {
    val psiFile = readAction { dependencyFile.toPsi(project) } ?: return CompletableDeferred(value = null)
    return CachedValuesManager.getManager(project).getCachedValue(psiFile, CACHE_KEY, { extractDependenciesAsync(dependencyFile) }, false)
  }

  private fun extractDependenciesAsync(dependencyFile: VirtualFile): CachedValueProvider.Result<Deferred<PyResult<List<PythonPackage>>?>> {
    val scope = PyPackageCoroutine.getScope(project)
    val deferred = scope.async(NON_INTERACTIVE_ROOT_TRACE_CONTEXT, start = CoroutineStart.LAZY) {
      extractDependencies()
    }
    return CachedValueProvider.Result.create(deferred, dependencyFile)
  }

  abstract val repositoryManager: PythonRepositoryManager

  @ApiStatus.Internal
  suspend fun sync(): PyResult<List<PythonPackage>> {
    if (sdk.isReadOnly) {
      return PyResult.localizedError(sdk.readOnlyErrorMessage)
    }
    syncCommand().getOr { return it }
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
  suspend fun installPackageDetached(
    installRequest: PythonPackageInstallRequest,
    options: List<String> = emptyList(),
  ): PyResult<List<PythonPackage>> {
    waitForInit()
    installPackageDetachedCommand(installRequest, options).getOr { return it }

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
      return PyResult.success(installedPackages)
    }

    waitForInit()

    val normalizedPackagesNames = packages.map { PyPackageName.normalizePackageName(it) }
    uninstallPackageCommand(*normalizedPackagesNames.toTypedArray(), workspaceMember = workspaceMember).getOr { return it }
    return reloadPackages()
  }

  @ApiStatus.Internal
  open suspend fun reloadPackages(): PyResult<List<PythonPackage>> {
    return loadPackagesImpl(isInit = false)
  }

  private suspend fun loadPackagesImpl(isInit: Boolean): PyResult<List<PythonPackage>> {
    val packages = loadPackagesCommand().getOr {
      return it
    }

    if (packages != installedPackages) {
      if (!isInit) {
        refreshPaths(project, sdk)
      }
      installedPackages = packages
      PyPackageCoroutine.launch(project, NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
        reloadOutdatedPackages()
      }.cancelOnDispose(this)

      ApplicationManager.getApplication().messageBus.apply {
        syncPublisher(PACKAGE_MANAGEMENT_TOPIC).packagesChanged(sdk)
        syncPublisher(PyPackageManager.PACKAGE_MANAGER_TOPIC).packagesRefreshed(sdk)
      }
    }

    return PyResult.success(packages)
  }


  @ApiStatus.Internal
  suspend fun listInstalledPackages(): List<PythonPackage> {
    waitForInit()
    return listInstalledPackagesSnapshot()
  }

  @ApiStatus.Internal
  fun listInstalledPackagesSnapshot(): List<PythonPackage> {
    return installedPackages
  }

  @ApiStatus.Internal
  suspend fun listOutdatedPackages(): Map<String, PythonOutdatedPackage> {
    waitForInit()
    return listOutdatedPackagesSnapshot()
  }


  @ApiStatus.Internal
  fun listOutdatedPackagesSnapshot(): Map<String, PythonOutdatedPackage> {
    return outdatedPackages
  }

  private suspend fun reloadOutdatedPackages() {
    if (installedPackages.isEmpty()) {
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

  @ApiStatus.Internal
  @CheckReturnValue
  protected abstract suspend fun syncCommand(): PyResult<Unit>

  @ApiStatus.Internal
  open fun syncErrorMessage(): PackageManagerErrorMessage? = null

  /**
   * @param module the target workspace member module for workspace-aware package managers (e.g., UV).
   *   When provided, the package is added as a dependency of this specific workspace member
   *   rather than the root project. Package managers that do not support workspaces ignore this parameter.
   */
  @ApiStatus.Internal
  @CheckReturnValue
  protected abstract suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>, module: Module? = null): PyResult<Unit>

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
  protected abstract suspend fun uninstallPackageCommand(vararg pythonPackages: String, workspaceMember: PyWorkspaceMember? = null): PyResult<Unit>

  @ApiStatus.Internal
  protected abstract suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>>

  @ApiStatus.Internal
  protected abstract suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>>

  /**
   * Extracts project top-level dependencies.
   * Returns null by default — this manager doesn't support dependency extraction.
   */
  @ApiStatus.Internal
  open suspend fun extractDependencies(): PyResult<List<PythonPackage>>? = null

  /**
   * Extracts project top-level dependencies with caching based on dependency file modification time.
   * Returns cached result if dependency file hasn't changed since last extraction.
   * Uses Platform's CachedValuesManager with the dependency file as an invalidation dependency.
   *
   * @return null if this package manager doesn't support dependency extraction,
   *         PyResult.Failure if extraction is supported but failed (e.g., parsing error),
   *         PyResult.Success with the list of dependencies if extraction succeeded.
   */
  @ApiStatus.Internal
  suspend fun extractDependenciesCached(): PyResult<List<PythonPackage>>? {
    val dependencyFile = getDependencyFile() ?: return null
    return createCachedDependencies(dependencyFile).await()
  }

  /**
   * Returns the dependency declaration file (e.g., requirements.txt, Pipfile.lock, environment.yml).
   * Returns null if no dependency file is associated with this package manager.
   */
  @ApiStatus.Internal
  @RequiresBackgroundThread
  open fun getDependencyFile(): VirtualFile? = null


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
    initializationJob?.join()
    if (shouldBeInitInstantly()) {
      initInstalledPackages()
    }
  }

  private suspend fun initInstalledPackages() {
    try {
      if (isInited.getAndSet(true))
        return
      if (installedPackages.isEmpty() && !PythonSdkType.isMock(sdk)) {
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

  //Some test on EDT so need to be inited on first create
  private fun shouldBeInitInstantly(): Boolean = ApplicationManager.getApplication().isUnitTestMode

  companion object {
    private val CACHE_KEY = Key.create<CachedValue<Deferred<PyResult<List<PythonPackage>>?>>>("PythonPackageManagerDependenciesCache")

    @RequiresBackgroundThread
    @Throws(AlreadyDisposedException::class)
    fun forSdk(project: Project, sdk: Sdk): PythonPackageManager {
      val pythonPackageManagerService = project.service<PythonPackageManagerService>()
      val manager = pythonPackageManagerService.forSdk(project, sdk)
      if (manager.shouldBeInitInstantly()) {
        runBlockingMaybeCancellable {
          manager.initInstalledPackages()
        }
      }
      return manager
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
 * Extracts project top-level dependencies (blocking version for Java interop).
 * Returns null by default — this manager doesn't support dependency extraction.
 */
@ApiStatus.Internal
@RequiresBackgroundThread
fun PythonPackageManager.extractDependenciesAsync(): List<PythonPackage>? = runBlockingMaybeCancellable {
  extractDependenciesCached()
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
internal fun resolvePyProjectToml(workingDirectory: Path): VirtualFile? {
  val pyprojectPath = workingDirectory.resolve(PY_PROJECT_TOML)
  return runBlockingMaybeCancellable {
    readAction {
      VirtualFileManager.getInstance().refreshAndFindFileByNioPath(pyprojectPath)
    }
  }
}

@ApiStatus.Internal
@JvmInline
value class PyWorkspaceMember(val name: String)
