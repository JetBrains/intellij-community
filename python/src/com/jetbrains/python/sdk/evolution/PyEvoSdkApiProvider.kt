@file:Suppress("UnstableApiUsage")

package com.jetbrains.python.sdk.evolution

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
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
import com.intellij.python.hatch.HatchVirtualEnvironment
import com.intellij.python.hatch.getHatchService
import com.intellij.python.hatch.resolveHatchWorkingDirectory
import com.intellij.python.pytools.resolveExecutable
import com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.discoverVenvs
import com.intellij.python.sdk.backend.evolution.getPythonVersion
import com.intellij.python.sdk.backend.evolution.resolvePythonExecutable
import com.intellij.python.uv.backend.UvPyTool
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoNodeDto
import com.intellij.python.sdk.common.evolution.EvoSelectResultDto
import com.intellij.python.sdk.common.evolution.PyEvoRegistry
import com.intellij.python.sdk.common.evolution.PyEvoSdkApi
import com.intellij.python.sdk.common.evolution.PyInterpreterDto
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.getOrNull
import com.jetbrains.python.module.PyModuleService
import com.jetbrains.python.packaging.conda.CondaPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.hatch.packaging.HatchPackageManager
import com.jetbrains.python.sdk.poetry.PoetryPackageManager
import com.jetbrains.python.sdk.uv.UvPackageManager
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import com.jetbrains.python.project.PyProject.Companion.getPyProjects
import com.jetbrains.python.project.project
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.PyRemoteSdkAdditionalDataMarker
import com.jetbrains.python.sdk.collectAddInterpreterActions
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
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.poetry.createNewPoetrySdk
import com.jetbrains.python.sdk.poetry.createPoetrySdk
import com.jetbrains.python.sdk.pyInterpreterPresentation
import com.jetbrains.python.sdk.uv.setupExistingEnvAndSdk
import com.jetbrains.python.hatch.sdk.createSdk
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

private val LOG = logger<PyEvoSdkApiProvider>()

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
          .async { provider.loadSections(pyProject, eelFileSystem(pyProject), discovered) }
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
            createSdkForCreateEnv(pyProject, fileSystem, ref.token, nodeId)
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
   * Creates the SDK for a declared-but-not-yet-materialized env (poetry per-version, hatch declared env) using the
   * tool's "create" logic, dispatched by [nodeId]. [token] is tool-specific (poetry: base Python path; hatch: env
   * name). Returns `null` on failure.
   */
  private suspend fun createSdkForCreateEnv(pyProject: PyProject, fileSystem: EelFileSystem, token: String, nodeId: String): Sdk? {
    val module = pyProject.residesOnModule
    return when (nodeId) {
      "Poetry" -> {
        val poetryExe = PoetryPyTool.getInstance().resolveExecutable(fileSystem) ?: return null
        createNewPoetrySdk(
          moduleBasePath = pyProject.baseDir,
          basePythonBinaryPath = PathHolder.Eel(Path.of(token)),
          fileSystem = fileSystem,
          poetryExecutable = poetryExe,
          installPackages = false,
          errorSink = ErrorSink(),
          inProjectEnv = false,
          targetPanelExtension = null,
        ).getOrNull()
      }
      "Hatch" -> {
        val hatchService = module.getHatchService(fileSystem).getOrNull() ?: return null
        val hatchEnv = hatchService.findVirtualEnvironments().getOrNull()
                         ?.firstOrNull { it.hatchEnvironment.name == token }?.hatchEnvironment ?: return null
        val eelApi = fileSystem.eelDescriptor.toEelApi()
        val basePython = SystemPythonService().findSystemPythons(eelApi).firstOrNull()?.pythonBinary ?: return null
        val venv = hatchService.createVirtualEnvironment(PathHolder.Eel(basePython), token).getOrNull() ?: return null
        HatchVirtualEnvironment(hatchEnv, venv).createSdk(hatchService.getWorkingDirectoryPath(), fileSystem, null).getOrNull()
      }
      else -> null
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
      widgetScope.launch { writeAction { PyModuleService.getInstance(project).setPythonSdk(module, sdk) } }
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
