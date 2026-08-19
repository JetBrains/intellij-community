@file:Suppress("UnstableApiUsage")

package com.jetbrains.python.sdk.evolution

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.util.coroutines.childScope
import com.intellij.python.community.impl.conda.CondaPyTool
import com.intellij.python.community.impl.poetry.backend.PoetryPyTool
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.community.services.systemPython.createVenvFromSystemPython
import com.intellij.python.hatch.HatchVirtualEnvironment
import com.intellij.python.hatch.getHatchService
import com.intellij.python.hatch.resolveHatchWorkingDirectory
import com.intellij.python.pytools.resolveExecutable
import com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.defaultVenvDir
import com.intellij.python.sdk.backend.evolution.discoverVenvs
import com.intellij.python.sdk.backend.evolution.firstFreeVenvDir
import com.intellij.python.sdk.backend.evolution.getPythonVersion
import com.intellij.python.sdk.backend.evolution.resolvePythonExecutable
import com.intellij.python.uv.backend.UvPyTool
import com.intellij.openapi.module.Module
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.icons.AllIcons
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.performToolInstallation
import com.intellij.python.sdk.common.evolution.EvoAddNewDto
import com.intellij.python.sdk.common.evolution.EvoAddNewOptionDto
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.common.evolution.EvoLeafKind
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoNodeDto
import com.intellij.python.sdk.common.evolution.EvoSelectResultDto
import com.intellij.python.sdk.common.evolution.PyEvoRegistry
import com.intellij.python.sdk.common.evolution.PyEvoSdkApi
import com.intellij.python.sdk.common.evolution.PyInterpreterDto
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
import com.jetbrains.python.Result
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.getOrNull
import com.jetbrains.python.module.PyModuleService
import com.jetbrains.python.packaging.PyVersionSpecifiers
import com.jetbrains.python.packaging.conda.CondaPackageManager
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.hatch.packaging.HatchPackageManager
import com.jetbrains.python.sdk.poetry.PoetryPackageManager
import com.jetbrains.python.sdk.uv.UvPackageManager
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import com.jetbrains.python.project.PyProject.Companion.getPyProjects
import com.jetbrains.python.project.project
import com.jetbrains.python.impl.getRootModuleOrNull
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.PyRemoteSdkAdditionalDataMarker
import com.jetbrains.python.sdk.collectAddInterpreterActions
import com.jetbrains.python.sdk.configurePythonSdk
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.getSdkCreator
import com.jetbrains.python.sdk.getAssignablePythonSdks
import com.jetbrains.python.sdk.isAssociatedWithModule
import com.jetbrains.python.sdk.isSdkConfigurationInProgress
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.withSdkConfigurationLock
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.PythonAddLocalInterpreterDialog
import com.jetbrains.python.sdk.add.v2.PythonAddLocalInterpreterPresenter
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers
import com.jetbrains.python.sdk.add.v2.toEelFileSystem
import com.jetbrains.python.sdk.createSdkGuessingTypeByPath
import com.jetbrains.python.sdk.conda.condaSupportedLanguages
import com.jetbrains.python.sdk.conda.createCondaSdkAlongWithNewEnv
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.poetry.createNewPoetrySdk
import com.jetbrains.python.sdk.poetry.createPoetrySdk
import com.jetbrains.python.sdk.pyInterpreterPresentation
import com.jetbrains.python.sdk.uv.setupExistingEnvAndSdk
import com.jetbrains.python.sdk.uv.setupNewUvSdkAndEnv
import com.jetbrains.python.sdk.uv.impl.createUvCli
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import com.jetbrains.python.hatch.sdk.createSdk
import io.github.z4kn4fein.semver.Version
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

private val LOG = logger<PyEvoSdkApiProvider>()

/** Descending star ratings for the "Shortcuts" autoconfigure rows: the best (first) option gets a full star, then 4→1. */
private val AUTOCONFIG_RATING_ICONS = listOf(
  AllIcons.Ide.Rating, AllIcons.Ide.Rating4, AllIcons.Ide.Rating3, AllIcons.Ide.Rating2, AllIcons.Ide.Rating1,
)

/**
 * The project's `requires-python` from `pyproject.toml` (or null when absent), so the version pickers offer only
 * versions the project allows (as the v2 dialog does). Shared by the uv/pip and poetry providers.
 */
internal suspend fun requiresPython(baseDir: Path): String? = withContext(Dispatchers.IO) {
  val toml = baseDir.resolve(PY_PROJECT_TOML)
  if (!toml.exists()) return@withContext null
  runCatching { PyProjectToml.parse(toml.readText())?.project?.requiresPython }.getOrNull()
}

internal class PyEvoSdkApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<PyEvoSdkApi>()) { PyEvoSdkApiImpl }
  }
}

private object PyEvoSdkApiImpl : PyEvoSdkApi {
  private val providers get() = PyEvoEnvironmentProvider.EP_NAME.extensionList

  /**
   * The root "Python Interpreter Widget" coroutine for one built popup tree, keyed by the frontend's per-tree-build
   * `traceId`. Every tool of that tree runs in a child of this scope, so cancelling it (on eviction) cancels all the
   * tree's in-flight commands. A rebuilt tree (new `traceId`) gets a fresh root; a re-open served from the frontend
   * cache makes no calls at all.
   */
  private val rootScopes: Cache<String, CoroutineScope> = Caffeine.newBuilder()
    .maximumSize(50)
    .expireAfterAccess(Duration.ofMinutes(10))
    .removalListener<String, CoroutineScope> { _, scope, _ -> scope?.cancel() }
    .build()

  private fun rootScope(project: Project, traceId: String): CoroutineScope =
    rootScopes.get(traceId) { newRootScope(project) }

  /** A fresh "Python Interpreter Widget" root coroutine, parented to the project scope so it is never unparented. */
  private fun newRootScope(project: Project): CoroutineScope =
    project.service<EvoWidgetTraceScope>().scope.childScope(
      name = "Python Interpreter Widget",
      context = TraceContext(PySdkBundle.message("evolution.trace.widget.root"), null),
    )

  /**
   * Each tool's own coroutine — a child of its tree's [rootScope], wrapped with the tool's [TraceContext] — keyed by
   * `traceId|nodeId`. A tool lists its environments ([loadNode]) and detects their versions ([resolveInterpreterVersion])
   * in this one coroutine, so both share its context and appear together under that tool in the trace view. Recreated
   * when the cached one was already cancelled (e.g. its root expired).
   */
  private val toolScopes: Cache<String, CoroutineScope> = Caffeine.newBuilder()
    .maximumSize(200)
    .expireAfterAccess(Duration.ofMinutes(10))
    .removalListener<String, CoroutineScope> { _, scope, _ -> scope?.cancel() }
    .build()

  private fun toolScope(project: Project, traceId: String, nodeId: String, label: @Nls String): CoroutineScope {
    toolScopes.getIfPresent("$traceId|$nodeId")?.let { if (it.isActive) return it }
    val root = rootScope(project, traceId)
    return root.childScope(name = label, context = TraceContext(label, root)).also { toolScopes.put("$traceId|$nodeId", it) }
  }

  /**
   * Cached env-list result for tools measured as slow (see [slowLoadThreshold]), keyed by `projectId|moduleName|nodeId`
   * and kept for 10 min. Fast tools are not cached here — the frontend's short-lived popup-tree cache covers them. A
   * forced reload (the tool's reload icon) refills this entry.
   */
  private val envListCache: Cache<String, EvoLoadResultDto> = Caffeine.newBuilder()
    .maximumSize(100)
    .expireAfter(object : Expiry<String, EvoLoadResultDto> {
      // Read the TTL live from the registry on each write, so the flag takes effect without a restart.
      override fun expireAfterCreate(key: String, value: EvoLoadResultDto, currentTime: Long): Long = slowCacheDuration.inWholeNanoseconds
      override fun expireAfterUpdate(key: String, value: EvoLoadResultDto, currentTime: Long, currentDuration: Long): Long = slowCacheDuration.inWholeNanoseconds
      override fun expireAfterRead(key: String, value: EvoLoadResultDto, currentTime: Long, currentDuration: Long): Long = currentDuration
    })
    .build()

  /** Keys (`projectId|moduleName|nodeId`) whose last scan was slow — they use the long-cache + reload-icon strategy. */
  private val slowTools: MutableSet<String> = ConcurrentHashMap.newKeySet()

  /** A tool whose env scan takes longer than this is treated as "slow" (long cache + reload icon). Registry-tunable. */
  private val slowLoadThreshold: kotlin.time.Duration
    get() = PyEvoRegistry.slowToolThresholdSeconds.seconds

  /** How long a slow tool's env list stays cached. Registry-tunable. */
  private val slowCacheDuration: kotlin.time.Duration
    get() = PyEvoRegistry.slowToolCacheSeconds.seconds

  override suspend fun getCurrentInterpreter(projectId: ProjectId, moduleName: String): PyInterpreterDto? {
    val pyProject = resolvePyProject(projectId, moduleName) ?: return null
    val sdk = PythonSdkUtil.findPythonSdk(pyProject.residesOnModule) ?: return null
    // The current-interpreter display works with Eel-based interpreters; remote/target SDKs surface only in the associated list.
    if (sdk.sdkAdditionalData is PyRemoteSdkAdditionalDataMarker) return null
    val presentation = sdk.pyInterpreterPresentation()
    return PyInterpreterDto(
      title = presentation.shortName,
      description = presentation.description,
      icon = presentation.icon.rpcId(),
      ref = PyInterpreterRef.ExistingSdk(sdk.name),
      packageManagerActionIds = packageManagerActionIds(pyProject.project, sdk),
    )
  }

  /**
   * Action ids (from the `PythonPackageManagerActions` group) applicable to [sdk]'s package manager. Mirrors that
   * group so the current-interpreter popup shows the right tool actions instead of always poetry's.
   */
  private fun packageManagerActionIds(project: Project, sdk: Sdk): List<String> =
    when (runCatching { PythonPackageManager.forSdk(project, sdk) }.getOrNull()) {
      is UvPackageManager -> listOf("UvLockAction", "UvSyncAction")
      is PoetryPackageManager -> listOf("PoetryLockAction", "PoetryUpdateAction")
      is CondaPackageManager -> listOf("CondaExportAction", "CondaUpdateEnvAction")
      is HatchPackageManager -> listOf("HatchRunAction")
      else -> emptyList()
    }

  override suspend fun listNodes(projectId: ProjectId, moduleName: String): List<EvoNodeDto> {
    val pyProject = resolvePyProject(projectId, moduleName) ?: return emptyList()
    val fileSystem = eelFileSystem(pyProject)
    // Availability is probed before any popup tree exists, so use a transient root with one child coroutine per tool.
    val root = newRootScope(pyProject.project)
    return try {
      // Probe every tool in PARALLEL (each on IO): first-time detection may spawn a process per tool, so launch all
      // probes first and only then await, making the total wait the slowest tool rather than the sum. (The detection
      // itself is off-EDT and cached inside PyExecutableCache; this only stops us from serializing the tools.)
      val available = providers
        .map { provider ->
          root.childScope(name = provider.label, context = TraceContext(provider.label, root))
            .async(Dispatchers.IO) { runCatching { provider.isAvailable(pyProject, fileSystem) }.getOrDefault(false) }
        }
        .awaitAll()
      providers.filterIndexed { i, _ -> available[i] }.map { it.getNode() }
    }
    finally {
      root.cancel()
    }
  }

  override suspend fun listShortcuts(projectId: ProjectId, moduleName: String): List<EvoLeafDto> {
    val pyProject = resolvePyProject(projectId, moduleName) ?: return emptyList()
    val module = pyProject.residesOnModule
    // Only at setup time (no interpreter yet): listing evaluates every configurator, so we never do it once an SDK is set.
    if (PythonSdkUtil.findPythonSdk(module) != null) return emptyList()
    // Every setup option the IDE can offer for this module (the same set the "no interpreter configured" inspection
    // ranks), sorted best-first. A single tool can have several configurators with distinct tool ids but the same
    // suggestion (e.g. uv's "uv" and "uvBase" both offer "Set up uv environment"), so collapse by the visible label and
    // keep the first (best-sorted) — its tool id drives selection. Each row runs that option
    // (PyInterpreterRef.Autoconfigure(toolId) → autoconfigureInterpreter).
    return PyProjectSdkConfigurationExtension.findAllSortedForModule(module)
      .distinctBy { it.createSdkInfo.intentionName }
      .mapIndexed { index, option ->
        EvoLeafDto(
          title = option.createSdkInfo.intentionName,
          // Rank by position, best-first (as the old Autoconfigure node did): only the top option gets a full star.
          icon = AUTOCONFIG_RATING_ICONS.getOrElse(index) { AllIcons.Ide.Rating1 }.rpcId(),
          kind = EvoLeafKind.SELECT_ENV,
          ref = PyInterpreterRef.Autoconfigure(option.toolId.id),
        )
      }
  }

  override suspend fun loadNode(projectId: ProjectId, moduleName: String, nodeId: String, traceId: String, forceRefresh: Boolean): EvoLoadResultDto {
    val pyProject = resolvePyProject(projectId, moduleName)
                    ?: return EvoLoadResultDto.Error(PySdkBundle.message("evolution.error.module.not.found", moduleName))
    val provider = providers.firstOrNull { it.id == nodeId }
                   ?: return EvoLoadResultDto.Error(PySdkBundle.message("evolution.error.unknown.node", nodeId))
    val cacheKey = "$projectId|$moduleName|$nodeId"
    if (cacheKey in slowTools && !forceRefresh) {
      envListCache.getIfPresent(cacheKey)?.let { return it }
    }
    return try {
      val discovered = discoverVenvs(baseDirs(pyProject), excludedRoots(pyProject))
      // Run (and time) the tool's env listing in the tool's own coroutine.
      val timed = measureTimedValue {
        toolScope(pyProject.project, traceId, provider.id, provider.label)
          .async {
            val loaded = provider.loadSections(pyProject, eelFileSystem(pyProject), discovered)
            withHatchVersionPickers(withAddNewEnv(loaded, provider.id, pyProject), provider.id, pyProject)
          }
          .await()
      }
      // Slow tools get the long cache + reload icon; fast tools fall back to the frontend's short cache.
      val slow = timed.duration > slowLoadThreshold
      if (slow) slowTools.add(cacheKey) else slowTools.remove(cacheKey)
      val result = (timed.value as? EvoLoadResultDto.Ok)?.copy(refreshable = slow) ?: timed.value
      if (slow && result is EvoLoadResultDto.Ok) envListCache.put(cacheKey, result)
      result
    }
    catch (e: Exception) {
      LOG.warn("Failed to load Evo node '$nodeId' for module '$moduleName'", e)
      EvoLoadResultDto.Error(e.message ?: e.javaClass.simpleName)
    }
  }

  override suspend fun listAssociatedInterpreters(projectId: ProjectId, moduleName: String): List<PyInterpreterDto> {
    val pyProject = resolvePyProject(projectId, moduleName) ?: return emptyList()
    val module = pyProject.residesOnModule
    // Only interpreters actually associated with this module (its own envs) — not every configured SDK, which for a
    // fresh project would be a huge global list. De-duplicated like the classic popup.
    return pyProject.project.getAssignablePythonSdks(module)
      .filter { it.isAssociatedWithModule(module) }
      .distinctBy { it.sdkAdditionalData?.javaClass to it.homePath }
      .map { sdk ->
        val presentation = sdk.pyInterpreterPresentation()
        PyInterpreterDto(
          title = presentation.shortName,
          description = presentation.description,
          icon = presentation.icon.rpcId(),
          ref = PyInterpreterRef.ExistingSdk(sdk.name),
        )
      }
  }

  override suspend fun selectInterpreter(projectId: ProjectId, moduleName: String, ref: PyInterpreterRef, nodeId: String): EvoSelectResultDto {
    val pyProject = resolvePyProject(projectId, moduleName)
                    ?: return EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.module.not.found", moduleName))
    val fileSystem = eelFileSystem(pyProject)
    if (ref is PyInterpreterRef.Autoconfigure) return autoconfigureInterpreter(pyProject, fileSystem, ref.toolId)
    return try {
      // The whole create-or-select + apply runs under the SDK-configuration lock, which serializes concurrent
      // configuration and (via withBackgroundProgress) shows a visible task; the widget spinners on the same lock
      // (see isSdkConfigurationInProgress). The tool creators' own withProgressText steps attach to that progress.
      withSdkConfigurationLock(pyProject.project) {
        val sdk = when (ref) {
          is PyInterpreterRef.ExistingSdk ->
            PythonSdkUtil.getAllSdks().find { it.name == ref.sdkName }
            ?: return@withSdkConfigurationLock EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.sdk.not.found", ref.sdkName))
          is PyInterpreterRef.DetectedPath -> {
            val homePath = Path.of(ref.homePath)
            // Tool-aware "select existing" (uv/poetry/conda/hatch); a plain venv (pip) falls back to the generic path.
            createSdkForDetectedEnv(pyProject, fileSystem, homePath, nodeId)
            ?: createSdkGuessingTypeByPath(PathHolder.Eel(homePath), fileSystem, ModuleOrProject.ModuleAndProject(pyProject.residesOnModule), null).getOrNull()
            ?: return@withSdkConfigurationLock EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.select.failed"))
          }
          is PyInterpreterRef.CreateEnv ->
            createSdkForCreateEnv(pyProject, fileSystem, ref.token, ref.folder, ref.name, nodeId)
            ?: return@withSdkConfigurationLock EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.select.failed"))
        }
        // Apply via the module setter (like the add-interpreter dialog's setupSdk and the classic widget): it runs
        // the write on the EDT and refreshes/notifies.
        pyProject.residesOnModule.pythonSdk = sdk
        EvoSelectResultDto.Ok
      }
    }
    catch (e: Exception) {
      LOG.warn("Failed to select interpreter for module '$moduleName'", e)
      EvoSelectResultDto.Error(e.message ?: e.javaClass.simpleName)
    }
  }

  /**
   * Runs the setup option [toolId] (a "Shortcuts" row) for the module — the same work the "no interpreter configured"
   * inspection's fix does. The option is re-resolved (its `sdkCreator` can't cross RPC): an existing/creatable env is
   * created and applied; a not-yet-installed tool is installed first (then the env is created in the same click if it
   * became available). Runs under the SDK-configuration lock (so the widget spinner shows and the resulting
   * `rootsChanged` refreshes it); the lock is held once here, so the tool creators must not take it themselves.
   */
  private suspend fun autoconfigureInterpreter(pyProject: PyProject, fileSystem: EelFileSystem, toolId: String): EvoSelectResultDto =
    try {
      val module = pyProject.residesOnModule
      withSdkConfigurationLock(pyProject.project) {
        // TOCTOU: an SDK may have appeared since the row was listed.
        if (PythonSdkUtil.findPythonSdk(module) != null) return@withSdkConfigurationLock EvoSelectResultDto.Ok
        val option = PyProjectSdkConfigurationExtension.findAllSortedForModule(module).firstOrNull { it.toolId.id == toolId }
                     ?: return@withSdkConfigurationLock EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.select.failed"))
        when (val info = option.createSdkInfo) {
          is CreateSdkInfo.ExistingEnv, is CreateSdkInfo.WillCreateEnv ->
            if (applyAutoconfigOption(module, option)) EvoSelectResultDto.Ok
            else EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.select.failed"))
          is CreateSdkInfo.WillInstallTool -> {
            // The option's tool isn't installed: install it (like the inspection's install fix), then re-resolve and,
            // if the option became creatable, create & apply the env in the same click.
            val tool = PyTool.findByPackageName(info.toolToInstall)
                       ?: return@withSdkConfigurationLock EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.select.failed"))
            val installed = tool.performToolInstallation(fileSystem.eelDescriptor.toEelApi()).getOrNull()
                            ?: return@withSdkConfigurationLock EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.select.failed"))
            info.pathPersister(installed)
            PyProjectSdkConfigurationExtension.findAllSortedForModule(module).firstOrNull { it.toolId.id == toolId }?.let { applyAutoconfigOption(module, it) }
            EvoSelectResultDto.Ok
          }
        }
      }
    }
    catch (e: Exception) {
      LOG.warn("Failed to autoconfigure interpreter for module '${pyProject.residesOnModule.name}'", e)
      EvoSelectResultDto.Error(e.message ?: e.javaClass.simpleName)
    }

  /**
   * Creates the env for a resolved setup [option] and assigns it to [module] (and its tool root module), mirroring the
   * inspection's `setSdkUsingCreateSdkInfo`. Returns false when the option has no creator (a not-yet-installed tool) or
   * creation failed. Must be called under the SDK-configuration lock.
   */
  private suspend fun applyAutoconfigOption(module: Module, option: CreateSdkInfoWithTool): Boolean {
    val sdk = when (val info = option.createSdkInfo) {
      // Both carry a creator (smart-cast to CreateSdkInfoWithSdkCreator in this branch); a not-yet-installed tool has none.
      is CreateSdkInfo.ExistingEnv, is CreateSdkInfo.WillCreateEnv -> info.getSdkCreator(module).createSdk().getOrNull()
      is CreateSdkInfo.WillInstallTool -> null
    } ?: return false
    module.getRootModuleOrNull(option.toolId)?.also { configurePythonSdk(it.project, it, sdk) }
    configurePythonSdk(module.project, module, sdk)
    return true
  }

  override suspend fun sdkConfigurationInProgress(projectId: ProjectId): Flow<Boolean> =
    projectId.findProjectOrNull()?.isSdkConfigurationInProgress ?: flowOf(false)

  /**
   * Creates the correctly-typed SDK for an existing env at [homePath] using the tool's own "select existing" logic
   * (the same the v2 Add dialog runs), dispatched by [nodeId]. Returns `null` for `pip`/unknown or on failure, so the
   * caller falls back to the generic path-based SDK.
   */
  private suspend fun createSdkForDetectedEnv(pyProject: PyProject, fileSystem: EelFileSystem, homePath: Path, nodeId: String): Sdk? {
    val module = pyProject.residesOnModule
    return when (nodeId) {
      "uv" -> {
        val uvPath = UvPyTool.getInstance().resolveExecutable(fileSystem) ?: return null
        setupExistingEnvAndSdk(pythonBinary = PathHolder.Eel(homePath), uvPath = uvPath, workingDir = pyProject.baseDir, fileSystem = fileSystem, usePip = false).getOrNull()
      }
      "Poetry" -> createPoetrySdk(pyProject.baseDir, PathHolder.Eel(homePath), fileSystem).getOrNull()
      "Conda" -> {
        val condaExe = CondaPyTool.getInstance().resolveExecutable(fileSystem) ?: return null
        val envDir = homePath.parent?.parent ?: return null
        val envs = PyCondaEnv.getEnvs(PyCondaCommand(condaExe.path.toString(), null).asBinaryToExec()).getOrNull() ?: return null
        val env = envs.firstOrNull { candidate ->
          when (val id = candidate.envIdentity) {
            is PyCondaEnvIdentity.UnnamedEnv -> runCatching { Path.of(id.envPath) == envDir }.getOrDefault(false)
            is PyCondaEnvIdentity.NamedEnv -> envDir.fileName?.toString() == id.envName
          }
        } ?: return null
        env.createSdkFromThisEnv(null, PythonSdkUtil.getAllSdks(), pyProject.baseDir).getOrNull()
      }
      "Hatch" -> {
        val hatchService = module.getHatchService(fileSystem).getOrNull() ?: return null
        val env = hatchService.findVirtualEnvironments().getOrNull()?.firstOrNull { candidate ->
          candidate.pythonVirtualEnvironment?.pythonHomePath?.path?.resolvePythonExecutable()?.toString() == homePath.toString()
        } ?: return null
        val workingDir = resolveHatchWorkingDirectory(pyProject.project, module).getOrNull() ?: pyProject.baseDir
        env.createSdk(workingDir, fileSystem, null).getOrNull()
      }
      else -> null // "pip"/venv/unknown → generic fallback
    }
  }

  /**
   * Unwraps a creator result: on failure surfaces it to the user via the default [ErrorSink] (a process error opens the
   * process-execution-error dialog with the command's output) instead of silently swallowing it, and returns `null`.
   */
  private suspend fun <T> Result<T, com.jetbrains.python.errorProcessing.PyError>.getOrShowError(project: Project): T? =
    when (this) {
      is Result.Success -> result
      is Result.Failure -> { ErrorSink().emit(error, project); null }
    }

  /**
   * Creates the SDK for a declared-but-not-yet-materialized env (poetry per-version, hatch declared env) using the
   * tool's "create" logic, dispatched by [nodeId]. [token] is tool-specific (poetry: base Python path; hatch: env
   * name). Returns `null` on failure (surfaced to the user via [getOrShowError]).
   */
  private suspend fun createSdkForCreateEnv(pyProject: PyProject, fileSystem: EelFileSystem, token: String, folder: String?, name: String?, nodeId: String): Sdk? {
    val module = pyProject.residesOnModule
    val project = pyProject.project
    // uv/pip env location: the (possibly user-edited) [name] folder inside the [folder] containing dir. Fallbacks keep
    // older callers working (folder as a full path, or the first free `.venv{X}` under the base dir).
    val venvDir = when {
      !folder.isNullOrBlank() && !name.isNullOrBlank() -> Path.of(folder).resolve(name)
      !folder.isNullOrBlank() -> pyProject.baseDir.resolve(folder)
      else -> firstFreeVenvDir(pyProject.baseDir)
    }
    return when (nodeId) {
      "uv" -> {
        val uvExe = UvPyTool.getInstance().resolveExecutable(fileSystem) ?: return null
        if (venvDir.exists()) return existsError(project, venvDir.fileName.toString())
        // token is the chosen Python version ("" = uv's default).
        val version = token.takeIf { it.isNotBlank() }?.let { runCatching { Version.parse(it, strict = false) }.getOrNull() }
        setupNewUvSdkAndEnv(
          uvExecutable = uvExe,
          workingDir = pyProject.baseDir,
          venvPath = PathHolder.Eel(venvDir),
          fileSystem = fileSystem,
          version = version,
          errorSink = ErrorSink(),
        ).getOrShowError(project)
      }
      "pip" -> {
        if (venvDir.exists()) return existsError(project, venvDir.fileName.toString())
        // token is the chosen system Python's binary path.
        val eelApi = fileSystem.eelDescriptor.toEelApi()
        val systemPython = SystemPythonService().findSystemPythons(eelApi).firstOrNull { it.pythonBinary.pathString == token } ?: return null
        val venvPython = createVenvFromSystemPython(systemPython, venvDir).getOrShowError(project) ?: return null
        createSdkGuessingTypeByPath(PathHolder.Eel(venvPython), fileSystem, ModuleOrProject.ModuleAndProject(module), null).getOrShowError(project)
      }
      "Poetry" -> {
        val poetryExe = PoetryPyTool.getInstance().resolveExecutable(fileSystem) ?: return null
        createNewPoetrySdk(
          moduleBasePath = pyProject.baseDir,
          basePythonBinaryPath = PathHolder.Eel(Path.of(token)),
          fileSystem = fileSystem,
          poetryExecutable = poetryExe,
          installPackages = false,
          errorSink = ErrorSink(),
          // The in-project "add new" carries a target folder; the per-version cache rows don't (poetry uses its cache).
          inProjectEnv = folder != null,
          targetPanelExtension = null,
        ).getOrShowError(project)
      }
      "Hatch" -> {
        // Version picker: folder = declared env name, token = chosen base python. Fallback (no picker): token = env name.
        val envName = folder ?: token
        val hatchService = module.getHatchService(fileSystem).getOrNull() ?: return null
        val hatchEnv = hatchService.findVirtualEnvironments().getOrNull()
                         ?.firstOrNull { it.hatchEnvironment.name == envName }?.hatchEnvironment ?: return null
        val eelApi = fileSystem.eelDescriptor.toEelApi()
        val basePython = if (folder != null) Path.of(token)
                         else SystemPythonService().findSystemPythons(eelApi).firstOrNull()?.pythonBinary ?: return null
        val venv = hatchService.createVirtualEnvironment(PathHolder.Eel(basePython), envName).getOrShowError(project) ?: return null
        HatchVirtualEnvironment(hatchEnv, venv).createSdk(hatchService.getWorkingDirectoryPath(), fileSystem, null).getOrShowError(project)
      }
      "Conda" -> {
        // Named conda env: name = the (possibly user-edited) env name, token = the chosen Python version. Conda
        // provides the interpreter for that version, so no base system Python is needed.
        val condaExe = CondaPyTool.getInstance().resolveExecutable(fileSystem) ?: return null
        val envName = name?.takeIf { it.isNotBlank() } ?: folder?.takeIf { it.isNotBlank() } ?: return null
        val langLevel = LanguageLevel.fromPythonVersion(token) ?: return null
        // conda itself refuses to recreate an existing named env; its error is surfaced by getOrShowError below.
        PyCondaCommand(condaExe.path.toString(), null)
          .createCondaSdkAlongWithNewEnv(NewCondaEnvRequest.EmptyNamedEnv(langLevel, envName), PythonSdkUtil.getAllSdks(), pyProject.baseDir)
          .getOrShowError(project)
      }
      else -> null
    }
  }

  /** Surfaces a "that environment already exists" error (so we never silently overwrite/recreate) and returns null. */
  private suspend fun existsError(project: Project, name: String): Sdk? {
    ErrorSink().emit(MessageError(PySdkBundle.message("evolution.error.env.exists", name)), project)
    return null
  }

  /**
   * For the uv/pip nodes, computes the in-widget "add new environment" flow (target folder + Python version choices)
   * and attaches it to every section that offers an add-new row. Other nodes (and empty/failed probes) are returned
   * unchanged, so the frontend keeps its modal "Add new environment" row.
   */
  private suspend fun withAddNewEnv(result: EvoLoadResultDto, nodeId: String, pyProject: PyProject): EvoLoadResultDto {
    if (result !is EvoLoadResultDto.Ok) return result
    val options = addNewVersionOptions(nodeId, pyProject).takeIf { it.isNotEmpty() } ?: return result
    return result.copy(sections = result.sections.map { section ->
      if (!section.addNew) return@map section
      val addNewEnv = when (nodeId) {
        // Conda envs are named, not folder-based: propose a free env name (from the provider) and let the user edit it.
        // `path` is unused for conda — the name is the env name.
        "Conda" -> {
          val envName = section.addNewFolderPath ?: (pyProject.baseDir.fileName?.toString() ?: "conda")
          EvoAddNewDto(name = envName, path = "", options = options, nameEditable = true)
        }
        // Poetry's in-project env is always the fixed `.venv` (poetry ignores any other name) → non-editable.
        "Poetry" -> {
          val dir = defaultVenvDir(section.addNewFolderPath?.let { Path.of(it) } ?: pyProject.baseDir)
          EvoAddNewDto(name = dir.fileName.toString(), path = dir.pathString, options = options, nameEditable = false)
        }
        // uv/pip: the env folder is created inside the section's containing dir; propose the first-free `.venv{X}` name,
        // which the user can edit. `path` is that containing dir. Taken names = EVERY existing entry in that dir (not just
        // virtualenvs) — any file/folder with the same name would block creating the env there.
        else -> {
          val container = section.addNewFolderPath?.let { Path.of(it) } ?: pyProject.baseDir
          val taken = runCatching { withContext(Dispatchers.IO) { container.listDirectoryEntries().map { it.fileName.toString() } } }.getOrDefault(emptyList())
          EvoAddNewDto(name = firstFreeVenvDir(container).fileName.toString(), path = container.pathString, options = options, nameEditable = true, takenNames = taken)
        }
      }
      section.copy(addNewEnv = addNewEnv)
    })
  }

  /**
   * For the Hatch node, turns each not-yet-created declared env (a `CreateEnv` leaf) into a Python-version picker so the
   * user chooses the base Python instead of always getting the latest. Other nodes are returned unchanged.
   */
  private suspend fun withHatchVersionPickers(result: EvoLoadResultDto, nodeId: String, pyProject: PyProject): EvoLoadResultDto {
    if (nodeId != "Hatch" || result !is EvoLoadResultDto.Ok) return result
    val options = addNewVersionOptions(nodeId, pyProject).takeIf { it.isNotEmpty() } ?: return result
    return result.copy(sections = result.sections.map { section ->
      section.copy(leaves = section.leaves.map { leaf ->
        if (leaf.ref is PyInterpreterRef.CreateEnv) leaf.copy(createVersions = options) else leaf
      })
    })
  }

  /** Python versions offered by the in-widget "add new environment" for uv (uv's list) / pip (system pythons); empty otherwise. */
  private suspend fun addNewVersionOptions(nodeId: String, pyProject: PyProject): List<EvoAddNewOptionDto> {
    val fileSystem = eelFileSystem(pyProject)
    return when (nodeId) {
      "uv" -> {
        val uvExe = UvPyTool.getInstance().resolveExecutable(fileSystem) ?: return emptyList()
        val cli = createUvCli(uvExe, fileSystem).getOrNull() ?: return emptyList()
        // Same list as the v2 dialog: filtered by the project's requires-python, newest-first. The version token is the
        // full version so the create step can pin it.
        val versions = createUvLowLevel(pyProject.baseDir, cli, fileSystem, null)
          .listSupportedPythonVersions(requiresPython(pyProject.baseDir)).getOrNull().orEmpty()
        versions.map { EvoAddNewOptionDto(title = "${it.major}.${it.minor}", token = it.toString()) }
      }
      // pip creates a venv from a base interpreter; Poetry/Hatch create their env from one — all pick a system Python.
      "pip", "Poetry", "Hatch" -> {
        val eelApi = fileSystem.eelDescriptor.toEelApi()
        // One entry per minor version, newest first; the token is the base interpreter. Only pythons that can back a
        // venv: >= 3.8 (the bundled virtualenv minimum) and within requires-python.
        val spec = PyVersionSpecifiers(requiresPython(pyProject.baseDir) ?: "")
        SystemPythonService().findSystemPythons(eelApi)
          .filter { it.pythonInfo.languageLevel.isAtLeast(LanguageLevel.PYTHON38) && spec.isValid(it.pythonInfo.languageLevel) }
          .distinctBy { it.pythonInfo.languageLevel }
          .sortedByDescending { it.pythonInfo.languageLevel }
          .map { EvoAddNewOptionDto(title = it.pythonInfo.languageLevel.toPythonVersion(), token = it.pythonBinary.pathString) }
      }
      // Conda provides the interpreter itself, so the choice is a Python version from conda's supported levels (the same
      // list the v2 dialog offers — up to 3.13, NOT filtered by the project's requires-python). The token is the
      // version string; the create step parses it back to a LanguageLevel.
      "Conda" -> condaSupportedLanguages.map { EvoAddNewOptionDto(title = it.toPythonVersion(), token = it.toPythonVersion()) }
      else -> emptyList()
    }
  }

  override suspend fun resolveInterpreterVersion(projectId: ProjectId, moduleName: String, nodeId: String, homePath: String, traceId: String): String? {
    val project = projectId.findProjectOrNull() ?: return null
    val binary = runCatching { Path.of(homePath) }.getOrNull() ?: return null
    // Detect the version in the tool's own coroutine (the same one that listed its envs), so it appears under that tool.
    val label = providers.firstOrNull { it.id == nodeId }?.label ?: nodeId
    return toolScope(project, traceId, nodeId, label).async { binary.getPythonVersion() }.await()
  }

  override suspend fun addInterpreter(projectId: ProjectId, moduleName: String, nodeId: String): EvoSelectResultDto {
    val pyProject = resolvePyProject(projectId, moduleName)
                    ?: return EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.module.not.found", moduleName))
    val presenter = PythonAddLocalInterpreterPresenter(
      moduleOrProject = ModuleOrProject.ModuleAndProject(pyProject.residesOnModule),
      errorSink = ErrorSink(),
      bestGuessCreateSdkInfo = CompletableDeferred(value = null),
    )
    return try {
      // Open the v2 Add-Interpreter dialog on the EDT, preselecting the clicked tool's manager. On OK the presenter
      // creates the SDK and associates it with the module (PythonAddEnvironment.setupSdk), and the widget refreshes
      // on the resulting rootsChanged — no explicit refresh needed here.
      withContext(Dispatchers.EDT) {
        PythonAddLocalInterpreterDialog(presenter, nodeIdToManager(nodeId)).show()
      }
      EvoSelectResultDto.Ok
    }
    catch (e: Exception) {
      LOG.warn("Failed to open Add Interpreter dialog for module '$moduleName'", e)
      EvoSelectResultDto.Error(e.message ?: e.javaClass.simpleName)
    }
  }

  override suspend fun performNodeAction(projectId: ProjectId, moduleName: String, nodeId: String, actionId: String): EvoSelectResultDto {
    val pyProject = resolvePyProject(projectId, moduleName)
                    ?: return EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.module.not.found", moduleName))
    // Only the "advanced" node (AdvancedEvoEnvironmentProvider.id) exposes backend actions today; its actionId is the
    // index into collectAddInterpreterActions.
    val index = actionId.toIntOrNull()?.takeIf { nodeId == "advanced" }
                ?: return EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.select.failed"))
    val project = pyProject.project
    val module = pyProject.residesOnModule
    val widgetScope = project.service<EvoWidgetTraceScope>().scope
    // Re-collect the same actions the node was built from and run the index-th one. Associate any SDK the action
    // creates with the module (target wizards report the new SDK via this callback; the local dialog also self-associates).
    val actions = collectAddInterpreterActions(ModuleOrProject.ModuleAndProject(module)) { sdk ->
      // setPythonSdk → ModuleRootModificationUtil.setModuleSdk does its own EDT write (invokeAndWait); calling it inside
      // a write action deadlocks, so run it plainly on a background coroutine.
      widgetScope.launch { PyModuleService.getInstance(project).setPythonSdk(module, sdk) }
    }
    val action = actions.getOrNull(index)
                 ?: return EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.select.failed"))
    return try {
      withContext(Dispatchers.EDT) { action.createDialog()?.show() }
      EvoSelectResultDto.Ok
    }
    catch (e: Exception) {
      LOG.warn("Failed to perform advanced action $index for module '$moduleName'", e)
      EvoSelectResultDto.Error(e.message ?: e.javaClass.simpleName)
    }
  }

  /** Maps an Evo tool node id (provider `id`) to the v2 dialog's environment manager to preselect; null → dialog default. */
  private fun nodeIdToManager(nodeId: String): PythonSupportedEnvironmentManagers? = when (nodeId) {
    "pip" -> PythonSupportedEnvironmentManagers.VIRTUALENV
    "Conda" -> PythonSupportedEnvironmentManagers.CONDA
    "Poetry" -> PythonSupportedEnvironmentManagers.POETRY
    "uv" -> PythonSupportedEnvironmentManagers.UV
    "Hatch" -> PythonSupportedEnvironmentManagers.HATCH
    else -> null
  }

  /** Base dirs to scan for virtualenvs — the module's own base dir for now (a settings-backed list later). */
  private fun baseDirs(pyProject: PyProject): List<Path> = listOf(pyProject.baseDir)

  /** Base dirs of *other* python modules, so discovery does not descend into inner/sibling modules. */
  private suspend fun excludedRoots(pyProject: PyProject): Set<Path> {
    val self = pyProject.baseDir
    return pyProject.project.getPyProjects().map { it.baseDir }.filterNot { it == self }.toSet()
  }

  private suspend fun eelFileSystem(pyProject: PyProject): EelFileSystem = pyProject.baseDir.toEelFileSystem()

  private suspend fun resolvePyProject(projectId: ProjectId, moduleName: String): PyProject? {
    val project = projectId.findProjectOrNull() ?: return null
    val module = ModuleManager.getInstance(project).findModuleByName(moduleName) ?: return null
    return module.asPyProject()
  }
}

/** Owns the project-level parent scope for the widget's per-tree root coroutines, so they are never unparented. */
@Service(Service.Level.PROJECT)
private class EvoWidgetTraceScope(val scope: CoroutineScope)
