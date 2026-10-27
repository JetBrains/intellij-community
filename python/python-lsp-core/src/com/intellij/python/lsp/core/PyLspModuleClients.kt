// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineUtils
import com.intellij.python.lsp.core.utils.PyLspToolVersionTracker
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.pyproject.model.evolution.getInterpreter
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.detectedEnvironment
import com.intellij.util.messages.Topic
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private val MODULE_CLIENTS_LOG: Logger = logger<PyLspToolDescriptor>()

/**
 * The Python projects one server can answer for in [snapshot], in a stable order.
 *
 * A Python project is served when it has a native, writable interpreter, see [PyTypeEngineUtils.isServedInterpreter],
 * because the tool runs against the interpreter. A Python project always has a content root, its
 * [PyProject.baseDir], and that root becomes the workspace folder. The order comes from the base directory, not from
 * the module name, so renaming a module keeps it.
 *
 * The answer reads the immutable snapshot only, so it takes no lock, and it changes exactly when a new snapshot lands.
 */
@ApiStatus.Internal
fun pyLspServedProjects(snapshot: EvoPyProjectModel.Snapshot): List<PyProject> =
  snapshot.evoPyProjects
    .filter { evoPyProject -> evoPyProject.interpreter?.let(PyTypeEngineUtils::isServedInterpreter) == true }
    .map { it.pyProject }
    .filterNot { it.residesOnModule.isDisposed }
    .sortedBy { it.baseDir.toString() }
    .toList()

/** [pyLspServedProjects] of the current snapshot. Waits for the first snapshot. */
@ApiStatus.Internal
suspend fun pyLspServedProjects(project: Project): List<PyProject> =
  pyLspServedProjects(EvoPyProjectModel.getInstance(project).snapshot())

/**
 * The Python projects one server of [pyTool] should hold when it starts for [pyProject].
 *
 * A tool whose server keeps one workspace for each folder holds every served Python project that runs
 * the same binary, so one server answers for the whole project in the common case. Only such a tool
 * calls this, see [PyLspToolIntegrationProvider.servesEveryModule]. The first entry of the list is the
 * primary Python project.
 *
 * One server runs one binary, see [PyLspToolDescriptor.executableCandidates], so a Python project whose
 * environment pins another version of [pyTool] needs a server of its own. The Python projects therefore
 * split by the installed version of [pyTool], see [pyLspToolVersionOf]. A Python project whose
 * environment holds no copy of [pyTool] states no version, so it joins the version of the first served
 * one that does, and every one of them lands in exactly one group.
 *
 * The list never comes back empty. [pyProject] alone answers for a project of one served Python project,
 * and for a Python project no server may serve. The second case does not arrive through the type engine
 * or the tool, because both check the interpreter before they build a descriptor. It keeps the promise
 * for any other caller.
 */
@ApiStatus.Internal
fun pyLspProjectsToServeWith(pyProject: PyProject, pyTool: PyTool): List<PyProject> {
  // It runs under the read lock of `fileOpened`, so it cannot wait. Before the first snapshot, nothing is known to be
  // served, and the Python project serves alone. The first snapshot regroups the servers, see PY_LSP_SERVE_KEYS_CHANGED.
  val served = EvoPyProjectModel.getInstance(pyProject.project).snapshotOrNull()?.let(::pyLspServedProjects) ?: emptyList()
  val keys = pyLspServeKeys(pyProject.project, pyTool)
  return pyLspServeGroupOf(pyProject, served) { keys.getByModule(it) ?: pyLspServeKeyWithoutVersion(it) }
}

/**
 * The last known [PyLspServeKey] of every served Python project of [project] for [pyTool].
 *
 * The answer never blocks and never reads an interpreter, because the type evaluation path asks for
 * it under a read lock. `TypeEvalContextImpl` builds a type engine in its constructor, and the
 * factory builds a context before it looks in its cache, so this runs for every `TypeEvalContext`.
 * Reading the installed packages here would call [PythonPackageManager.forPythonInterpreter], which creates a
 * manager and does first-touch I/O while the caller holds the read lock.
 *
 * A Python project the snapshot does not name yet states no version, see [pyLspServeKeyWithoutVersion], so
 * every Python project of a workspace shares one server until the snapshot arrives. A refresh that changes the
 * keys fires [PY_LSP_SERVE_KEYS_CHANGED], and each provider then checks its running servers again.
 */
@ApiStatus.Internal
fun pyLspServeKeys(project: Project, pyTool: PyTool): Map<PyProject, PyLspServeKey> =
  pyLspServeKeysView(project, pyTool).keys

/**
 * The [PyLspServeKey] of every served Python project of [project] for [pyTool], recomputed when it is stale.
 *
 * For a caller that must decide on a fresh answer, such as the folder set check after a package
 * event. It reads the interpreters, so it takes no read lock of its own around that part.
 */
@ApiStatus.Internal
suspend fun pyLspRefreshServeKeys(project: Project, pyTool: PyTool): Map<PyProject, PyLspServeKey> =
  project.service<PyLspServeKeyCache>().refreshedKeys(pyTool)

/**
 * The last known serve keys of a tool, and whether they still fit the project.
 *
 * [isFresh] is `false` for a cold snapshot and for a stale one. A stale snapshot can name every
 * module and still miss that one of them just got the tool.
 */
@ApiStatus.Internal
class PyLspServeKeysView(val keys: Map<PyProject, PyLspServeKey>, val isFresh: Boolean) {
  /** The key of [pyProject], matched by module, see [isSamePyProject]. `null` when the keys do not name it. */
  fun keyOf(pyProject: PyProject): PyLspServeKey? = keys.getByModule(pyProject)
}

/**
 * Whether [this] and [other] are one Python project: they reside on the same module.
 *
 * A [PyProject] is equal to another only with the same base dir. A module that gets one more content root can get
 * another base dir, so a [PyProject] that a running server keeps from its start is not equal to the one of a later
 * snapshot. Every check that compares a kept [PyProject] with the current ones matches by module.
 */
@ApiStatus.Internal
fun PyProject.isSamePyProject(other: PyProject): Boolean = residesOnModule == other.residesOnModule

/** Whether [this] holds [pyProject], matched by module, see [isSamePyProject]. */
@ApiStatus.Internal
fun Collection<PyProject>.holds(pyProject: PyProject): Boolean = any { it.isSamePyProject(pyProject) }

/** The value of [pyProject], matched by module, see [isSamePyProject]. */
@ApiStatus.Internal
fun <V> Map<PyProject, V>.getByModule(pyProject: PyProject): V? =
  get(pyProject) ?: entries.firstOrNull { it.key.isSamePyProject(pyProject) }?.value

/** [pyLspServeKeys] together with whether the answer still fits the project. Never blocks. */
@ApiStatus.Internal
fun pyLspServeKeysView(project: Project, pyTool: PyTool): PyLspServeKeysView =
  project.service<PyLspServeKeyCache>().view(pyTool)

/**
 * Hears that the serve keys of a tool changed.
 *
 * A running server was built from the keys it saw then, so it can hold the wrong group now. A
 * refresh lands without any event of the project, so only this tells a provider to check again.
 */
@ApiStatus.Internal
fun interface PyLspServeKeysListener {
  fun serveKeysChanged(toolName: String)
}

@ApiStatus.Internal
@Topic.ProjectLevel
val PY_LSP_SERVE_KEYS_CHANGED: Topic<PyLspServeKeysListener> =
  Topic(PyLspServeKeysListener::class.java, Topic.BroadcastDirection.NONE)

/**
 * The last computed serve keys of each tool, with the stamp of the project state they rest on.
 *
 * The keys rest on the Python projects, their content roots and their interpreters, which one
 * [EvoPyProjectModel] generation states, and on the installed tool versions. So the generation and the
 * version counter make the stamp. Reading the keys is cheap; recomputing them is not, and it cannot
 * happen under a read lock.
 */
@Service(Service.Level.PROJECT)
private class PyLspServeKeyCache(private val project: Project, private val cs: CoroutineScope) {
  private data class Stamp(val structure: Long, val versions: Long) {
    /** Whether the state behind this stamp is at least as new as the state behind [other]. Every counter only grows. */
    fun isAtLeast(other: Stamp): Boolean = structure >= other.structure && versions >= other.versions
  }

  private class Snapshot(val stamp: Stamp?, val keys: Map<PyProject, PyLspServeKey>)

  private val perTool = ConcurrentHashMap<String, AtomicReference<Snapshot>>()
  /** The tools with a refresh in flight, so a hot read path does not queue one refresh per call. */
  private val refreshing: MutableSet<String> = ConcurrentHashMap.newKeySet()
  /** Every tool that asked for its keys, so a new [EvoPyProjectModel] generation can refresh them. */
  private val tools = ConcurrentHashMap<String, PyTool>()

  /**
   * The count of [EvoPyProjectModel] generations. A Python project that arrives or leaves, a new content root and a
   * new interpreter each land in a new generation.
   */
  private val structureGeneration = AtomicLong()

  fun view(pyTool: PyTool): PyLspServeKeysView {
    val toolName = pyTool.packageName.name
    tools.putIfAbsent(toolName, pyTool)
    val snapshot = snapshotRefOf(toolName).get()
    val fresh = snapshot.stamp == stampOf(toolName)
    if (!fresh && refreshing.add(toolName)) {
      cs.launch {
        try {
          refreshedKeys(pyTool)
        }
        finally {
          refreshing.remove(toolName)
        }
      }
    }
    return PyLspServeKeysView(snapshot.keys, fresh)
  }

  suspend fun refreshedKeys(pyTool: PyTool): Map<PyProject, PyLspServeKey> {
    val toolName = pyTool.packageName.name
    tools.putIfAbsent(toolName, pyTool)
    val ref = snapshotRefOf(toolName)
    val stamp = stampOf(toolName)
    ref.get().let { if (it.stamp?.isAtLeast(stamp) == true) return it.keys }

    val computed = computeKeys(pyTool)
    // A refresh that began earlier and ends later must not replace a newer snapshot. It would bring
    // back a grouping the project left, and the type engine would start a server for it again.
    var previous: Snapshot? = null
    var replaced = false
    val stored = ref.updateAndGet { current ->
      previous = current
      replaced = current.stamp?.isAtLeast(stamp) != true
      if (replaced) Snapshot(stamp, computed) else current
    }
    if (replaced && previous?.keys != computed) {
      MODULE_CLIENTS_LOG.debug("The serve keys of '$toolName' changed: $computed")
      project.messageBus.syncPublisher(PY_LSP_SERVE_KEYS_CHANGED).serveKeysChanged(toolName)
    }
    return stored.keys
  }

  init {
    // Each generation refreshes the keys of every tool that asked for them, even when nothing reads them then. So a
    // Python project that leaves drops out of the keys, and a refresh that changes them tells the providers to group
    // again.
    cs.launch {
      EvoPyProjectModel.getInstance(project).snapshotFlow().collect {
        structureGeneration.incrementAndGet()
        for (pyTool in tools.values) {
          cs.launch { refreshedKeys(pyTool) }
        }
      }
    }
  }

  private fun snapshotRefOf(toolName: String): AtomicReference<Snapshot> =
    perTool.computeIfAbsent(toolName) { AtomicReference(Snapshot(null, emptyMap())) }

  private fun stampOf(toolName: String): Stamp = Stamp(
    structureGeneration.get(),
    PyLspToolVersionTracker.getInstance(project).counterOf(toolName),
  )

  /**
   * The project model comes from a read action, and the installed version of each interpreter from
   * outside it. [PythonPackageManager.forPythonInterpreter] creates a manager on the first call for an
   * interpreter, so it must not run under the read lock.
   *
   * `forPythonInterpreter` throws `AlreadyDisposedException` for an interpreter or a project that went away while
   * this ran. That exception is a cancellation, so it ends this refresh and the snapshot keeps its
   * last answer. The stamp does not move, so the next read schedules another refresh.
   */
  private suspend fun computeKeys(pyTool: PyTool): Map<PyProject, PyLspServeKey> {
    // One generation for every Python project, so all the keys of one refresh agree.
    val snapshot = EvoPyProjectModel.getInstance(project).snapshot()
    val model = readAction {
      pyLspServedProjects(snapshot).map { pyProject -> pyProject to pyLspWorkspaceRootOf(pyProject) }
    }
    return model.associate { (pyProject, workspaceRoot) ->
      val interpreter = snapshot.forPyProject(pyProject)?.interpreter
      val version = interpreter?.let { pyLspToolVersionOf(it, project, pyTool) }
      pyProject to PyLspServeKey(workspaceRoot, version, interpreter?.let { pyLspServeInterpreterOf(it, pyTool) })
    }
  }
}

/**
 * What decides which server a module belongs to.
 *
 * Two modules share a server only when they share a workspace and run the same binary. For a tool that
 * sets [PyLspTool.serverNeedsOneInterpreter], they must also share the [interpreter]. Every other tool
 * leaves [interpreter] `null`, so the interpreter does not split its modules.
 */
@ApiStatus.Internal
data class PyLspServeKey(val workspaceRoot: String?, val toolVersion: String?, val interpreter: String? = null)

/**
 * The [PyLspServeKey.interpreter] of [interpreter] for [pyTool], or `null` when [pyTool] does not group
 * its modules by interpreter.
 */
private fun pyLspServeInterpreterOf(interpreter: PythonInterpreter, pyTool: PyTool): String? =
  if ((pyTool as? PyLspTool<*>)?.serverNeedsOneInterpreter == true) interpreter.detectedEnvironment?.pythonBinaryPath?.toString() else null

/**
 * The interpreter of [pyProject], from the current snapshot. `null` when [pyProject] has no interpreter. For a tool
 * module that does not depend on the Python project structure.
 */
@ApiStatus.Internal
suspend fun pyLspInterpreterOf(pyProject: PyProject): PythonInterpreter? = pyProject.getInterpreter()

/**
 * The [PyLspServeKey] of [pyProject] for [pyTool], with the interpreter from the current snapshot.
 *
 * [pyLspServeKeys] answers the same question for every served Python project at once.
 */
@ApiStatus.Internal
suspend fun pyLspServeKeyOf(pyProject: PyProject, pyTool: PyTool): PyLspServeKey {
  val workspaceRoot = readAction { pyLspWorkspaceRootOf(pyProject) }
  val interpreter = pyProject.getInterpreter()
  val version = interpreter?.let { pyLspToolVersionOf(it, pyProject.project, pyTool) }
  return PyLspServeKey(workspaceRoot, version, interpreter?.let { pyLspServeInterpreterOf(it, pyTool) })
}

/**
 * The [PyLspServeKey] of a Python project the serve-key snapshot does not name yet.
 *
 * It states the workspace, which the project model alone decides, and no version and no interpreter.
 * A Python project without a version joins the version of its own workspace, and one without an
 * interpreter joins its interpreter, see [pyLspServeGroupOf]. So an unknown Python project lands with
 * the rest of its workspace instead of getting a server of its own.
 */
@ApiStatus.Internal
fun pyLspServeKeyWithoutVersion(pyProject: PyProject): PyLspServeKey = PyLspServeKey(pyLspWorkspaceRootOf(pyProject), null)

/**
 * The Python projects of [live] that may provide the binary of one server, in the order to ask them.
 *
 * One server runs one binary, and the group formed on one version. A Python project without the package
 * falls through to `PATH` and to `uvx`, which can answer with a version no Python project pinned.
 * - A fresh [view] answers with the Python projects that hold the package. With no holder it answers with
 *   the primary one alone, because `PATH` and `uvx` answer the same for every Python project, and each
 *   probe runs under the read lock of `fileOpened`.
 * - A cold or stale [view] cannot say which Python project holds the package. One that just got the tool
 *   is not in it yet, and no later event retries the start. So every Python project stays a candidate,
 *   and the known holders come first.
 */
@ApiStatus.Internal
fun pyLspExecutableCandidates(live: List<PyProject>, view: PyLspServeKeysView): List<PyProject> {
  val holders = live.filter { view.keyOf(it)?.toolVersion != null }
  if (!view.isFresh) return holders + live.filterNot { holders.holds(it) }
  return when {
    holders.isNotEmpty() -> holders
    live.all { view.keyOf(it) != null } -> live.take(1)
    else -> live
  }
}

/**
 * The group of [served] that holds [pyProject], where [keyOf] states the key of a Python project.
 *
 * The rule alone, so a test can state it without a project on disk and without an environment. Every
 * module of [served] lands in exactly one group, and the group keeps the order of [served].
 *
 * The workspace decides first, then the [PyLspServeKey.interpreter], then the version. A module without
 * its own copy of the tool states no version, so it takes the version of the first module of **its
 * own** group that states one. A global fallback would give a module of an attached project the
 * version of the main project. A module whose interpreter is not known yet takes the interpreter of the
 * first module of its workspace in the same way.
 */
@VisibleForTesting
@ApiStatus.Internal
fun pyLspServeGroupOf(pyProject: PyProject, served: List<PyProject>, keyOf: (PyProject) -> PyLspServeKey): List<PyProject> {
  // Match by module: [pyProject] can come from a server that started before the base dir moved.
  val current = served.firstOrNull { it.isSamePyProject(pyProject) }
  if (served.size <= 1 || current == null) return listOf(pyProject)

  val keys = served.associateWith(keyOf)
  val mine = keys.getValue(current)
  val sameWorkspace = served.filter { keys.getValue(it).workspaceRoot == mine.workspaceRoot }
  val sameInterpreter = sameWorkspace.withFallback(current) { keys.getValue(it).interpreter }
  return sameInterpreter.withFallback(current) { keys.getValue(it).toolVersion }
}

/**
 * The Python projects of [this] whose [value] equals the one of [pyProject]. One without a value takes the value of
 * the first one that has one, so every Python project lands in exactly one group.
 */
private fun List<PyProject>.withFallback(pyProject: PyProject, value: (PyProject) -> String?): List<PyProject> {
  val fallback = firstNotNullOfOrNull(value)
  val wanted = value(pyProject) ?: fallback
  return filter { (value(it) ?: fallback) == wanted }
}

/**
 * The workspace [module] belongs to, as a path, or `null` when it has no content root.
 *
 * A module whose content root shares a tree with the project directory belongs to the project's own
 * workspace. That holds for a root inside the project directory and for a root that contains it.
 * Both must give the same answer, or the two servers would hold folders that nest, and one tree
 * would be analysed twice.
 *
 * Every other module is a workspace of its own. `Attach to project` loads a second project as a
 * module of this one, see `ModuleAttachProcessor`, and the content root of that module is the other
 * project's directory. One server for both would hold two unrelated trees, and every attach and
 * detach would change its folder set and restart it for every module. A plain module with a content
 * root outside the project directory takes the same answer, so it gets a server of its own. That
 * costs a process, and it never puts one folder in two servers.
 *
 * The project directory is the same test the platform makes to find the primary module, see
 * `PrimaryModuleManager`.
 */
@ApiStatus.Internal
fun pyLspWorkspaceRootOf(module: Module): String? {
  val roots = ModuleRootManager.getInstance(module).contentRoots
  val projectPath = module.project.basePath
  if (projectPath != null && roots.any { sharesTreeWith(projectPath, it.path) }) return projectPath
  return roots.minOfOrNull { it.path }
}

/** The workspace of the module of [pyProject], see [pyLspWorkspaceRootOf]. */
@ApiStatus.Internal
fun pyLspWorkspaceRootOf(pyProject: PyProject): String? = pyLspWorkspaceRootOf(pyProject.residesOnModule)

/** Whether one of the two paths contains the other, or they are the same path. */
private fun sharesTreeWith(one: String, other: String): Boolean =
  FileUtil.isAncestor(one, other, false) || FileUtil.isAncestor(other, one, false)

/**
 * The version of [pyTool] installed in the environment of [interpreter], or `null` when it holds none.
 *
 * The snapshot needs no process and does not block. It reads an empty list until the package cache
 * of the environment is seeded, so every interpreter states no version at first. `LspPackageListener`
 * restarts the clients once that changes.
 */
@ApiStatus.Internal
fun pyLspToolVersionOf(interpreter: PythonInterpreter, project: Project, pyTool: PyTool): String? =
  PythonPackageManager.forPythonInterpreter(project, interpreter).getInstalledToolPackage(pyTool)?.version

/**
 * The content roots of [pyProjects], with no duplicate, ordered by path.
 *
 * The platform builds the server identity from the root paths in order, so the order must not depend
 * on the order of [pyProjects]. Otherwise renaming a module would start a second server for the same
 * set of folders.
 */
@ApiStatus.Internal
fun pyLspContentRootsOf(pyProjects: List<PyProject>): Array<VirtualFile> =
  pyProjects.map { it.residesOnModule }.filterNot { it.isDisposed }
    .flatMap { ModuleRootManager.getInstance(it).contentRoots.asList() }
    .distinctBy { it.path }
    .sortedBy { it.path }
    .toTypedArray()

/**
 * The content roots inside the folders of [served] that belong to a module [served] does not hold,
 * ordered by path.
 *
 * A workspace folder holds its whole tree, so this server also analyses a module nested in it, with
 * the interpreter of the outer folder. The IDE never sends a file of that module to this server, see
 * [PyLspToolDescriptor.isSupportedFile], so that analysis only costs. It happens when the nested
 * module has a server of its own, because it pins another tool version, and when no server serves it.
 *
 * A root that contains a folder of [served] is left out. Excluding it would also exclude that
 * folder, and a tool cannot include a file again once a glob excludes it.
 */
@ApiStatus.Internal
fun pyLspForeignNestedRoots(served: List<PyProject>, all: List<Module>): List<String> {
  val folders = pyLspContentRootsOf(served).map { it.path }
  val servedModules = served.mapTo(HashSet()) { it.residesOnModule }
  return all.asSequence()
    .filter { it !in servedModules && !it.isDisposed }
    .flatMap { ModuleRootManager.getInstance(it).contentRoots.asSequence() }
    .map { it.path }
    .filter { root -> folders.any { folder -> FileUtil.isAncestor(folder, root, true) } }
    .filter { root -> folders.none { folder -> FileUtil.isAncestor(root, folder, false) } }
    .distinct()
    .sorted()
    .toList()
}

/**
 * Whether a running server holds a folder set the project no longer wants.
 *
 * Each descriptor is compared with the folder set of its own group, see [pyLspServeGroupOf], because
 * a project can want more than one server. Comparing every descriptor with the folders of every
 * served module would call a correctly grouped server stale and restart it on each roots change.
 *
 * The group comes from a served module of the descriptor, and not from its primary module. The
 * primary module can leave the served set while the rest of the group stays, and the server then
 * holds a folder the project dropped. Asking the primary module alone would call that server fresh.
 *
 * The check compares the modules as well as the folders. A module can leave the project while
 * another one arrives at the same directory. The folders then match, and the platform builds the
 * server identity from them, so nothing would replace the server. It would still hold the old module
 * and refuse every file of the new one.
 *
 * A descriptor that serves nothing any more is not stale, because no folder set would suit it. See
 * [pyLspServesNothing], which says to stop it instead.
 */
@ApiStatus.Internal
fun pyLspFolderSetIsStale(
  descriptors: Collection<PyLspToolDescriptor>,
  served: List<PyProject>,
  keyOf: (PyProject) -> PyLspServeKey,
): Boolean =
  descriptors.any { descriptor ->
    val stillServed = descriptor.servedProjects.firstOrNull { served.holds(it) } ?: return@any false
    val group = pyLspServeGroupOf(stillServed, served, keyOf)
    // The current Python projects, not the kept ones: a moved base dir is a change of the folder set.
    descriptor.servedProjects.toSet() != group.toSet() ||
    descriptor.roots.mapTo(HashSet()) { it.path } != pyLspContentRootsOf(group).mapTo(HashSet()) { it.path }
  }

/**
 * Whether the project serves no module of [descriptor] any more.
 *
 * Such a server answers for nobody. It holds folders the project dropped, and its descriptor keeps a
 * strong reference to every module it was built with, so a disposed module stays reachable while the
 * server runs. The caller stops it.
 *
 * A server of one live module that [served] leaves out is not such a server. [pyLspProjectsToServeWith]
 * gives that module a server of its own, for example for a module without a local interpreter. A stop
 * would start the same server again, and the group check on its start would stop it again.
 */
@ApiStatus.Internal
fun pyLspServesNothing(descriptor: PyLspToolDescriptor, served: List<PyProject>): Boolean {
  if (descriptor.servedProjects.any { served.holds(it) }) return false
  val alone = descriptor.servedProjects.singleOrNull() ?: return true
  return alone.residesOnModule.isDisposed
}

/** The Python projects the server behind [this] answers for. Empty when the client is not a Python LSP tool. */
@get:ApiStatus.Internal
val LspClient.pyServedProjects: List<PyProject>
  get() = (descriptor as? PyLspToolDescriptor)?.servedProjects.orEmpty()

/**
 * The client of this collection whose server answers for [pyProject], or `null` when none does.
 *
 * A server answers for the Python projects of its own descriptor. One server can hold several, so do not
 * compare against the primary one alone. A client that answers nothing is skipped, see [isUsable].
 */
@ApiStatus.Internal
fun Collection<LspClient>.clientFor(pyProject: PyProject): LspClient? =
  firstOrNull { it.isUsable && it.pyServedProjects.holds(pyProject) }

/**
 * Whether [this] answers a request now.
 *
 * The platform answers `null` to a request sent before the server runs, see
 * `LspRequestExecutorBase.doSendRequestAsync`, so a client that still initializes answers nothing. A
 * type engine behind it would store `PyNullType` for every element. A shut-down server stays in the
 * client list, and it answers nothing either.
 */
@get:ApiStatus.Internal
val LspClient.isUsable: Boolean
  get() = state == LspServerState.Running

/**
 * [descriptor], wired to the provider of [providerClass].
 *
 * The type engine and the tool both start a server from a descriptor, and each one must reach the
 * provider that owns it, see [PyLspToolIntegrationProvider.attach].
 */
@ApiStatus.Internal
fun <T : PyLspToolIntegrationProvider> pyLspAttachedDescriptor(
  descriptor: PyLspToolDescriptor,
  providerClass: Class<T>,
): PyLspToolDescriptor {
  LspIntegrationProvider.EP_NAME.findFirstSafe { providerClass.isInstance(it) }
    ?.let { (it as PyLspToolIntegrationProvider).attach(descriptor) }
  return descriptor
}

/**
 * The running [providerClass] client that answers for [pyProject], or `null` when none does. The debug
 * log names the modules that each client serves, and the state of that client.
 *
 * A module gets no client when the tool cannot drive its interpreter. The caller then leaves the
 * external type engine off for that module, and PyCharm infers the types itself.
 */
@ApiStatus.Internal
fun findLspClientFor(pyProject: PyProject, providerClass: Class<out LspIntegrationProvider>): LspClient? {
  val clients = LspClientManager.getInstance(pyProject.project).getClients(providerClass)
  val client = clients.clientFor(pyProject)
  if (client == null && MODULE_CLIENTS_LOG.isDebugEnabled) {
    val served = clients.joinToString { client ->
      val modules = client.pyServedProjects.joinToString("+") { p -> p.residesOnModule.name }.ifEmpty { "<not a python tool>" }
      "$modules (${client.state})"
    }
    MODULE_CLIENTS_LOG.debug("No running ${providerClass.simpleName} client serves module '${pyProject.residesOnModule.name}'. The clients serve: [$served]")
  }
  return client
}
