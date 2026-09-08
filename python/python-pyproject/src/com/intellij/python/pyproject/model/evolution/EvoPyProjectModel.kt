// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.evolution

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorManagerListener.FILE_EDITOR_MANAGER
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.asInterpreterRef
import com.jetbrains.python.sdk.findPythonSdk
import com.jetbrains.python.sdk.pythonSdk
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.python.pyproject.model.internal.workspaceBridge.affectsWorkspaceLayout
import com.intellij.python.pyproject.model.internal.workspaceBridge.getWorkspaceLayout
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.evolution.EvoWorkspace
import com.intellij.python.sdk.common.evolution.EvoPyProjectDto
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.PyProject.Companion.getPyProjects
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

/**
 * The entities [PyProject] is derived from ([com.intellij.python.pyproject.model.internal.pyProject.PyProjectImpl]):
 * the module (for its type), its facets (for a Python facet on a module of another type) and its content roots (for
 * the base dir). A change to any of them can add, remove or move a `PyProject`, or repoint one at another interpreter,
 * which a module holds among its dependencies; a change to anything else cannot.
 */
private val PY_PROJECT_ENTITIES: List<Class<out WorkspaceEntity>> =
  listOf(ModuleEntity::class.java, FacetEntity::class.java, ContentRootEntity::class.java)

/**
 * Whether this change can alter anything [EvoPyProjectModel.computeSnapshot] reads: the entities a [PyProject] is
 * derived from, or the workspace membership the clusters are built from. The latter moves entirely on its own — a member
 * dropped from `[tool.uv.workspace]` and added back changes no module and no content root — so watching only the former
 * leaves a stale cluster behind.
 */
private fun VersionedStorageChange.affectsPyProjects(): Boolean =
  PY_PROJECT_ENTITIES.any { getChanges(it).isNotEmpty() } || affectsWorkspaceLayout()

/**
 * The project's Python structure — every [PyProject], which one is the *main* one, and how they cluster into tool
 * workspaces — computed once and recomputed only when the workspace model actually changes it.
 *
 * It exists because the widget asks the same three questions on every single RPC, and answering them from scratch each
 * time is expensive: [getPyProjects] awaits a JPS↔workspace-model synchronization, and
 * [getWorkspaceLayout] scans every module entity to find a workspace's members. Both are facts about the *project*, not
 * about the target of any one call, so they are computed per project-model generation instead of per call.
 *
 * It is also the frontend's only source of this knowledge: `PyProject` is backend-only (its service lives in
 * `intellij.python.pyproject`), so [dtos] is pushed over RPC and the frontend resolves its target against that.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class EvoPyProjectModel(private val project: Project, scope: CoroutineScope) {

  /**
   * One self-consistent view of the project's Python structure. Immutable: a recomputation publishes a new instance
   * rather than mutating this one, so a caller that resolved a target keeps working against the generation it read.
   */
  class Snapshot(
    private val byKey: Map<String, EvoPyProject>,
    /**
     * The `PyProject` rooted at the project's own base dir, i.e. the one that makes the *project* a Python project —
     * `null` when its root belongs to no Python module. See [EvoPyProjectDto.isMain].
     */
    val main: EvoPyProject?,
    /** Wire projection of [byKey], in project-model order. */
    val dtos: List<EvoPyProjectDto>,
  ) {
    /**
     * The target [key] addresses, or `null` when this generation has no such `PyProject` — a key the frontend held
     * across a change that removed it.
     *
     * A disposed module is rejected too: recomputation follows every module change, but a removal can land between
     * one being published and this being read, and handing a disposed module to a tool provider is not a state any of
     * them are written for.
     */
    fun resolve(key: String): EvoPyProject? = byKey[key]?.takeUnless { it.module.isDisposed }

    /** By the module a `PyProject` resides on, which [computeSnapshot] already treats as its identity. */
    private val byModule: Map<Module, EvoPyProject> = byKey.values.associateBy { it.module }

    /**
     * The `PyProject` residing on [module], or `null` when it is not a Python module at all.
     */
    fun forModule(module: Module): EvoPyProject? = byModule[module]?.takeUnless { it.module.isDisposed }

    /** Every `PyProject`'s own base dir — a workspace member's own, not its root's. Used to exclude sibling projects from env discovery. */
    val baseDirs: Set<Path> get() = byKey.values.mapTo(mutableSetOf()) { it.moduleBaseDir }

  }

  private val state = MutableStateFlow<Snapshot?>(null)

  // Declared before `init`: the coroutines it starts read these fields. A dispatch can start one before the
  // constructor ends.
  private val interpreterState = MutableStateFlow<Sdk?>(null)

  private val selectionChanges = MutableStateFlow(0)

  init {
    scope.launch {
      // Compute up front, then on every workspace-model change that can alter the PyProject set. Conflated: a
      // recomputation reads the whole model from scratch, so an intermediate generation is worth nothing.
      WorkspaceModel.getInstance(project).eventLog
        .filter { it.affectsPyProjects() }
        .map { }
        .onStart { emit(Unit) }
        .conflate()
        .collect { state.value = computeSnapshot() }
    }

    project.messageBus.connect(scope).subscribe(FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun selectionChanged(event: FileEditorManagerEvent) {
        selectionChanges.value++
      }
    })

    scope.launch {
      // Either input moving means the answer can have changed: a different file is being edited, or the structure it
      // is resolved against was recomputed.
      combine(state.filterNotNull(), selectionChanges) { _, _ -> }
        .conflate()
        .collect { interpreterState.value = interpreterFor(selectedFile()) }
    }

  }

  /** The current structure, awaiting the first computation when it has not landed yet. */
  suspend fun snapshot(): Snapshot = state.filterNotNull().first()

  /**
   * The current structure, or `null` while the first computation is still running.
   *
   * For a caller that must answer without suspending, such as an action's `update`. Treat `null` as "not known yet"
   * rather than "nothing there": the answer arrives shortly after the project opens.
   */
  fun snapshotOrNull(): Snapshot? = state.value

  /** See [Snapshot.resolve]. */
  suspend fun resolve(key: String): EvoPyProject? = snapshot().resolve(key)

  /**
   * The `PyProject` [file] belongs to, or the project's own when it belongs to none — a scratch, a file dragged in
   * from outside, or nothing focused at all. See [EvoPyProjectDto.isMain].
   */
  internal suspend fun targetFor(file: VirtualFile?): EvoPyProject? {
    val snapshot = snapshot()
    val module = file?.let { readAction { ModuleUtilCore.findModuleForFile(it, project) } } ?: return snapshot.main
    return snapshot.forModule(module) ?: snapshot.main
  }

  /**
   * The interpreter every Python surface shows for [file]: the one the workspace declares when [file]'s `PyProject`
   * takes part in one, and the one its own module carries otherwise.
   *
   * Nothing where [file] resolves to no `PyProject`. That is the same nothing the interpreter widget shows, and the
   * two surfaces state one interpreter, so neither invents one the other does not have.
   */
  suspend fun interpreterFor(file: VirtualFile?): Sdk? {
    val target = targetFor(file) ?: return null
    return readAction { target.sdk }
  }

  /**
   * The interpreter for the file being edited, as [interpreterFor] resolves it, recomputed whenever the structure or
   * the selected file changes.
   *
   * One flow so every surface shows one interpreter. Resolving it per surface let them disagree: the packages tool
   * window followed the file's own module and cleared itself on a file whose module carried no interpreter, while the
   * widget followed the workspace and kept showing it (PY-90174).
   */
  val interpreter: StateFlow<Sdk?> get() = interpreterState.asStateFlow()

  private fun selectedFile(): VirtualFile? = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()

  /** The pushed structure, re-emitted on every recomputation — the backing flow of [com.intellij.python.sdk.common.evolution.PyEvoSdkApi.pyProjects]. */
  fun dtos(): Flow<List<EvoPyProjectDto>> = state.filterNotNull().map { it.dtos }

  private suspend fun computeSnapshot(): Snapshot {
    val pyProjects = project.getPyProjects()
    val byModule = pyProjects.associateBy { it.residesOnModule }
    // One read action for the whole pass rather than one per module: each call reads the same workspace-model
    // snapshot, and taking it once also keeps the layouts consistent with each other.
    val layouts = readAction { pyProjects.associate { it.residesOnModule to it.residesOnModule.getWorkspaceLayout() } }
    // Read with the layouts, from the same workspace-model snapshot, so a DTO never pairs one generation's structure
    // with another's interpreters.
    val interpreterRefs = readAction { pyProjects.associate { it.residesOnModule to it.residesOnModule.pythonSdk?.asInterpreterRef() } }

    // Built once per cluster and shared by its members, so the whole workspace is one object rather than one per
    // member — which is what makes "is this the same workspace" answerable by identity downstream.
    val workspacesByRoot = mutableMapOf<Module, EvoWorkspace>()
    fun workspaceOf(pyProject: PyProject): EvoWorkspace? {
      // Null layout also covers a root-only workspace (a declared workspace with no members yet), which
      // getWorkspaceLayout cannot distinguish from a plain project — such a project stays standalone, as before.
      val layout = layouts[pyProject.residesOnModule] ?: return null
      // A workspace whose root is not itself a Python module has no PyProject to resolve directories against, so the
      // member is treated as standalone rather than dropped.
      val root = byModule[layout.rootModule] ?: return null
      return workspacesByRoot.getOrPut(layout.rootModule) { EvoWorkspace(root, layout.tool, layout.allModules) }
    }

    // Same spelling as the keys, so "is this the main one" is a comparison of like with like.
    val mainKey = project.basePath?.let { FileUtil.toSystemIndependentName(it) }
    // Keyed rather than listed, and the DTOs derived from the map, so the two can never disagree about what exists.
    val byKey = pyProjects.associateBy({ keyOf(it) }, { EvoPyProject(it, workspaceOf(it)) })
    val dtos = byKey.map { (key, target) ->
      EvoPyProjectDto(
        key = key,
        name = target.module.name,
        isMain = key == mainKey,
        workspaceRootKey = target.workspace?.let { keyOf(it.root) },
        interpreterRef = interpreterRefs[target.module],
      )
    }
    return Snapshot(byKey, mainKey?.let { byKey[it] }, dtos)
  }
}

/**
 * A `PyProject`'s wire identity: its base dir, system-independent.
 *
 * System-independent because the frontend matches it against a content root's
 * [com.intellij.openapi.vfs.VirtualFile.getPath], which is already in that form — so the comparison is plain string
 * equality on both sides, with no path parsing and no VFS lookup.
 */
internal fun keyOf(pyProject: PyProject): String = FileUtil.toSystemIndependentName(pyProject.baseDir.toString())

/**
 * The interpreter of the module at the project root, for a file that belongs to no module, such as a scratch file.
 *
 * Replaces `ProjectRootManager.getProjectSdk()`, which reads `project-jdk-name` from `.idea/misc.xml`. That attribute
 * is missing or stale in the projects PY-89831 reports.
 *
 * Waits for the structure and for the project model behind the module's own SDK reference, so it never answers on
 * incomplete information.
 */
@ApiStatus.Internal
suspend fun Project.findMainPythonSdk(): Sdk? = service<EvoPyProjectModel>().snapshot().main?.module?.findPythonSdk()
