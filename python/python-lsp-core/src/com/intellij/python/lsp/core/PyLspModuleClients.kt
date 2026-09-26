// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineUtils
import com.intellij.python.lsp.core.utils.PyLspToolVersionTracker
import com.intellij.python.pytools.backend.PyTool
import com.intellij.util.messages.Topic
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

private val MODULE_CLIENTS_LOG: Logger = logger<PyLspToolDescriptor>()

private val SERVED_MODULES_KEY = Key.create<CachedValue<List<Module>>>("py.lsp.served.modules")

/**
 * The modules one server can answer for, in a stable order.
 *
 * A module needs a local, non-read-only interpreter, because the tool runs against the interpreter,
 * and at least one content root, because a root is what becomes a workspace folder. The order comes
 * from the content roots, not from the module name, so renaming a module keeps it.
 *
 * The answer is cached until the project roots change. A caller on the type evaluation path asks for
 * it often, and each answer reads the SDK of every module. The root manager moves its counter before
 * it fires `rootsChanged`, so a `rootsChanged` listener reads a fresh answer.
 */
@ApiStatus.Internal
fun pyLspServedModules(project: Project): List<Module> =
  CachedValuesManager.getManager(project).getCachedValue(project, SERVED_MODULES_KEY, {
    CachedValueProvider.Result.create(
      computePyLspServedModules(project),
      ProjectRootModificationTracker.getInstance(project),
    )
  }, false)

/** [pyLspServedModules] without the cache. */
@ApiStatus.Internal
fun computePyLspServedModules(project: Project): List<Module> =
  project.modules
    .filterNot { it.isDisposed }
    .mapNotNull { module -> firstRootPathOf(module)?.let { module to it } }
    .filter { (module, _) -> PyTypeEngineUtils.isLocalNonReadOnlySdk(module) }
    .sortedBy { (_, rootPath) -> rootPath }
    .map { (module, _) -> module }

/** The lowest content root path of [module], or `null` when it has no content root. */
private fun firstRootPathOf(module: Module): String? =
  ModuleRootManager.getInstance(module).contentRoots.minOfOrNull { it.path }

/**
 * The modules one server of [pyTool] should hold when it starts for [module].
 *
 * A tool whose server keeps one workspace for each folder holds every served module that runs the
 * same binary, so one server answers for the whole project in the common case. Only such a tool
 * calls this, see [PyLspToolIntegrationProvider.servesEveryModule]. The first module of the list is
 * the primary module.
 *
 * One server runs one binary, see [PyLspToolDescriptor.executableCandidates], so a module whose
 * environment pins another version of [pyTool] needs a server of its own. The modules therefore
 * split by the installed version of [pyTool], see [pyLspToolVersionOf]. A module whose environment
 * holds no copy of [pyTool] states no version, so it joins the version of the first served module
 * that does, and every one of them lands in exactly one group.
 *
 * The list never comes back empty. [module] alone answers for a project of one served module, and
 * for a module no server may serve. The second case does not arrive through the type engine or the
 * tool, because both check the interpreter of the module before they build a descriptor. It keeps
 * the promise for any other caller.
 */
@ApiStatus.Internal
fun pyLspModulesToServeWith(module: Module, pyTool: PyTool): List<Module> {
  val keys = pyLspServeKeys(module.project, pyTool)
  return pyLspServeGroupOf(module, pyLspServedModules(module.project)) { keys[it] ?: pyLspServeKeyWithoutVersion(it) }
}

/**
 * The last known [PyLspServeKey] of every served module of [project] for [pyTool].
 *
 * The answer never blocks and never reads an interpreter, because the type evaluation path asks for
 * it under a read lock. `TypeEvalContextImpl` builds a type engine in its constructor, and the
 * factory builds a context before it looks in its cache, so this runs for every `TypeEvalContext`.
 * Reading the installed packages here would call [PythonPackageManager.forSdk], which creates a
 * manager and does first-touch I/O while the caller holds the read lock.
 *
 * A module the snapshot does not name yet states no version, see [pyLspServeKeyWithoutVersion], so
 * every module of a workspace shares one server until the snapshot arrives. A refresh that changes the
 * keys fires [PY_LSP_SERVE_KEYS_CHANGED], and each provider then checks its running servers again.
 */
@ApiStatus.Internal
fun pyLspServeKeys(project: Project, pyTool: PyTool): Map<Module, PyLspServeKey> =
  pyLspServeKeysView(project, pyTool).keys

/**
 * The [PyLspServeKey] of every served module of [project] for [pyTool], recomputed when it is stale.
 *
 * For a caller that must decide on a fresh answer, such as the folder set check after a package
 * event. It reads the interpreters, so it takes no read lock of its own around that part.
 */
@ApiStatus.Internal
suspend fun pyLspRefreshServeKeys(project: Project, pyTool: PyTool): Map<Module, PyLspServeKey> =
  project.service<PyLspServeKeyCache>().refreshedKeys(pyTool)

/**
 * The last known serve keys of a tool, and whether they still fit the project.
 *
 * [isFresh] is `false` for a cold snapshot and for a stale one. A stale snapshot can name every
 * module and still miss that one of them just got the tool.
 */
@ApiStatus.Internal
class PyLspServeKeysView(val keys: Map<Module, PyLspServeKey>, val isFresh: Boolean)

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
 * The keys rest on the project roots and on the installed tool versions, so both trackers make the
 * stamp. Reading them is cheap; recomputing them is not, and it cannot happen under a read lock.
 */
@Service(Service.Level.PROJECT)
private class PyLspServeKeyCache(private val project: Project, private val cs: CoroutineScope) {
  private data class Stamp(val roots: Long, val versions: Long) {
    /** Whether the state behind this stamp is at least as new as the state behind [other]. Both counters only grow. */
    fun isAtLeast(other: Stamp): Boolean = roots >= other.roots && versions >= other.versions
  }

  private class Snapshot(val stamp: Stamp?, val keys: Map<Module, PyLspServeKey>)

  private val perTool = ConcurrentHashMap<String, AtomicReference<Snapshot>>()
  /** The tools with a refresh in flight, so a hot read path does not queue one refresh per call. */
  private val refreshing: MutableSet<String> = ConcurrentHashMap.newKeySet()

  fun view(pyTool: PyTool): PyLspServeKeysView {
    val toolName = pyTool.packageName.name
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

  suspend fun refreshedKeys(pyTool: PyTool): Map<Module, PyLspServeKey> {
    val toolName = pyTool.packageName.name
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
    // A snapshot names each served module, so it would keep a removed module until the next read
    // refreshes it. That read may never come, for example once the user turns the tool off.
    project.messageBus.connect(cs).subscribe(ModuleListener.TOPIC, object : ModuleListener {
      override fun moduleRemoved(project: Project, module: Module) {
        dropModule(module)
      }
    })
  }

  /** Removes [module] from every snapshot. The snapshot then counts as stale, so the next read refreshes it. */
  private fun dropModule(module: Module) {
    for (ref in perTool.values) {
      ref.updateAndGet { current -> if (module !in current.keys) current else Snapshot(null, current.keys - module) }
    }
  }

  private fun snapshotRefOf(toolName: String): AtomicReference<Snapshot> =
    perTool.computeIfAbsent(toolName) { AtomicReference(Snapshot(null, emptyMap())) }

  private fun stampOf(toolName: String): Stamp = Stamp(
    ProjectRootModificationTracker.getInstance(project).modificationCount,
    PyLspToolVersionTracker.getInstance(project).counterOf(toolName),
  )

  /**
   * The project model comes from a read action, and the installed version of each interpreter from
   * outside it. [PythonPackageManager.forSdk] creates a manager on the first call for an
   * interpreter, so it must not run under the read lock.
   *
   * `forSdk` throws `AlreadyDisposedException` for an interpreter or a project that went away while
   * this ran. That exception is a cancellation, so it ends this refresh and the snapshot keeps its
   * last answer. The stamp does not move, so the next read schedules another refresh.
   */
  private suspend fun computeKeys(pyTool: PyTool): Map<Module, PyLspServeKey> {
    val model = readAction {
      pyLspServedModules(project)
        .filterNot { it.isDisposed }
        .map { module -> Triple(module, pyLspWorkspaceRootOf(module), module.pythonSdk) }
    }
    return model.associate { (module, workspaceRoot, sdk) ->
      module to PyLspServeKey(workspaceRoot, sdk?.let { pyLspToolVersionOf(it, project, pyTool) })
    }
  }
}

/**
 * What decides which server a module belongs to.
 *
 * Two modules share a server only when they share a workspace and run the same binary.
 */
@ApiStatus.Internal
data class PyLspServeKey(val workspaceRoot: String?, val toolVersion: String?)

/**
 * The [PyLspServeKey] of [module] for [pyTool]. Reads the interpreter, so never call it under a read
 * lock. [pyLspServeKeys] answers the same question from a snapshot for a caller that holds one.
 */
@ApiStatus.Internal
fun pyLspServeKeyOf(module: Module, pyTool: PyTool): PyLspServeKey =
  PyLspServeKey(pyLspWorkspaceRootOf(module), pyLspToolVersionOf(module, pyTool))

/**
 * The [PyLspServeKey] of a module the serve-key snapshot does not name yet.
 *
 * It states the workspace, which the project model alone decides, and no version. A module without a
 * version joins the version of its own workspace, see [pyLspServeGroupOf], so an unknown module
 * lands with the rest of its workspace instead of getting a server of its own.
 */
@ApiStatus.Internal
fun pyLspServeKeyWithoutVersion(module: Module): PyLspServeKey = PyLspServeKey(pyLspWorkspaceRootOf(module), null)

/**
 * The modules of [live] that may provide the binary of one server, in the order to ask them.
 *
 * One server runs one binary, and the group formed on one version. A module without the package
 * falls through to `PATH` and to `uvx`, which can answer with a version no module pinned.
 * - A fresh [view] answers with the modules that hold the package. With no holder it answers with the
 *   primary module alone, because `PATH` and `uvx` answer the same for every module, and each probe
 *   runs under the read lock of `fileOpened`.
 * - A cold or stale [view] cannot say which module holds the package. A module that just got the tool
 *   is not in it yet, and no later event retries the start. So every module stays a candidate, and
 *   the known holders come first.
 */
@ApiStatus.Internal
fun pyLspExecutableCandidates(live: List<Module>, view: PyLspServeKeysView): List<Module> {
  val holders = live.filter { view.keys[it]?.toolVersion != null }
  if (!view.isFresh) return holders + live.filterNot { it in holders }
  return when {
    holders.isNotEmpty() -> holders
    live.all { it in view.keys } -> live.take(1)
    else -> live
  }
}

/**
 * The group of [served] that holds [module], where [keyOf] states the key of a module.
 *
 * The rule alone, so a test can state it without a project on disk and without an environment. Every
 * module of [served] lands in exactly one group, and the group keeps the order of [served].
 *
 * The workspace decides first. A module without its own copy of the tool states no version, so it
 * takes the version of the first module of **its own** workspace that states one. A global fallback
 * would give a module of an attached project the version of the main project.
 */
@VisibleForTesting
@ApiStatus.Internal
fun pyLspServeGroupOf(module: Module, served: List<Module>, keyOf: (Module) -> PyLspServeKey): List<Module> {
  if (served.size <= 1 || module !in served) return listOf(module)

  val keys = served.associateWith(keyOf)
  val mine = keys.getValue(module)
  val sameWorkspace = served.filter { keys.getValue(it).workspaceRoot == mine.workspaceRoot }
  // The version a module without its own copy of the tool runs.
  val fallback = sameWorkspace.firstNotNullOfOrNull { keys.getValue(it).toolVersion }
  val wanted = mine.toolVersion ?: fallback
  return sameWorkspace.filter { (keys.getValue(it).toolVersion ?: fallback) == wanted }
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

/** Whether one of the two paths contains the other, or they are the same path. */
private fun sharesTreeWith(one: String, other: String): Boolean =
  FileUtil.isAncestor(one, other, false) || FileUtil.isAncestor(other, one, false)

/**
 * The version of [pyTool] installed in [module]'s environment, or `null` when it holds none.
 *
 * The snapshot needs no process and does not block. It reads an empty list until the package cache
 * of the environment is seeded, so every module states no version at first. `LspPackageListener`
 * restarts the clients once that changes.
 */
@ApiStatus.Internal
fun pyLspToolVersionOf(module: Module, pyTool: PyTool): String? {
  val sdk = module.pythonSdk ?: return null
  return pyLspToolVersionOf(sdk, module.project, pyTool)
}

/** [pyLspToolVersionOf] for an interpreter that no module has to own. */
@ApiStatus.Internal
fun pyLspToolVersionOf(sdk: Sdk, project: Project, pyTool: PyTool): String? =
  PythonPackageManager.forSdk(project, sdk).getInstalledToolPackage(pyTool)?.version

/**
 * The content roots of [modules], with no duplicate, ordered by path.
 *
 * The platform builds the server identity from the root paths in order, so the order must not depend
 * on the order of [modules]. Otherwise renaming a module would start a second server for the same
 * set of folders.
 */
@ApiStatus.Internal
fun pyLspContentRootsOf(modules: List<Module>): Array<VirtualFile> =
  modules.filterNot { it.isDisposed }
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
fun pyLspForeignNestedRoots(served: List<Module>, all: List<Module>): List<String> {
  val folders = pyLspContentRootsOf(served).map { it.path }
  return all.asSequence()
    .filter { it !in served && !it.isDisposed }
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
  served: List<Module>,
  keyOf: (Module) -> PyLspServeKey,
): Boolean =
  descriptors.any { descriptor ->
    val stillServed = descriptor.servedModules.firstOrNull { it in served } ?: return@any false
    val group = pyLspServeGroupOf(stillServed, served, keyOf)
    descriptor.servedModules.toSet() != group.toSet() ||
    descriptor.roots.mapTo(HashSet()) { it.path } != pyLspContentRootsOf(group).mapTo(HashSet()) { it.path }
  }

/**
 * Whether the project serves no module of [descriptor] any more.
 *
 * Such a server answers for nobody. It holds folders the project dropped, and its descriptor keeps a
 * strong reference to every module it was built with, so a disposed module stays reachable while the
 * server runs. The caller stops it.
 */
@ApiStatus.Internal
fun pyLspServesNothing(descriptor: PyLspToolDescriptor, served: List<Module>): Boolean =
  descriptor.servedModules.none { it in served }

/** The modules the server behind [this] answers for. Empty when the client is not a Python LSP tool. */
@get:ApiStatus.Internal
val LspClient.pyServedModules: List<Module>
  get() = (descriptor as? PyLspToolDescriptor)?.servedModules.orEmpty()

/**
 * The client of this collection whose server answers for [module], or `null` when none does.
 *
 * A server answers for the modules of its own descriptor. One server can hold several modules, so do
 * not compare against the primary module alone. A client that answers nothing is skipped, see
 * [isUsable].
 */
@ApiStatus.Internal
fun Collection<LspClient>.clientForModule(module: Module): LspClient? =
  firstOrNull { it.isUsable && module in it.pyServedModules }

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
 * The running [providerClass] client that answers for [module], or `null` when none does. The debug
 * log names the modules that each client serves, and the state of that client.
 *
 * A module gets no client when the tool cannot drive its interpreter. The caller then leaves the
 * external type engine off for that module, and PyCharm infers the types itself.
 */
@ApiStatus.Internal
fun findLspClientForModule(module: Module, providerClass: Class<out LspIntegrationProvider>): LspClient? {
  val clients = LspClientManager.getInstance(module.project).getClients(providerClass)
  val client = clients.clientForModule(module)
  if (client == null && MODULE_CLIENTS_LOG.isDebugEnabled) {
    val served = clients.joinToString { client ->
      val modules = client.pyServedModules.joinToString("+") { m -> m.name }.ifEmpty { "<not a python tool>" }
      "$modules (${client.state})"
    }
    MODULE_CLIENTS_LOG.debug("No running ${providerClass.simpleName} client serves module '${module.name}'. The clients serve: [$served]")
  }
  return client
}
