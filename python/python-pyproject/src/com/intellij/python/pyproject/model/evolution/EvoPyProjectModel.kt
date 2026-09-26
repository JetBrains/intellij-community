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
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.pythonInterpreterAsync
import com.jetbrains.python.sdk.findPythonSdk
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
 * leaves a stale workspace behind.
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
 * `intellij.python.pyproject`), so an [EvoPyProjectDto] of each is pushed over RPC and the frontend resolves its
 * target against that.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class EvoPyProjectModel(private val project: Project, scope: CoroutineScope) {

  /**
   * One self-consistent view of the project's Python structure. Immutable: a recomputation publishes a new instance
   * rather than mutating this one, so a caller that resolved a target keeps working against the generation it read.
   *
   * Inner, so it reads the model's own [project] rather than holding a second reference to it, and so only the model
   * can build one. A snapshot answers for one project and for no other, and that now follows from the type.
   */
  inner class Snapshot(
    /**
     * Every workspace of this generation, in project-model order: a tool workspace once, whatever the number of its
     * members, and a standalone project as a workspace of one.
     *
     * The structure itself, because a workspace owns its projects. A caller that acts on a tool walks these; one that
     * asks about a single project reads [pyProjects], [forKey] or [forFile].
     */
    val workspaces: List<EvoWorkspace>,
    /**
     * The `PyProject` rooted at the project's own base dir, i.e. the one that makes the *project* a Python project —
     * `null` when its root belongs to no Python module. See [EvoPyProjectDto.isMain].
     */
    val main: EvoPyProject?,
  ) {
    /**
     * Every `PyProject` of this generation, one workspace after another and each workspace's root first.
     *
     * The flat view of [workspaces], for a caller that asks about a project rather than about what a tool acts on.
     * Each one states its own [EvoPyProject.key], so nothing outside has to hold a key to address one.
     *
     * A sequence, and not a list, because it is a view and not content: nothing here is held, every caller reduces it
     * in one pass, and [forKey] and [forModule] stop at the project they answer instead of flattening the rest. A
     * caller that wants one of them by key or by file asks [forKey] or [forFile] instead.
     */
    val pyProjects: Sequence<EvoPyProject> get() = workspaces.asSequence().flatMap { it.members }
    /**
     * The target [key] addresses, or `null` when this generation has no such `PyProject` — a key the frontend held
     * across a change that removed it.
     *
     * A disposed module is rejected too: recomputation follows every module change, but a removal can land between
     * one being published and this being read, and handing a disposed module to a tool provider is not a state any of
     * them are written for.
     */
    fun forKey(key: String): EvoPyProject? = pyProjects.firstOrNull { it.key == key }?.takeUnless { it.module.isDisposed }

    /**
     * The `PyProject` residing on [module], or `null` when it is not a Python module at all.
     *
     * The module is the identity [computeSnapshot] builds a generation from, so at most one project answers here.
     * Private: [forFile] is the one way in, so no caller states the rule around this on its own.
     */
    private fun forModule(module: Module): EvoPyProject? = pyProjects.firstOrNull { it.module == module }?.takeUnless { it.module.isDisposed }

    /**
     * The `PyProject` [file] belongs to:
     *
     * * a file in a Python module answers with that module's `PyProject`;
     * * a file in no module at all — a scratch, a file dragged in from outside — and no file at all, both fall back
     *   to [main], the `PyProject` rooted at the project's own base dir;
     * * a file in a module that is not Python has no target, so that a mixed project never lends an unrelated
     *   interpreter.
     *
     * The one rule every Python surface reads. The interpreter widget states it again on the frontend, over
     * [EvoPyProjectDto] and across the RPC boundary, so that copy cannot be shared. The two must stay the same: a
     * surface that answers on its own rule names an interpreter another surface does not (PY-90174).
     *
     * Suspends, unlike [forKey] and [forModule], because it alone leaves the snapshot: the module lookup reads the
     * project model under a read action.
     *
     * [ProjectFileIndex.getModuleForFile] and not [com.intellij.openapi.module.ModuleUtilCore.findModuleForFile],
     * which is the same call under [com.intellij.openapi.application.ReadAction.computeBlocking]. That blocks the
     * thread while a write action runs, and it is the wrong lock for a suspend caller.
     */
    suspend fun forFile(file: VirtualFile?): EvoPyProject? {
      if (file == null) return main
      val module = readAction { ProjectFileIndex.getInstance(project).getModuleForFile(file) } ?: return main
      return forModule(module)
    }

    /** Every `PyProject`'s own base dir — a workspace member's own, not its root's. Used to exclude sibling projects from env discovery. */
    val baseDirs: Set<Path> = pyProjects.mapTo(mutableSetOf()) { it.baseDir }

    /**
     * Every interpreter a project of this generation uses.
     *
     * The answer to "does this project use that interpreter", for a caller that would otherwise walk every module to
     * find out. Read from the same generation as everything else here, so a caller never mixes two answers.
     *
     * A set, so [PythonInterpreter] equality carries it: two wrappers of one SDK are one interpreter here.
     */
    val interpreters: Set<PythonInterpreter> = pyProjects.mapNotNullTo(mutableSetOf()) { it.interpreter }
  }

  private val state = MutableStateFlow<Snapshot?>(null)

  // Declared before `init`: the coroutines it starts read these fields. A dispatch can start one before the
  // constructor ends.
  private val interpreterState = MutableStateFlow<PythonInterpreter?>(null)

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
  suspend fun snapshot(): Snapshot = snapshotFlow().first()

  /**
   * The current structure, or `null` while the first computation is still running.
   *
   * For a caller that must answer without suspending, such as an action's `update`. Treat `null` as "not known yet"
   * rather than "nothing there": the answer arrives shortly after the project opens.
   */
  fun snapshotOrNull(): Snapshot? = state.value

  /**
   * Every structure this model publishes, the current one first.
   *
   * The one source the others read: [snapshot] takes its first element, [snapshotOrNull] the value behind it, and the
   * RPC layer its [EvoPyProjectDto] projection. Each states a different contract — await, peek, follow — over the
   * same generations.
   *
   * For a caller that must follow the structure rather than ask for it: a change of the interpreter of a module
   * arrives here, in order, after the model is recomputed. A workspace-model listener sees the change earlier, while
   * this model still holds the generation before it, so a caller that needs this model must not use one.
   */
  fun snapshotFlow(): Flow<Snapshot> = state.filterNotNull()

  /**
   * The interpreter every Python surface shows for [file], which is what [interpreter] publishes.
   *
   * Private: it states nothing that [Snapshot.forFile] does not, so a caller that wants one file's interpreter reads
   * `snapshot().forFile(file)?.interpreter` and a caller that wants the edited file's follows [interpreter].
   */
  private suspend fun interpreterFor(file: VirtualFile?): PythonInterpreter? {
    val snapshot = snapshot()
    return snapshot.forFile(file)?.interpreter
  }

  /**
   * The interpreter for the file being edited, as [interpreterFor] resolves it, recomputed whenever the structure or
   * the selected file changes.
   *
   * One flow so every surface shows one interpreter. Resolving it per surface let them disagree: the packages tool
   * window followed the file's own module and cleared itself on a file whose module carried no interpreter, while the
   * widget followed the workspace and kept showing it (PY-90174).
   */
  val interpreter: StateFlow<PythonInterpreter?> get() = interpreterState.asStateFlow()

  private fun selectedFile(): VirtualFile? = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()

  private suspend fun computeSnapshot(): Snapshot {
    val sources = project.getPyProjects()
    // One read action for the whole pass rather than one per module: each call reads the same workspace-model
    // snapshot, and taking it once also keeps the layouts consistent with each other.
    val layouts = readAction { sources.associate { it.residesOnModule to it.residesOnModule.getWorkspaceLayout() } }
    // Waits for the project model, once for the whole generation, so no reader of this snapshot waits again and none
    // reads `null` for a configured interpreter while the SDK table is still loading (PY-91871). Detection runs here
    // too: it reads the layout around the binary and caches on the SDK, so a reader never pays for it.
    val interpreters = sources.associate { it.residesOnModule to it.residesOnModule.findPythonSdk()?.pythonInterpreterAsync() }

    // Every project first, indexed by its module: a project states nothing about a workspace, and a workspace is built
    // from the projects it owns, so this pass has to come first.
    val projectsByModule = sources.associate { it.residesOnModule to EvoPyProject(it, interpreters[it.residesOnModule]) }

    /**
     * The module that identifies the workspace [module] belongs to: its workspace root, or itself when it is in no
     * workspace.
     *
     * A null layout covers a standalone project and a root-only workspace alike — a declared workspace with no members
     * yet, which `getWorkspaceLayout` cannot tell from a plain project. A workspace whose root is not itself a Python
     * module has no project to resolve directories against, so `takeIf` leaves its members standalone rather than
     * grouping them under a root that cannot answer.
     */
    fun workspaceKeyOf(module: Module): Module =
      layouts[module]?.rootModule?.takeIf { it in projectsByModule } ?: module

    /** The workspace [module] belongs to. A project in no workspace is a workspace of one, its own root. */
    fun workspaceOf(module: Module): EvoWorkspace {
      val rootModule = workspaceKeyOf(module)
      val root = projectsByModule.getValue(rootModule)
      // Only a layout whose root answers a project describes a workspace here, which is what `workspaceKeyOf` states
      // by answering that root. Anything else is a workspace of one.
      val layout = layouts[module]?.takeIf { it.rootModule == rootModule }
      // Every module of a layout is pyproject-based, so each one has a project here.
      val members = layout?.allModules?.mapNotNull { projectsByModule[it] } ?: listOf(root)
      return EvoWorkspace(root, members)
    }

    // Same spelling as the keys, so "is this the main one" is a comparison of like with like.
    val mainKey = project.basePath?.let { FileUtil.toSystemIndependentName(it) }
    // One workspace per group, built where its first member appears, so the order follows the project model.
    val workspaces = sources.map { it.residesOnModule }.distinctBy(::workspaceKeyOf).map(::workspaceOf)
    return Snapshot(workspaces, projectsByModule.values.firstOrNull { it.key == mainKey })
  }
}

/**
 * The interpreter of the module at the project root, for a file that belongs to no module, such as a scratch file.
 *
 * Replaces `ProjectRootManager.getProjectSdk()`, which reads `project-jdk-name` from `.idea/misc.xml`. That attribute
 * is missing or stale in the projects PY-89831 reports.
 *
 * Waits for the structure, which already holds the interpreter of every module, so it never answers on incomplete
 * information and it repeats no lookup.
 */
@ApiStatus.Internal
suspend fun Project.findMainPythonInterpreter(): PythonInterpreter? = service<EvoPyProjectModel>().snapshot().main?.interpreter
