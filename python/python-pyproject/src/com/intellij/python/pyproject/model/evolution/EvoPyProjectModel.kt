// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.evolution

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorManagerListener.FILE_EDITOR_MANAGER
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.python.externalIndex.PyExternalFilesIndexService
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.isFor
import com.intellij.python.sdk.backend.pythonInterpreterAsync
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntity
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.entities
import com.intellij.python.pyproject.model.internal.workspaceBridge.affectsWorkspaceLayout
import com.intellij.python.pyproject.model.internal.workspaceBridge.getWorkspaceLayout
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.evolution.EvoWorkspace
import com.intellij.python.sdk.common.evolution.EvoPyProjectDto
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.PyProject.Companion.getPyProjects
import com.intellij.python.pyproject.model.internal.pyProject.getPyProjectsWithStorage
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import com.jetbrains.python.project.project
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

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
  PY_PROJECT_ENTITIES.any { getChanges(it).isNotEmpty() } || changesInheritedInterpreter() || affectsWorkspaceLayout()

/**
 * Whether this change gives a module that inherits its interpreter another one.
 *
 * Such a module has an [InheritedSdkDependency], and [sdkReferenceOf] reads its interpreter from
 * [ProjectSettingsEntity]. Only that field counts, and only while a module inherits it. So a change of any other
 * setting, or a project where no module inherits, publishes no snapshot.
 */
private fun VersionedStorageChange.changesInheritedInterpreter(): Boolean =
  getChanges(ProjectSettingsEntity::class.java).any { it.oldEntity?.projectSdk != it.newEntity?.projectSdk } &&
  storageAfter.entities<ModuleEntity>().any { module -> module.dependencies.any { it is InheritedSdkDependency } }

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
     * asks about a single project reads [evoPyProjects], [forKey] or [forFile].
     */
    val workspaces: List<EvoWorkspace>,
    /**
     * The `PyProject` rooted at the project's own base dir, i.e. the one that makes the *project* a Python project —
     * `null` when its root belongs to no Python module. See [EvoPyProjectDto.isMain].
     */
    val main: EvoPyProject?,
    /**
     * The SDK names the modules refer to, also names that the SDK table does not hold.
     * A change of one of these SDKs in the SDK table starts a recompute.
     */
    val sdkReferences: Set<String>,
  ) {
    /**
     * Every `PyProject` of this generation, one workspace after another and each workspace's root first.
     *
     * The flat view of [workspaces], for a caller that asks about a project rather than about what a tool acts on.
     * Each one states its own [EvoPyProject.key], so nothing outside has to hold a key to address one.
     *
     * A sequence, and not a list, because it is a view and not content: nothing here is held, every caller reduces it
     * in one pass, and [forKey] and [forPyProject] stop at the project they answer instead of flattening the rest. A
     * caller that wants one of them by key or by file asks [forKey] or [forFile] instead.
     */
    val evoPyProjects: Sequence<EvoPyProject> get() = workspaces.asSequence().flatMap { it.members }
    /**
     * The target [key] addresses, or `null` when this generation has no such `PyProject` — a key the frontend held
     * across a change that removed it.
     *
     * A disposed module is rejected too: recomputation follows every module change, but a removal can land between
     * one being published and this being read, and handing a disposed module to a tool provider is not a state any of
     * them are written for.
     */
    fun forKey(key: String): EvoPyProject? = evoPyProjects.firstOrNull { it.key == key }?.takeUnless { it.pyProject.residesOnModule.isDisposed }

    /**
     * The entry of this generation for [pyProject], or `null` when this generation does not hold it.
     *
     * A disposed module is rejected, as [forKey] rejects one.
     *
     * For a caller that asks about several projects and wants every answer from one generation. A caller that asks
     * about one project calls [getInterpreter] instead.
     */
    fun forPyProject(pyProject: PyProject): EvoPyProject? =
      evoPyProjects.firstOrNull { it.pyProject == pyProject }?.takeUnless { it.pyProject.residesOnModule.isDisposed }

    /**
     * The `PyProject` of [file]:
     * * a file in a Python module gets the project of that module;
     * * a file in no module gets [main] if [mainForOrphans] is set, or if the file is in the external files index;
     * * a file in a non-Python module gets `null`.
     *
     * Clear [mainForOrphans] in code that changes the file, such as an inspection or a quick fix.
     * The frontend widget has a copy of this rule. Keep the two the same (PY-90174).
     */
    @RequiresReadLock
    fun forFile(file: VirtualFile, mainForOrphans: Boolean = true): EvoPyProject? {
      val module = ProjectFileIndex.getInstance(project).getModuleForFile(file)
      if (module != null) return forModule(module)
      val fallsBackToMain = mainForOrphans || project.service<PyExternalFilesIndexService>().isFileAddedToNonProjectIndex(file)
      return main.takeIf { fallsBackToMain }
    }

    private fun forModule(module: Module): EvoPyProject? =
      evoPyProjects.firstOrNull { it.pyProject.residesOnModule == module }?.takeUnless { module.isDisposed }

    /** Every `PyProject`'s own base dir — a workspace member's own, not its root's. Used to exclude sibling projects from env discovery. */
    val baseDirs: Set<Path> = evoPyProjects.mapTo(mutableSetOf()) { it.pyProject.baseDir }

    /**
     * Every interpreter a project of this generation uses.
     *
     * The answer to "does this project use that interpreter", for a caller that would otherwise walk every module to
     * find out. Read from the same generation as everything else here, so a caller never mixes two answers.
     *
     * A set, so [PythonInterpreter] equality carries it: two wrappers of one SDK are one interpreter here.
     * A name that the SDK table does not hold is only in [sdkReferences].
     */
    val interpreters: Set<PythonInterpreter> = evoPyProjects.mapNotNullTo(mutableSetOf()) { it.interpreter }
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
      merge(
        WorkspaceModel.getInstance(project).eventLog.filter { it.affectsPyProjects() }.map { },
        sdkTableChanges(),
      )
        .onStart { emit(Unit) }
        .conflate()
        .collect {
          val snapshot = computeSnapshot()
          state.value = snapshot
          // An inspection can have read the previous snapshot. Run it again on the new one. A project without Python
          // projects has nothing that reads it.
          if (snapshot.workspaces.isNotEmpty()) {
            DaemonCodeAnalyzer.getInstance(project).restart("Python project structure changed")
          }
        }
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
        .collect {
          val file = selectedFile()
          interpreterState.value = if (file == null) project.findMainPythonInterpreter() else project.findPythonInterpreter(file)
        }
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
   * The interpreter for the file being edited, as [findPythonInterpreter] resolves it, recomputed whenever the structure or
   * the selected file changes.
   *
   * One flow so every surface shows one interpreter. Resolving it per surface let them disagree: the packages tool
   * window followed the file's own module and cleared itself on a file whose module carried no interpreter, while the
   * widget followed the workspace and kept showing it (PY-90174).
   */
  val interpreter: StateFlow<PythonInterpreter?> get() = interpreterState.asStateFlow()

  private fun selectedFile(): VirtualFile? = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()

  /**
   * An event for each SDK table change of an SDK in [Snapshot.sdkReferences].
   * The project event log does not report the SDK table. A change before the first snapshot is ignored.
   */
  /** The Python SDK named [name] in the SDK table, or `null`. A broken SDK without Python data gets `null` too. */
  private fun findPythonSdk(name: String): Sdk? =
    ProjectJdkTable.getInstance().findJdk(name)?.takeIf { PythonSdkUtil.isPythonSdk(it) && it.sdkAdditionalData is PythonSdkAdditionalData }

  private fun sdkTableChanges(): Flow<Unit> = callbackFlow {
    fun onChange(vararg names: String) {
      val references = state.value?.sdkReferences ?: return
      if (names.any { it in references }) trySend(Unit)
    }

    val connection = ApplicationManager.getApplication().messageBus.connect(this)
    connection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, object : ProjectJdkTable.Listener {
      override fun jdkAdded(jdk: Sdk) = onChange(jdk.name)

      override fun jdkRemoved(jdk: Sdk) = onChange(jdk.name)

      override fun jdkNameChanged(jdk: Sdk, previousName: String) = onChange(jdk.name, previousName)
    })
    awaitClose { connection.disconnect() }
  }

  private suspend fun computeSnapshot(): Snapshot {
    // Read all module data from this one storage. It does not change, so a module removal cannot break the pass.
    val (sources, storage) = project.getPyProjectsWithStorage()
    val layouts = sources.associate { it.residesOnModule to it.residesOnModule.getWorkspaceLayout(storage) }
    val references = sources.associate { pyProject ->
      pyProject.residesOnModule to pyProject.residesOnModule.findModuleEntity(storage)?.let { sdkReferenceOf(it, storage) }
    }
    val interpreters = references.mapValues { (_, name) -> name?.let(::findPythonSdk)?.pythonInterpreterAsync() }
    val sdkReferences = references.values.filterNotNullTo(mutableSetOf())

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
    return Snapshot(workspaces, projectsByModule.values.firstOrNull { it.key == mainKey }, sdkReferences)
  }

  /**
   * Suspends until a snapshot states the interpreter each of [pyProjects] holds now.
   *
   * For a function that sets an interpreter: it calls this after the write and before it returns. The model recomputes
   * only when the workspace model reports the write, and that report arrives later. So without this wait, a caller
   * that reads the snapshot right after such a function still sees the interpreter from before the write.
   *
   * Returns at once when the current snapshot already agrees.
   */
  suspend fun awaitInterpreterOf(pyProjects: Collection<PyProject>) {
    // The SDK each module refers to now, read the same way as the snapshot reads it.
    val (_, storage) = project.getPyProjectsWithStorage()
    val expected = pyProjects.associateWith { pyProject ->
      pyProject.residesOnModule.findModuleEntity(storage)?.let { sdkReferenceOf(it, storage) }?.let(::findPythonSdk)
    }
    snapshotFlow().first { snapshot ->
      expected.all { (pyProject, sdk) ->
        val interpreter = snapshot.forPyProject(pyProject)?.interpreter
        if (sdk == null) interpreter == null else interpreter?.isFor(sdk) == true
      }
    }
  }

  /** For tests: suspends until the snapshot holds the current SDK of every Python module. Call it before highlighting. */
  @TestOnly
  suspend fun awaitCurrentInterpreters() {
    awaitInterpreterOf(project.getPyProjects())
  }

  companion object {
    fun getInstance(project: Project): EvoPyProjectModel = project.service()
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
suspend fun Project.findMainPythonInterpreter(): PythonInterpreter? = findMainEvoPyProject()?.interpreter

/** The Python project at the project base dir. Waits for the first snapshot. See [EvoPyProjectModel.Snapshot.main]. */
@ApiStatus.Internal
suspend fun Project.findMainEvoPyProject(): EvoPyProject? = EvoPyProjectModel.getInstance(this).snapshot().main

/** [findMainEvoPyProject] without the wait. `null` before the first snapshot. */
@ApiStatus.Internal
fun Project.findMainEvoPyProjectIfReady(): EvoPyProject? = EvoPyProjectModel.getInstance(this).snapshotOrNull()?.main

/**
 * Every Python project of the current snapshot, with its interpreter. See [EvoPyProjectModel.Snapshot.evoPyProjects].
 *
 * [PyProject.Companion.getPyProjects] lists the same projects without their interpreters.
 */
@ApiStatus.Internal
suspend fun Project.evoPyProjects(): Sequence<EvoPyProject> = EvoPyProjectModel.getInstance(this).snapshot().evoPyProjects

/**
 * Every interpreter a project of the current snapshot uses. See [EvoPyProjectModel.Snapshot.interpreters].
 */
@ApiStatus.Internal
suspend fun Project.pythonInterpreters(): Set<PythonInterpreter> = EvoPyProjectModel.getInstance(this).snapshot().interpreters

/** [pythonInterpreters] without the wait. `null` before the first snapshot. */
@ApiStatus.Internal
fun Project.pythonInterpretersIfReady(): Set<PythonInterpreter>? = EvoPyProjectModel.getInstance(this).snapshotOrNull()?.interpreters

/**
 * The Python project every Python surface shows for [file], by the rule of [EvoPyProjectModel.Snapshot.forFile].
 * See there for [mainForOrphans].
 */
@ApiStatus.Internal
suspend fun Project.findEvoPyProject(file: VirtualFile, mainForOrphans: Boolean = true): EvoPyProject? {
  val snapshot = EvoPyProjectModel.getInstance(this).snapshot()
  return readAction { snapshot.forFile(file, mainForOrphans) }
}

/**
 * The interpreter the widget and the packages tool window show: the one of the file being edited.
 * See [EvoPyProjectModel.interpreter].
 *
 * `null` before the first structure lands, and when that file has no interpreter.
 */
@ApiStatus.Internal
fun Project.currentPythonInterpreter(): PythonInterpreter? = EvoPyProjectModel.getInstance(this).interpreter.value

/**
 * The base dir of every Python project of the current snapshot. See [EvoPyProjectModel.Snapshot.baseDirs].
 */
@ApiStatus.Internal
suspend fun Project.pythonProjectBaseDirs(): Set<Path> = EvoPyProjectModel.getInstance(this).snapshot().baseDirs

/**
 * The Python project of [this] element, by [EvoPyProjectModel.Snapshot.forFile]. `null` before the first snapshot.
 * For code that cannot suspend, such as an inspection. Each new snapshot restarts the analysis.
 */
@ApiStatus.Internal
@RequiresReadLock
fun PsiElement.findEvoPyProjectIfReady(mainForOrphans: Boolean = true): EvoPyProject? {
  val snapshot = EvoPyProjectModel.getInstance(project).snapshotOrNull() ?: return null
  val file = containingFile?.originalFile?.virtualFile ?: return snapshot.main.takeIf { mainForOrphans }
  return snapshot.forFile(file, mainForOrphans)
}

/**
 * The interpreter every Python surface shows for [file], by the rule of [EvoPyProjectModel.Snapshot.forFile].
 * See there for [mainForOrphans].
 *
 * A caller that wants the interpreter of the file being edited follows [EvoPyProjectModel.interpreter] instead.
 */
@ApiStatus.Internal
suspend fun Project.findPythonInterpreter(file: VirtualFile, mainForOrphans: Boolean = true): PythonInterpreter? =
  findEvoPyProject(file, mainForOrphans)?.interpreter

/**
 * The interpreter this project uses, as the current [EvoPyProjectModel.Snapshot] states it.
 *
 * Waits for the first snapshot. A function that sets an interpreter calls [EvoPyProjectModel.awaitInterpreterOf]
 * before it returns, so a caller that reads this after such a function sees the new interpreter.
 */
@ApiStatus.Internal
suspend fun PyProject.getInterpreter(): PythonInterpreter? =
  EvoPyProjectModel.getInstance(project).snapshot().forPyProject(this)?.interpreter
