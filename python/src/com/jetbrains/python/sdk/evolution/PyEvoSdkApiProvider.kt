// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage")

package com.jetbrains.python.sdk.evolution

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.util.coroutines.childScope
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.community.impl.poetry.common.POETRY_TOOL_ID
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.hatch.impl.HATCH_TOOL_ID
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.performToolInstallation
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.evolution.EvoToolContext
import com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.discoverVenvs
import com.intellij.python.sdk.backend.evolution.getPythonVersion
import com.intellij.python.sdk.common.evolution.EvoAddNewOptionDto
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.common.evolution.EvoLeafKind
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoNodeDto
import com.intellij.python.sdk.common.evolution.EvoNodeIds
import com.intellij.python.sdk.common.evolution.EvoPyProjectDto
import com.intellij.python.sdk.common.evolution.EvoSelectResultDto
import com.intellij.python.sdk.common.evolution.PyEvoRegistry
import com.intellij.python.sdk.common.evolution.PyEvoSdkApi
import com.intellij.python.sdk.common.evolution.PyInterpreterDto
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
import com.intellij.python.uv.common.UV_TOOL_ID
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.getOrNull
import com.jetbrains.python.impl.getRootModuleOrNull
import com.jetbrains.python.module.PyModuleService
import com.jetbrains.python.packaging.PyVersionSpecifiers
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.PyRemoteSdkAdditionalDataMarker
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.PythonAddLocalInterpreterDialog
import com.jetbrains.python.sdk.add.v2.PythonAddLocalInterpreterPresenter
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers
import com.jetbrains.python.sdk.add.v2.toEelFileSystem
import com.jetbrains.python.sdk.collectAddInterpreterActions
import com.jetbrains.python.sdk.configuration.CONDA_TOOL_ID
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.VENV_TOOL_ID
import com.jetbrains.python.sdk.configuration.getSdkCreator
import com.jetbrains.python.sdk.configurePythonSdk
import com.jetbrains.python.sdk.evolution.PyEvoSdkApiImpl.rootScope
import com.jetbrains.python.sdk.evolution.PyEvoSdkApiImpl.slowLoadThreshold
import com.jetbrains.python.sdk.getAssignablePythonSdks
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.isAssociatedWithModule
import com.jetbrains.python.sdk.isSdkConfigurationInProgress
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.pyInterpreterPresentation
import com.jetbrains.python.sdk.withSdkConfigurationLock
import fleet.rpc.remoteApiDescriptor
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue
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
  PyProjectToml.parseOrNull(toml)?.project?.requiresPython
}

/**
 * The system Pythons that can back a new environment under [baseDir], newest first, one entry per minor version — the
 * base-interpreter choice pip, poetry and hatch all offer, since each creates its environment *from* an existing Python
 * rather than providing one (conda does, so it does not use this).
 *
 * Filtered to what can actually work: at least 3.8, the bundled virtualenv's minimum, and within the project's
 * `requires-python`. Each option's token is the interpreter's own path, which is what the create step consumes.
 */
internal suspend fun systemPythonOptions(baseDir: Path, fileSystem: FileSystem<PathHolder.Eel>): List<EvoAddNewOptionDto> {
  // A machine-less (legacy Target) filesystem has no descriptor; fall back to the local machine, as the poetry node did.
  val eelApi = fileSystem.eelDescriptor?.toEelApi() ?: localEel
  val spec = PyVersionSpecifiers(requiresPython(baseDir) ?: "")
  return SystemPythonService().findSystemPythons(eelApi)
    .filter { it.pythonInfo.languageLevel.isAtLeast(LanguageLevel.PYTHON38) && spec.isValid(it.pythonInfo.languageLevel) }
    .distinctBy { it.pythonInfo.languageLevel }
    .sortedByDescending { it.pythonInfo.languageLevel }
    .map { EvoAddNewOptionDto(title = it.pythonInfo.languageLevel.toPythonVersion(), token = it.pythonBinary.pathString) }
}

internal class PyEvoSdkApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<PyEvoSdkApi>()) { PyEvoSdkApiImpl }
  }
}

private object PyEvoSdkApiImpl : PyEvoSdkApi {
  /**
   * The contributed providers, minus any whose id cannot address a node.
   *
   * A node id is resolved by lookup ([PyEvoEnvironmentProvider.toolId]), so two providers sharing one id would make
   * the loser unreachable — every request for that id would land on the winner — and a provider claiming a reserved
   * [EvoNodeIds] id would shadow that node instead. Both are contribution bugs, not user-visible states, so they are
   * logged and dropped here rather than left to surface as a node that silently lists the wrong tool's environments.
   */
  private val providers: List<PyEvoEnvironmentProvider>
    get() {
      val all = PyEvoEnvironmentProvider.EP_NAME.extensionList
      val seen = mutableSetOf<String>()
      return all.filter { provider ->
        val id = provider.toolId.id
        when {
          id in EvoNodeIds.RESERVED && provider.toolId != ToolId(EvoNodeIds.ADVANCED) -> {
            LOG.error("Evo provider ${provider.javaClass.name} claims reserved node id '$id'; dropped")
            false
          }
          !seen.add(id) -> {
            LOG.error("Evo provider ${provider.javaClass.name} duplicates node id '$id'; dropped")
            false
          }
          else -> true
        }
      }
    }

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
   * Cached env-list result for tools measured as slow (see [slowLoadThreshold]), keyed by `projectId|pyProjectKey|nodeId`
   * and kept for 10 min. Fast tools are not cached here — the frontend's short-lived popup-tree cache covers them. A
   * forced reload (the tool's reload icon) refills this entry.
   */
  private val envListCache: Cache<String, EvoLoadResultDto> = Caffeine.newBuilder()
    .maximumSize(100)
    .expireAfter(object : Expiry<String, EvoLoadResultDto> {
      // Read the TTL live from the registry on each write, so the flag takes effect without a restart.
      override fun expireAfterCreate(key: String, value: EvoLoadResultDto, currentTime: Long): Long = slowCacheDuration.inWholeNanoseconds
      override fun expireAfterUpdate(key: String, value: EvoLoadResultDto, currentTime: Long, currentDuration: Long): Long =
        slowCacheDuration.inWholeNanoseconds

      override fun expireAfterRead(key: String, value: EvoLoadResultDto, currentTime: Long, currentDuration: Long): Long = currentDuration
    })
    .build()

  /** Keys (`projectId|pyProjectKey|nodeId`) whose last scan was slow — they use the long-cache + reload-icon strategy. */
  private val slowTools: MutableSet<String> = ConcurrentHashMap.newKeySet()

  /** A tool whose env scan takes longer than this is treated as "slow" (long cache + reload icon). Registry-tunable. */
  private val slowLoadThreshold: kotlin.time.Duration
    get() = PyEvoRegistry.slowToolThresholdSeconds.seconds

  /** How long a slow tool's env list stays cached. Registry-tunable. */
  private val slowCacheDuration: kotlin.time.Duration
    get() = PyEvoRegistry.slowToolCacheSeconds.seconds

  override suspend fun pyProjects(projectId: ProjectId): Flow<List<EvoPyProjectDto>> =
    projectId.findProjectOrNull()?.service<EvoPyProjectModel>()?.dtos() ?: flowOf(emptyList())

  override suspend fun getCurrentInterpreter(projectId: ProjectId, pyProjectKey: String): PyInterpreterDto? {
    val pyProject = resolvePyProject(projectId, pyProjectKey) ?: return null
    val sdk = PythonSdkUtil.findPythonSdk(pyProject.module) ?: return null
    // The current-interpreter display works with Eel-based interpreters; remote/target SDKs surface only in the associated list.
    if (sdk.sdkAdditionalData is PyRemoteSdkAdditionalDataMarker) return null
    val presentation = sdk.pyInterpreterPresentation()
    // `PythonPackageManager.forSdk` reads `sdk.pySdkAdditionalData`, which throws on an SDK created without any —
    // "buggy code" per its own message. Test the precondition instead of catching: an IllegalStateException cannot be
    // caught safely here, since ProcessCanceledException is one. Such an SDK has no dependency file to offer anyway.
    val manager = if (sdk.sdkAdditionalData is PythonSdkAdditionalData) PythonPackageManager.forSdk(pyProject.project, sdk) else null
    return PyInterpreterDto(
      title = presentation.shortName,
      description = presentation.description,
      icon = presentation.icon.rpcId(),
      ref = PyInterpreterRef.ExistingSdk(sdk.name),
      dependencyFileUrl = manager?.getRootDependenciesFile()?.virtualFile?.url,
    )
  }

  override suspend fun listNodes(projectId: ProjectId, pyProjectKey: String): List<EvoNodeDto> {
    val pyProject = resolvePyProject(projectId, pyProjectKey) ?: return emptyList()
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
            .async(Dispatchers.IO) { provider.isAvailable(pyProject, fileSystem) }
        }
        .awaitAll()
      providers.filterIndexed { i, _ -> available[i] }.map { it.getNode() }
    }
    finally {
      root.cancel()
    }
  }

  override suspend fun listShortcuts(projectId: ProjectId, pyProjectKey: String): List<EvoLeafDto> {
    val pyProject = resolvePyProject(projectId, pyProjectKey) ?: return emptyList()
    val module = pyProject.module
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

  override suspend fun loadNode(
    projectId: ProjectId,
    pyProjectKey: String,
    nodeId: String,
    traceId: String,
    forceRefresh: Boolean,
  ): EvoLoadResultDto {
    val pyProject = resolvePyProject(projectId, pyProjectKey)
                    ?: return EvoLoadResultDto.Error(PySdkBundle.message("evolution.error.pyproject.not.found", pyProjectKey))
    val provider = providers.firstOrNull { it.toolId.id == nodeId }
                   ?: return EvoLoadResultDto.Error(PySdkBundle.message("evolution.error.unknown.node", nodeId))
    val cacheKey = "$projectId|$pyProjectKey|$nodeId"
    if (cacheKey in slowTools && !forceRefresh) {
      envListCache.getIfPresent(cacheKey)?.let { return it }
    }
    val discovered = discoverVenvs(baseDirs(pyProject), excludedRoots(pyProject))
    // Run (and time) the tool's env listing in the tool's own coroutine.
    val timed = measureTimedValue {
      toolScope(pyProject.project, traceId, provider.toolId.id, provider.label)
        .async {
          val fs = eelFileSystem(pyProject)
          val context = EvoToolContext(pyProject, fs, ErrorSink()) { systemPythonOptions(pyProject.baseDir, fs) }
          val loaded = provider.loadSections(pyProject, fs, discovered)
          // The node's own decorations: its add-new flow, then whatever else the tool adds (hatch's version pickers).
          provider.decorate(context, withProviderAddNewEnv(loaded, provider, context))
        }
        .await()
    }
    // Slow tools get the long cache + reload icon; fast tools fall back to the frontend's short cache.
    val slow = timed.duration > slowLoadThreshold
    if (slow) slowTools.add(cacheKey) else slowTools.remove(cacheKey)
    val result = (timed.value as? EvoLoadResultDto.Ok)?.copy(refreshable = slow) ?: timed.value
    if (slow && result is EvoLoadResultDto.Ok) envListCache.put(cacheKey, result)
    return result
  }

  override suspend fun listAssociatedInterpreters(projectId: ProjectId, pyProjectKey: String): List<PyInterpreterDto> {
    val pyProject = resolvePyProject(projectId, pyProjectKey) ?: return emptyList()
    val module = pyProject.module
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

  override suspend fun selectInterpreter(
    projectId: ProjectId,
    pyProjectKey: String,
    ref: PyInterpreterRef,
    nodeId: String,
  ): EvoSelectResultDto {
    val pyProject = resolvePyProject(projectId, pyProjectKey)
                    ?: return EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.pyproject.not.found", pyProjectKey))
    val fileSystem = eelFileSystem(pyProject)
    if (ref is PyInterpreterRef.Autoconfigure) return autoconfigureInterpreter(pyProject, fileSystem, ref.toolId)
    val homePath = when (ref) {
      // The frontend echoes back a path this backend serialized, so an unparseable one is a broken round-trip.
      is PyInterpreterRef.DetectedPath -> ref.homePath.toNioPathOrNull()
                                          ?: return EvoSelectResultDto.Error(
                                            PySdkBundle.message("evolution.error.env.not.found", ref.homePath))
      else -> null
    }
    // The whole create-or-select + apply runs under the SDK-configuration lock, which serializes concurrent
    // configuration and (via withBackgroundProgress) shows a visible task; the widget spinners on the same lock
    // (see isSdkConfigurationInProgress). The tool creators' own withProgressText steps attach to that progress.
    return withSdkConfigurationLock(pyProject.project) {
      val sdk = when (ref) {
        is PyInterpreterRef.ExistingSdk ->
          PythonSdkUtil.getAllSdks().find { it.name == ref.sdkName }
          ?: return@withSdkConfigurationLock EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.sdk.not.found", ref.sdkName))
        // Every environment row belongs to a tool node, and each provider owns both building an SDK for an existing
        // env and creating a new one — including the pip node, whose "tool-specific" answer is the generic
        // path-guessing one. A failure carries its own message (an ExecError keeps the command and its output), so it
        // is reported once here rather than by each provider.
        is PyInterpreterRef.DetectedPath ->
          selectedSdk(nodeId, pyProject, fileSystem) { provider, context -> provider.createSdkForExistingEnv(context, homePath!!) }
            .getOr { return@withSdkConfigurationLock it.error.toSelectError(pyProject.project) }
        is PyInterpreterRef.CreateEnv ->
          selectedSdk(nodeId, pyProject, fileSystem) { provider, context -> provider.createSdkForNewEnv(context, ref) }
            .getOr { return@withSdkConfigurationLock it.error.toSelectError(pyProject.project) }
        is PyInterpreterRef.Autoconfigure -> error("handled above")
      }
      pyProject.applySdk(sdk)
      EvoSelectResultDto.Ok
    }
  }

  /** Runs [build] on the provider owning [nodeId], or fails when no provider claims it (an unknown or removed tool). */
  private suspend fun selectedSdk(
    nodeId: String,
    pyProject: EvoPyProject,
    fileSystem: EelFileSystem,
    build: suspend (PyEvoEnvironmentProvider, EvoToolContext) -> PyResult<Sdk>,
  ): PyResult<Sdk> {
    val (provider, context) = toolContextFor(ToolId(nodeId), pyProject, fileSystem)
                              ?: return PyResult.localizedError(PySdkBundle.message("evolution.error.unknown.node", nodeId))
    return build(provider, context)
  }

  /**
   * Turns a failed selection into the widget's error result, and reports it to the [ErrorSink] on the way out.
   *
   * The sink is what renders an [com.jetbrains.python.errorProcessing.ExecError] as the process-execution-error dialog,
   * with the command, its exit code and its output — the only place the user can see why a tool refused. Doing it here
   * means every provider reports the same way, once.
   */
  private suspend fun PyError.toSelectError(project: Project): EvoSelectResultDto.Error {
    ErrorSink().emit(this, project)
    return EvoSelectResultDto.Error(message)
  }

  /**
   * Assigns [sdk] to every module the interpreter belongs to — the whole workspace when the module takes part in one
   * ([EvoPyProject.sdkModules]), since a uv/poetry workspace has a single shared environment and leaving the siblings
   * on their previous interpreter would make the same environment disagree with itself across the project.
   *
   * Uses `configurePythonSdk` (the setter the inspection's fix and the add-interpreter dialog use): it does the EDT
   * write, promotes the SDK to the project level when the module is the project root, and excludes the inner venv.
   * Must be called under the SDK-configuration lock.
   */
  private fun EvoPyProject.applySdk(sdk: Sdk) {
    for (module in sdkModules) configurePythonSdk(module.project, module, sdk)
  }

  /**
   * Runs the setup option [toolId] (a "Shortcuts" row) for the module — the same work the "no interpreter configured"
   * inspection's fix does. The option is re-resolved (its `sdkCreator` can't cross RPC): an existing/creatable env is
   * created and applied; a not-yet-installed tool is installed first (then the env is created in the same click if it
   * became available). Runs under the SDK-configuration lock (so the widget spinner shows and the resulting
   * `rootsChanged` refreshes it); the lock is held once here, so the tool creators must not take it themselves.
   */
  private suspend fun autoconfigureInterpreter(pyProject: EvoPyProject, fileSystem: EelFileSystem, toolId: String): EvoSelectResultDto {
    val module = pyProject.module
    return withSdkConfigurationLock(pyProject.project) {
      // TOCTOU: an SDK may have appeared since the row was listed.
      if (PythonSdkUtil.findPythonSdk(module) != null) return@withSdkConfigurationLock EvoSelectResultDto.Ok
      val option = PyProjectSdkConfigurationExtension.findAllSortedForModule(module).firstOrNull { it.toolId.id == toolId }
                   ?: return@withSdkConfigurationLock EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.select.failed"))
      when (val info = option.createSdkInfo) {
        is CreateSdkInfo.ExistingEnv, is CreateSdkInfo.WillCreateEnv ->
          if (pyProject.applyAutoconfigOption(option)) EvoSelectResultDto.Ok
          else EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.select.failed"))
        is CreateSdkInfo.WillInstallTool -> {
          // The option's tool isn't installed: install it (like the inspection's install fix), then re-resolve and,
          // if the option became creatable, create & apply the env in the same click.
          val tool = PyTool.findByPackageName(info.toolToInstall)
                     ?: return@withSdkConfigurationLock EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.select.failed"))
          val installed = tool.performToolInstallation(fileSystem.eelDescriptor.toEelApi()).getOrNull()
                          ?: return@withSdkConfigurationLock EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.select.failed"))
          info.pathPersister(installed)
          PyProjectSdkConfigurationExtension.findAllSortedForModule(module).firstOrNull { it.toolId.id == toolId }
            ?.let { pyProject.applyAutoconfigOption(it) }
          EvoSelectResultDto.Ok
        }
      }
    }
  }

  /**
   * Creates the env for a resolved setup [option] and assigns it across the workspace ([applySdk]), mirroring the
   * inspection's `setSdkUsingCreateSdkInfo`. Returns false when the option has no creator (a not-yet-installed tool) or
   * creation failed. Must be called under the SDK-configuration lock.
   */
  private suspend fun EvoPyProject.applyAutoconfigOption(option: CreateSdkInfoWithTool): Boolean {
    val sdk = when (val info = option.createSdkInfo) {
      // Both carry a creator (smart-cast to CreateSdkInfoWithSdkCreator in this branch); a not-yet-installed tool has none.
      is CreateSdkInfo.ExistingEnv, is CreateSdkInfo.WillCreateEnv -> info.getSdkCreator(module).createSdk().getOrNull()
      is CreateSdkInfo.WillInstallTool -> null
    } ?: return false
    // A tool root outside this module's own workspace (a differently-scoped tool) still gets the SDK, as before.
    module.getRootModuleOrNull(option.toolId)?.also { configurePythonSdk(it.project, it, sdk) }
    applySdk(sdk)
    return true
  }

  override suspend fun sdkConfigurationInProgress(projectId: ProjectId): Flow<Boolean> =
    projectId.findProjectOrNull()?.isSdkConfigurationInProgress ?: flowOf(false)

  /**
   * Fills each add-new section's flow from the owning [provider].
   *
   * A provider with nothing to offer for a section (no usable Python versions, say) returns `null` and the section keeps
   * the frontend's plain "add new" row.
   */
  private suspend fun withProviderAddNewEnv(result: EvoLoadResultDto, provider: PyEvoEnvironmentProvider, context: EvoToolContext): EvoLoadResultDto {
    if (result !is EvoLoadResultDto.Ok) return result
    return result.copy(sections = result.sections.map { section ->
      if (!section.addNew) section else section.copy(addNewEnv = provider.addNewEnvSpec(context, section) ?: section.addNewEnv)
    })
  }

  override suspend fun resolveInterpreterVersion(
    projectId: ProjectId,
    pyProjectKey: String,
    nodeId: String,
    homePath: String,
    traceId: String,
  ): String? {
    val project = projectId.findProjectOrNull() ?: return null
    val binary = homePath.toNioPathOrNull() ?: return null
    // Detect the version in the tool's own coroutine (the same one that listed its envs), so it appears under that tool.
    val label = providers.firstOrNull { it.toolId.id == nodeId }?.label ?: nodeId
    return toolScope(project, traceId, nodeId, label).async { binary.getPythonVersion() }.await()
  }

  override suspend fun addInterpreter(projectId: ProjectId, pyProjectKey: String, nodeId: String): EvoSelectResultDto {
    val pyProject = resolvePyProject(projectId, pyProjectKey)
                    ?: return EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.pyproject.not.found", pyProjectKey))
    val presenter = PythonAddLocalInterpreterPresenter(
      moduleOrProject = ModuleOrProject.ModuleAndProject(pyProject.module),
      errorSink = ErrorSink(),
      bestGuessCreateSdkInfo = CompletableDeferred(value = null),
    )
    // Open the v2 Add-Interpreter dialog on the EDT, preselecting the clicked tool's manager. On OK the presenter
    // creates the SDK and associates it with the module (PythonAddEnvironment.setupSdk), and the widget refreshes
    // on the resulting rootsChanged — no explicit refresh needed here.
    withContext(Dispatchers.EDT) {
      PythonAddLocalInterpreterDialog(presenter, nodeIdToManager(nodeId)).show()
    }
    return EvoSelectResultDto.Ok
  }

  override suspend fun performNodeAction(projectId: ProjectId, pyProjectKey: String, nodeId: String, actionId: String): EvoSelectResultDto {
    val pyProject = resolvePyProject(projectId, pyProjectKey)
                    ?: return EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.pyproject.not.found", pyProjectKey))
    // Only the "advanced" node (AdvancedEvoEnvironmentProvider) exposes backend actions today; its actionId is the
    // index into collectAddInterpreterActions.
    val index = actionId.toIntOrNull()?.takeIf { nodeId == EvoNodeIds.ADVANCED }
                ?: return EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.select.failed"))
    val project = pyProject.project
    val module = pyProject.module
    val widgetScope = project.service<EvoWidgetTraceScope>().scope
    // Re-collect the same actions the node was built from and run the index-th one. Associate any SDK the action
    // creates with the module — and with the rest of its workspace (target wizards report the new SDK via this
    // callback; the local dialog also self-associates).
    val actions = collectAddInterpreterActions(ModuleOrProject.ModuleAndProject(module)) { sdk ->
      // setPythonSdk → ModuleRootModificationUtil.setModuleSdk does its own EDT write (invokeAndWait); calling it inside
      // a write action deadlocks, so run it plainly on a background coroutine.
      widgetScope.launch {
        for (target in pyProject.sdkModules) PyModuleService.getInstance(project).setPythonSdk(target, sdk)
      }
    }
    val action = actions.getOrNull(index)
                 ?: return EvoSelectResultDto.Error(PySdkBundle.message("evolution.error.select.failed"))
    withContext(Dispatchers.EDT) { action.createDialog()?.show() }
    return EvoSelectResultDto.Ok
  }

  /** Maps an Evo tool node id (provider `id`) to the v2 dialog's environment manager to preselect; null → dialog default. */
  private fun nodeIdToManager(nodeId: String): PythonSupportedEnvironmentManagers? = when (ToolId(nodeId)) {
    VENV_TOOL_ID -> PythonSupportedEnvironmentManagers.VIRTUALENV
    CONDA_TOOL_ID -> PythonSupportedEnvironmentManagers.CONDA
    POETRY_TOOL_ID -> PythonSupportedEnvironmentManagers.POETRY
    UV_TOOL_ID -> PythonSupportedEnvironmentManagers.UV
    HATCH_TOOL_ID -> PythonSupportedEnvironmentManagers.HATCH
    else -> null
  }

  /**
   * Base dirs to scan for virtualenvs — the widget's working dir, i.e. the workspace root's for a member (a
   * settings-backed list later). A nested member's own directory is still reached by the walk, since [excludedRoots]
   * keeps it out of the exclusions.
   */
  private fun baseDirs(pyProject: EvoPyProject): List<Path> = listOf(pyProject.baseDir)

  /** Base dirs of *other* python modules, so discovery does not descend into inner/sibling modules. */
  private suspend fun excludedRoots(pyProject: EvoPyProject): Set<Path> {
    // Ours are the module's own dir and the workspace root we scan from — neither may exclude itself from the walk.
    val own = setOf(pyProject.moduleBaseDir, pyProject.baseDir)
    return pyProject.project.service<EvoPyProjectModel>().snapshot().baseDirs - own
  }

  private suspend fun eelFileSystem(pyProject: EvoPyProject): EelFileSystem = pyProject.baseDir.toEelFileSystem()

  /**
   * The provider owning [toolId], paired with the context its tool-owned operations run in.
   *
   * `null` when no provider claims the id — an unknown or no-longer-available tool, which every caller treats as "no
   * tool logic for this", falling back to the central arm or a generic SDK.
   */
  private fun toolContextFor(toolId: ToolId, pyProject: EvoPyProject, fileSystem: EelFileSystem): Pair<PyEvoEnvironmentProvider, EvoToolContext>? {
    val provider = providers.firstOrNull { it.toolId == toolId } ?: return null
    return provider to EvoToolContext(pyProject, fileSystem, ErrorSink()) { systemPythonOptions(pyProject.baseDir, fileSystem) }
  }

  /**
   * The target [pyProjectKey] addresses, already resolved against its workspace: a member of a uv/poetry workspace
   * carries the root's [PyProject] along, so every directory the widget works in is the workspace root's (see
   * [EvoPyProject]).
   *
   * A lookup, not a computation — [EvoPyProjectModel] holds the whole structure per project-model generation, so this
   * costs nothing per call even though every entry point starts here.
   */
  private suspend fun resolvePyProject(projectId: ProjectId, pyProjectKey: String): EvoPyProject? =
    projectId.findProjectOrNull()?.service<EvoPyProjectModel>()?.resolve(pyProjectKey)
}

/** Owns the project-level parent scope for the widget's per-tree root coroutines, so they are never unparented. */
@Service(Service.Level.PROJECT)
private class EvoWidgetTraceScope(val scope: CoroutineScope)
