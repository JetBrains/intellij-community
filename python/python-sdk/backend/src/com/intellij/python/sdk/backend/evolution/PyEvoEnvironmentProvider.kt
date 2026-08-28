@file:Suppress("UnstableApiUsage")

package com.intellij.python.sdk.backend.evolution

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.sdk.common.evolution.EvoAddNewDto
import com.intellij.python.sdk.common.evolution.EvoAddNewOptionDto
import com.intellij.python.sdk.common.evolution.EvoBasePythonDto
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.common.evolution.EvoLeafKind
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoNodeDto
import com.intellij.python.sdk.common.evolution.EvoNodeKind
import com.intellij.python.sdk.common.evolution.EvoRecreateDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.intellij.python.sdk.common.evolution.PyEvoRegistry
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.ExecError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.project
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.pySdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.impl.shortenPath
import com.jetbrains.python.venvReader.Directory
import com.jetbrains.python.venvReader.PRUNED_SCAN_DIRS
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString

private val LOG: Logger = fileLogger()

/**
 * A tool workspace (uv/poetry) a module takes part in: the [root] project everything is resolved against, the [tool]
 * declaring it, and every [modules] belonging to it — the root and its members alike, since they all share one
 * environment.
 */
@ApiStatus.Internal
class EvoWorkspace(val root: PyProject, val tool: ToolId, val modules: List<Module>)

/**
 * The [PyProject] the widget acts on, resolved against the workspace it belongs to.
 *
 * A tool workspace (a uv/poetry workspace) has a single environment, declared at its root: no member owns one, and the
 * tools are always driven from the root. So everything the widget does with a directory (scanning for envs, reading
 * `requires-python`, creating an env, running the tool) uses [baseDir] — the *workspace root's* base dir — and an
 * interpreter picked for any one module is applied to [sdkModules], the whole workspace. Only [module] itself, whose
 * interpreter the status bar reflects, stays the one the user is looking at.
 */
@ApiStatus.Internal
class EvoPyProject(
  private val self: PyProject,
  /** The workspace [self] takes part in (as its root or as a member); `null` when it is standalone. */
  val workspace: EvoWorkspace? = null,
) {
  val module: Module get() = self.residesOnModule

  val project: Project get() = self.project

  /** The directory the widget works in: the workspace root's base dir when in a workspace, else the module's own. */
  val baseDir: Directory get() = (workspace?.root ?: self).baseDir

  /** The module's *own* base dir, whether or not it takes part in a workspace. */
  val moduleBaseDir: Directory get() = self.baseDir

  /** Every module a selected interpreter must be applied to: the whole workspace, or just this module when standalone. */
  val sdkModules: List<Module> get() = workspace?.let { (it.modules + module).distinct() } ?: listOf(module)
}

/** [EvoToolContext.cached] key under which the core-supplied system-Python list is memoized. */
private const val SYSTEM_PYTHONS_KEY: String = "core.systemPythons"

/**
 * What a provider needs to act on the module the widget is showing: the [pyProject] resolved against its workspace, the
 * [fileSystem] of the machine it lives on, and the [errorSink] a failed tool command must be reported to.
 *
 * Bundled into one parameter because every tool-owned operation needs all three, and because it gives the core a single
 * place to decide what a provider is handed — a provider must not reach for the project or the Eel machine itself.
 *
 * Report a tool failure through [errorSink] rather than swallowing it: the default sink opens the platform's
 * process-execution-error dialog with the command, its exit code and its output, which is the only way the user finds
 * out why an environment was not created.
 */
@ApiStatus.Internal
class EvoToolContext(
  val pyProject: EvoPyProject,
  val fileSystem: FileSystem<PathHolder.Eel>,
  val errorSink: ErrorSink,
  private val systemPythons: suspend (String?) -> List<EvoAddNewOptionDto>,
) {
  /**
   * The system Pythons that can back a new environment here, newest first, one per minor version — filtered to what the
   * project's `requires-python` allows and to what can actually host a venv.
   *
   * Supplied by the core rather than computed here: the services that enumerate interpreters and parse
   * `pyproject.toml` both sit *above* this module, so naming them here would be a cycle. It is core knowledge anyway —
   * which Pythons a machine has is not a fact about any one tool — which is why pip, poetry and hatch can all build
   * their version pickers from the same list instead of each deciding what counts.
   *
   * Pass [envSpecifiers] when the *environment* constrains the Python it needs, such as a hatch environment with a
   * `python` option. It is a version specifier — `==3.11`, `>=3.8`, `>=3.9,<3.13` — and the list then holds the
   * versions it admits. It also replaces the two general filters rather than joining them, because the environment
   * accepts nothing outside it and they have nothing left to decide: an environment that asks for 2.7 shows 2.7,
   * whatever the project's `requires-python` says.
   *
   * Memoized per context via [cached], since enumerating interpreters can spawn processes. Each specifier gets its own
   * entry, so a node with several constrained environments still probes once per distinct specifier.
   */
  suspend fun systemPythonOptions(envSpecifiers: String? = null): List<EvoAddNewOptionDto> =
    cached("$SYSTEM_PYTHONS_KEY:${envSpecifiers.orEmpty()}") { systemPythons(envSpecifiers) }

  private val memo = mutableMapOf<String, Any?>()
  private val memoLock = Mutex()

  /**
   * Runs [compute] once per context and returns the same value for every later call with the same [key].
   *
   * A context is built per operation, so this is the scope of "once for this request". It exists because the core may
   * call a provider several times for one operation — [PyEvoEnvironmentProvider.addNewEnvSpec] is asked per section,
   * and a tool can own several — while the expensive part (probing the tool for its Python versions, which spawns a
   * process) depends on the project and not on the section. Without this, a node with three folders would probe three
   * times.
   */
  @Suppress("UNCHECKED_CAST")
  suspend fun <T> cached(key: String, compute: suspend () -> T): T = memoLock.withLock {
    if (key in memo) memo[key] as T else compute().also { memo[key] = it }
  }
}

/**
 * Backend extension point for the "Evo" interpreter widget. Each provider (contributed by a *tool* module —
 * pip/uv/poetry/conda/hatch/…) owns one expandable node and the *layout* of that tool's environments.
 *
 * The central discovery finds every virtualenv under the project's base dirs and hands the list to every
 * provider via [loadSections]; each provider selects the subset it owns (uv: all; pip: non-uv; poetry: its
 * own) and may add tool-specific rows (conda/hatch enumerate their own envs; poetry adds per-version rows).
 *
 * `python-sdk` stays tool-agnostic: it aggregates providers, runs discovery, serializes DTOs, and performs the
 * interpreter switch. Providers never build SDKs or hand-roll display strings/icons.
 */
@ApiStatus.Internal
interface PyEvoEnvironmentProvider {
  /**
   * This provider's identity, and the node id the frontend addresses it by.
   *
   * Use the tool's *canonical* [ToolId] — the same constant its `PyProjectSdkConfigurationExtension` uses — rather
   * than a string spelled for the widget. The widget already speaks that vocabulary on its other path
   * (`PyInterpreterRef.Autoconfigure` carries a setup option's `toolId`), so reusing it keeps one id space instead of
   * a parallel one that can drift from it. A node that is not a tool's takes an id from
   * [com.intellij.python.sdk.common.evolution.EvoNodeIds] instead.
   */
  val toolId: ToolId

  /** Collapsed node label — the display string, unrelated to [toolId]. */
  val label: @Nls String

  /** Collapsed node icon (this provider's own tool icon). */
  val icon: Icon

  /**
   * How this node is reported in usage statistics.
   *
   * Defaults to [EvoNodeKind.OTHER] so a provider that says nothing is reported as unknown rather than being
   * silently counted as something it is not. Tool-backed providers get [EvoNodeKind.TOOL] from
   * [PyToolEvoEnvironmentProvider]; the two nodes with no tool behind them declare their own.
   */
  val nodeKind: EvoNodeKind get() = EvoNodeKind.OTHER

  /**
   * The backing tool's `PyExecutable.fusId`, for a node that has one — reported alongside [nodeKind].
   *
   * Null for every node that is not [EvoNodeKind.TOOL], which is what makes "no tool behind this node" and "a tool we
   * failed to name" impossible to confuse.
   */
  val fusId: String? get() = null

  /**
   * The `pyvenv.cfg` key this tool writes into every environment it creates, or null when it leaves no mark there.
   *
   * It is what lets a row say which tool an environment came from, whichever node the row is shown on — see
   * [withCreators]. Declare one only for a key that *this* tool alone writes. A key several tools share names none of
   * them: `virtualenv`, which poetry, hatch and the `virtualenv` package all write, would attribute one tool's
   * environment to another, and a wrong attribution is worse than none because it disables the row on the real owner's
   * node.
   */
  val pyvenvMarker: String? get() = null

  /**
   * The SDK flavor an interpreter of this node's environments carries, or null when this node owns no SDK of its own.
   *
   * It is what lets the widget say which node holds the environment in use — see [nodeIdForSdk]. Declared here, by the
   * tool, for the same reason [pyvenvMarker] is: which flavor a tool stamps on its SDKs is the tool's own knowledge, and
   * a table of it kept in the core would drift from the tools it names.
   *
   * A flavor names its node exactly. `PyProjectManager.forSdk` is not usable for this: it resolves uv, poetry and hatch
   * and answers `ToolId("pip")` for everything else, which is a wrong answer rather than a missing one — and does not
   * even equal the venv node's own id.
   */
  val sdkFlavor: Class<out PythonSdkFlavor<*>>? get() = null

  /**
   * One line saying what picking a row in this node's submenu does — see [EvoNodeDto.stepDescription].
   *
   * Declared by the tool because only the tool knows what its list means. `null` leaves the frontend's general wording.
   */
  val stepDescription: @Nls String? get() = null

  fun getNode(): EvoNodeDto =
    EvoNodeDto(id = toolId.id, label = label, icon = icon.rpcId(), kind = nodeKind, fusId = fusId,
               stepDescription = stepDescription)

  /**
   * Whether this provider's tool is available on the project's Eel machine. Unavailable providers are dropped
   * from the node list, so an uninstalled tool never shows up. Defaults to always-available.
   */
  suspend fun isAvailable(pyProject: EvoPyProject, fileSystem: FileSystem<PathHolder.Eel>): Boolean = true

  /**
   * Lazily compute this node's sections (layout owned by the provider) when it is expanded. [discovered] is the
   * centrally-found list of virtualenvs under the project's base dirs; providers filter it to the subset they own.
   */
  suspend fun loadSections(
    pyProject: EvoPyProject,
    fileSystem: FileSystem<PathHolder.Eel>,
    discovered: List<DiscoveredVenv>,
  ): EvoLoadResultDto

  /**
   * Builds the correctly-typed SDK for an *existing* environment at [homePath] — the tool's own "select existing"
   * logic, the same the v2 Add dialog runs.
   *
   * Returns a failure rather than a null when the tool cannot adopt the env, so "I could not" is never confused with
   * "not mine" — the pip node builds a generic path-based SDK itself rather than leaving the core to guess.
   */
  suspend fun createSdkForExistingEnv(context: EvoToolContext, homePath: Path): PyResult<Sdk> = notSupported()

  /**
   * Builds the SDK for an environment that does not exist yet, creating it first via the tool's own "create" logic —
   * a poetry per-version row, a hatch declared env, or an "add new" version pick.
   *
   * The provider owns [PyInterpreterRef.CreateEnv]'s payload: its `token`, `folder` and `name` mean whatever this tool
   * put in the leaf it built, and nothing outside this method interprets them. A failure travels back as the result —
   * the core reports it once, so an [ExecError] from a tool command still reaches the process-execution-error dialog.
   */
  suspend fun createSdkForNewEnv(context: EvoToolContext, ref: PyInterpreterRef.CreateEnv): PyResult<Sdk> = notSupported()

  /**
   * The in-widget "add new environment" flow for one of this node's [section]s: the proposed name, where it goes,
   * whether the user may edit it, and the Python versions offered.
   *
   * `null` keeps the frontend's plain "add new" row. What the fields mean is the provider's business — conda names an
   * env, uv and pip name a folder inside the section's directory, poetry's in-project env is always `.venv` and so is
   * not editable.
   */
  suspend fun addNewEnvSpec(context: EvoToolContext, section: EvoSectionDto): EvoAddNewDto? = null

  /**
   * Last look at this node's own [result] before it is serialized, for a decoration only the tool can compute — hatch
   * turning each declared-but-not-created env into a Python-version picker.
   *
   * Applies to this provider's sections only; returning [result] unchanged is the default.
   */
  suspend fun decorate(context: EvoToolContext, result: EvoLoadResultDto): EvoLoadResultDto = result

  /**
   * How this tool would rebuild the environment [leaf] stands for, or `null` when it cannot rebuild that one.
   *
   * Capability and options are one answer because they are one decision: a tool that can rebuild a row knows what it
   * could rebuild it on, and a tool that cannot has nothing to offer. Asked per row rather than per node, because the
   * answer differs row by row — a hatch environment's own Python specifier constrains its bases and no other's.
   *
   * Called for a row the core already admitted: an environment that exists ([PyInterpreterRef.DetectedPath]), that this
   * node can act on ([EvoLeafDto.unavailable] unset), and that lives inside the project. Those are facts about the row
   * and the project rather than about the tool, so the core decides them once — see [ownedEnvBinaryIn].
   */
  suspend fun recreateSpecFor(context: EvoToolContext, leaf: EvoLeafDto): EvoRecreateDto? = null

  /**
   * Destroys the environment whose interpreter is [homePath], builds a fresh one in the same place on
   * [EvoRecreateSpec.baseToken], and returns its SDK — the counterpart of [createSdkForNewEnv].
   *
   * There is no rollback and none is expected: destroy, create, and report a failure as the result. A provider that
   * could not destroy the environment must return that failure rather than build over the wreckage.
   */
  suspend fun recreateEnv(context: EvoToolContext, homePath: Path, spec: EvoRecreateSpec): PyResult<Sdk> = notSupported()

  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<PyEvoEnvironmentProvider> = ExtensionPointName.create("Pythonid.evoEnvironmentProvider")
  }
}

/**
 * The tool that made an environment: enough to name it in a message, draw its row, and tell it from the node the row is
 * shown on. Taken from a provider, so a tool is drawn and named the same way wherever it appears.
 */
@ApiStatus.Internal
data class EvoVenvCreator(val toolId: ToolId, val label: @Nls String, val icon: Icon)

/**
 * A virtualenv found by the central discovery, together with its parsed `pyvenv.cfg` [config] (tool markers +
 * version). No python is executed during discovery; the display [version] comes from `pyvenv.cfg`.
 */
@ApiStatus.Internal
data class DiscoveredVenv(
  val pythonBinary: Path,
  val config: Map<String, String>,
  val version: @NlsSafe String?,
  /** The tool [config] names as this environment's maker, or null when nothing there names one — see [withCreators]. */
  val creator: EvoVenvCreator? = null,
) {
  /** The environment root directory (`…/<venv>/bin/python` → `<venv>`). */
  val venvRoot: Path? get() = pythonBinary.parent?.parent
}

/**
 * The node id whose tool made [sdk], or null when no provider claims its flavor.
 *
 * The counterpart of [withCreators] for an SDK rather than a discovered directory: a provider declares the flavor it
 * stamps ([PyEvoEnvironmentProvider.sdkFlavor]) and the core matches. The flavors are flat siblings of
 * `CPythonSdkFlavor`, so at most one provider can claim any SDK and the order of the list does not matter.
 *
 * Null is a real answer, not a gap: an SDK built without additional data, or one of a flavor no node owns, has no node
 * to name and the caller must not guess at one.
 */
@ApiStatus.Internal
fun List<PyEvoEnvironmentProvider>.nodeIdForSdk(sdk: Sdk): String? {
  // `pySdkAdditionalData` throws on an SDK created without any — "buggy code" per its own message. Test the
  // precondition rather than catch, since an IllegalStateException here could be a ProcessCanceledException.
  if (sdk.sdkAdditionalData !is PythonSdkAdditionalData) return null
  val flavor = sdk.pySdkAdditionalData.flavor
  return firstOrNull { provider -> provider.sdkFlavor?.isInstance(flavor) == true }?.toolId?.id
}

/**
 * Names the tool behind each environment, by reading its `pyvenv.cfg` against what every provider says it writes
 * ([PyEvoEnvironmentProvider.pyvenvMarker]).
 *
 * Done once for the whole list, by the core, rather than per node: which tool made an environment is a fact about the
 * environment, and every node showing it must read it the same way. An environment naming no known tool keeps a null
 * creator and is shown as the node's own, which is what an ordinary `python -m venv` environment is on any of them.
 */
@ApiStatus.Internal
fun List<DiscoveredVenv>.withCreators(providers: List<PyEvoEnvironmentProvider>): List<DiscoveredVenv> {
  val byMarker = providers.mapNotNull { provider ->
    provider.pyvenvMarker?.let { it to EvoVenvCreator(provider.toolId, provider.label, provider.icon) }
  }
  if (byMarker.isEmpty()) return this
  return map { venv -> venv.copy(creator = byMarker.firstOrNull { (marker, _) -> marker in venv.config }?.second) }
}

/**
 * Discovers virtualenvs under [baseDirs], descending into nested subfolders (up to [maxDepth] levels) so envs kept in
 * a project subdirectory are found too — not just those directly under a base dir. Directories in [excludedRoots]
 * (content roots of other modules) are skipped, a directory that is itself a venv is never descended into, and
 * [PRUNED_SCAN_DIRS] are pruned. The walk is breadth-first so shallower envs (the common case: a project-root `.venv`)
 * are always found first, and it is capped at [maxDirs] directories. No python is executed; the display version is
 * read from `pyvenv.cfg`.
 *
 * [maxDepth] and [maxDirs] default to the registry-backed [PyEvoRegistry.scanMaxDepth]/[PyEvoRegistry.scanMaxDirs];
 * they are parameters so tests can drive the walk without a loaded registry.
 */
@ApiStatus.Internal
suspend fun discoverVenvs(
  baseDirs: List<Path>,
  excludedRoots: Set<Path>,
  maxDepth: Int = PyEvoRegistry.scanMaxDepth,
  maxDirs: Int = PyEvoRegistry.scanMaxDirs,
): List<DiscoveredVenv> = withContext(Dispatchers.IO) {
  val reader = VirtualEnvReader()
  val found = mutableListOf<DiscoveredVenv>()

  fun childDirs(dir: Path): List<Path> {
    val entries = try {
      dir.listDirectoryEntries()
    }
    catch (_: IOException) {
      return emptyList()
    }
    return entries.filter { it.fileName?.toString() !in PRUNED_SCAN_DIRS && it.isDirectory() }
  }

  // The base dir itself is never treated as a venv (matching the previous behavior); scanning starts at its children.
  var frontier = baseDirs.flatMap { childDirs(it) }
  var budget = maxDirs
  var depth = 1
  while (frontier.isNotEmpty() && depth <= maxDepth && budget > 0) {
    val next = mutableListOf<Path>()
    for (dir in frontier) {
      if (budget-- <= 0) break
      if (dir in excludedRoots) continue
      val binary = reader.findPythonInPythonRoot(dir)
      if (binary != null) {
        val config = parsePyvenvCfg(dir.resolve("pyvenv.cfg"))
        found += DiscoveredVenv(binary, config, config.pyvenvVersion())
        continue // a venv is a leaf: never descend into its internals
      }
      if (depth < maxDepth) next += childDirs(dir)
    }
    frontier = next
    depth++
  }
  found
}

/** The default for a tool-owned operation a provider does not implement: a failure naming the node, never a null. */
@ApiStatus.Internal
fun <T> notSupported(): PyResult<T> = PyResult.localizedError(PySdkBundle.message("evolution.error.select.failed"))

/** The tool backing this node is not installed on the target machine — its executable did not resolve. */
@ApiStatus.Internal
fun <T> PyEvoEnvironmentProvider.toolMissing(): PyResult<T> =
  PyResult.localizedError(PySdkBundle.message("evolution.error.tool.not.found", label))

/**
 * The names of every entry directly inside [dir], for validating a new environment's name against what is already there.
 *
 * Catches only [IOException], which is what [listDirectoryEntries] declares: an unreadable directory means "nothing
 * known to be taken", which is the safe answer since creating the environment will fail on its own if the name is in
 * fact occupied. Anything else is not an expected outcome and propagates.
 */
@ApiStatus.Internal
suspend fun listEntryNames(dir: Path): List<@NlsSafe String> = withContext(Dispatchers.IO) {
  try {
    dir.listDirectoryEntries().map { it.fileName.toString() }
  }
  catch (e: IOException) {
    LOG.info("Cannot list $dir for env-name validation", e)
    emptyList()
  }
}

/** What a rebuild is asked for: the base to build on, in the tool's own token, and whether to fill it afterwards. */
@ApiStatus.Internal
class EvoRecreateSpec(val baseToken: @NonNls String, val syncPackages: Boolean)

/**
 * The interpreter of the environment [this] names, when that environment is the project's own to destroy; `null`
 * otherwise — the gate deciding which rows may be rebuilt.
 *
 * Two rules, both facts about the ref and the project rather than about any tool, so no provider restates them. Only an
 * environment that exists has anything to destroy. And only an environment under [baseDir] is the project's own to
 * take: a system interpreter, a pyenv install, a named conda env, a poetry cache env and a pipenv env are all shared
 * with whatever else uses them.
 *
 * [baseDir] is the workspace root's, not one module's, because the members of a uv or poetry workspace share the
 * environment kept there.
 *
 * The comparison is lexical over normalized paths, so an environment reached through a symlink reads as not owned. That
 * is the safe direction: it withholds a destructive affordance rather than offering it where it should not be.
 */
@ApiStatus.Internal
fun PyInterpreterRef?.ownedEnvBinaryIn(baseDir: Path): Path? {
  val binary = when (this) {
    is PyInterpreterRef.DetectedPath -> homePath.toNioPathOrNull()
    is PyInterpreterRef.CreateEnv, is PyInterpreterRef.ExistingSdk, is PyInterpreterRef.Autoconfigure, null -> null
  } ?: return null
  val normalized = binary.normalize()
  val root = baseDir.normalize()
  return binary.takeIf { normalized != root && normalized.startsWith(root) }
}

/**
 * "That environment already exists", so a create never silently overwrites or recreates one.
 *
 * The widget's name field already rejects a taken name, so reaching this means the directory appeared between the popup
 * being built and the row being clicked.
 */
@ApiStatus.Internal
fun <T> envExistsError(name: @NlsSafe String): PyResult<T> =
  PyResult.localizedError(PySdkBundle.message("evolution.error.env.exists", name))

/**
 * Where a folder-based tool (uv, pip) should create the environment a [ref] asks for: the (possibly user-edited)
 * [PyInterpreterRef.CreateEnv.name] inside its [PyInterpreterRef.CreateEnv.folder] containing directory.
 *
 * The fallbacks keep older refs working — a `folder` that is itself the full path, or neither field set, in which case
 * the first free `.venv{X}` under the base dir is used, matching what the add-new row proposes.
 */
@ApiStatus.Internal
fun EvoToolContext.resolveNewVenvDir(ref: PyInterpreterRef.CreateEnv): Path {
  // Locals, so `isNullOrBlank`'s contract can narrow them — it does not narrow a property receiver.
  val folder = ref.folder
  val name = ref.name
  return when {
    !folder.isNullOrBlank() && !name.isNullOrBlank() -> Path.of(folder).resolve(name)
    !folder.isNullOrBlank() -> pyProject.baseDir.resolve(folder)
    else -> firstFreeVenvDir(pyProject.baseDir)
  }
}

/** Upper bound for the `.venv{X}` suffix search when auto-naming a new env folder; far beyond any realistic project. */
private const val MAX_VENV_NAME_INDEX = 1000

/**
 * First free environment folder under [baseDir]: `.venv`, then `.venv1`, `.venv2`, … — the naming the widget's
 * in-place "add new environment" uses. Falls back to `.venv{MAX+1}` only if every candidate up to the cap exists
 * (pathological); env creation then surfaces any real conflict.
 */
@ApiStatus.Internal
fun firstFreeVenvDir(baseDir: Path): Path {
  val base = VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME
  for (i in 0..MAX_VENV_NAME_INDEX) {
    val candidate = baseDir.resolve(if (i == 0) base else "$base$i")
    if (!candidate.exists()) return candidate
  }
  return baseDir.resolve("$base${MAX_VENV_NAME_INDEX + 1}")
}

/** The fixed `.venv` folder under [baseDir] — for tools (poetry) whose in-project env is always `.venv`, not `.venv{X}`. */
@ApiStatus.Internal
fun defaultVenvDir(baseDir: Path): Path = baseDir.resolve(VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME)

private fun parsePyvenvCfg(path: Path): Map<String, String> {
  if (!path.exists()) return emptyMap()
  return try {
    buildMap {
      for (line in Files.readAllLines(path)) {
        val eq = line.indexOf('=')
        if (eq >= 0) put(line.substring(0, eq).trim(), line.substring(eq + 1).trim())
      }
    }
  }
  catch (_: IOException) {
    emptyMap()
  }
}

private fun Map<String, String>.pyvenvVersion(): String? =
  this["version"]?.takeIf { it.isNotBlank() }
  ?: this["version_info"]?.substringBefore(".final")?.takeIf { it.isNotBlank() }

/** Shortens the user home to `~` for display, matching how SDK names are rendered. */
@ApiStatus.Internal
fun Path.toDisplayPath(): @NlsSafe String = FileUtil.getLocationRelativeToUserHome(pathString, false)

/**
 * Max length (chars) of a section-header path before its middle is elided. Same budget as an SDK's `shortName`, which is
 * the other place a path has to label something without dictating how wide it gets.
 */
private const val SECTION_LABEL_MAX_CHARS = 50

/**
 * Header form of a path: home-relative like [toDisplayPath], then middle-elided past [SECTION_LABEL_MAX_CHARS] by the same
 * shortener a long SDK name goes through (`~/.cache/intellij-python-test-env/conda/Min…/envs/child`). Only headers get this
 * — a row's description keeps the full [toDisplayPath], since a tooltip costs no layout width.
 */
@ApiStatus.Internal
fun Path.toSectionLabel(): @NlsSafe String = shortenPath(toDisplayPath(), SECTION_LABEL_MAX_CHARS, keepPrefix = true)

/** Version-column placeholder for a row that has no interpreter to report a version for (env not created / unreadable). */
@ApiStatus.Internal
const val NO_VERSION: @NlsSafe String = "n/a"

/**
 * Builds a SELECT_ENV leaf for a discovered venv on [owner]'s node; the version comes from `pyvenv.cfg` (or "n/a").
 *
 * A row this node can offer keeps [owner]'s icon, whoever made the environment. Only a row the node cannot offer — one
 * `pyvenv.cfg` names another tool as the maker of — swaps in that tool's icon and is shown disabled, with the tool named
 * in the tooltip. The icon and the disabled look then say the same thing: this environment belongs to another node.
 *
 * So the icon changes exactly where the row stops being selectable, and nowhere else. An environment naming no tool
 * stays the node's own to offer, which is the usual case: only uv writes a key of its own, so an environment created by
 * `python -m venv`, by the `virtualenv` package, or by hatch through either of them, names nobody.
 */
@ApiStatus.Internal
fun DiscoveredVenv.toLeaf(owner: PyEvoEnvironmentProvider): EvoLeafDto {
  val name: @NlsSafe String = venvRoot?.fileName?.toString() ?: pythonBinary.fileName.toString()
  val otherTool = creator?.takeIf { it.toolId != owner.toolId }
  return EvoLeafDto(
    title = name,
    description = pythonBinary.toDisplayPath(),
    secondaryText = version ?: NO_VERSION,
    icon = (otherTool?.icon ?: owner.icon).rpcId(),
    kind = EvoLeafKind.SELECT_ENV,
    ref = PyInterpreterRef.DetectedPath(pythonBinary.pathString),
    unavailable = otherTool?.let { PySdkBundle.message("evolution.env.owned.by.other.tool", it.label) },
    ownerNodeId = otherTool?.toolId?.id,
  )
}

/**
 * Groups discovered venvs by their containing folder into sections. When the list is empty and [addNew] is set,
 * emits a single "Add new environment" section for [baseDir] so the node is never an empty popup. Each section carries
 * its containing folder ([EvoSectionDto.addNewFolderPath]) so "add new" targets that folder, not always the base dir.
 */
@ApiStatus.Internal
fun List<DiscoveredVenv>.toSectionsGroupedByParent(owner: PyEvoEnvironmentProvider, addNew: Boolean, baseDir: Path): List<EvoSectionDto> {
  if (isEmpty()) {
    return if (addNew) listOf(EvoSectionDto(label = null,
                                            leaves = emptyList(),
                                            addNew = true,
                                            addNewFolderPath = baseDir.pathString))
    else emptyList()
  }
  return groupBy { it.venvRoot?.parent }.map { (containingFolder, venvs) ->
    EvoSectionDto(
      label = containingFolder?.toSectionLabel(),
      labelTooltip = containingFolder?.toDisplayPath(),
      leaves = venvs.map { it.toLeaf(owner) },
      addNew = addNew,
      addNewFolderPath = (containingFolder ?: baseDir).pathString,
    )
  }
}

/**
 * Builds an ACTION leaf carrying the given (provider-owned) [icon]. Pass [actionId] to make the row *runnable* on
 * the backend (dispatched to [com.intellij.python.sdk.common.evolution.PyEvoSdkApi.performNodeAction]); leave it
 * `null` for a display-only row.
 */
@ApiStatus.Internal
fun evoActionLeaf(
  title: @Nls String,
  description: @Nls String? = title,
  secondaryText: @Nls String? = null,
  icon: Icon,
  actionId: String? = null,
): EvoLeafDto =
  EvoLeafDto(title = title,
             description = description,
             secondaryText = secondaryText,
             icon = icon.rpcId(),
             kind = EvoLeafKind.ACTION,
             actionId = actionId)

/**
 * Builds a leaf for a Python version that is not on the machine: selecting it installs that version and then creates the
 * environment from it. The download icon and hint say so up front, matching the v2 "Add Interpreter" dialog's entries.
 *
 * For a tool whose rows are per-version create rows rather than an "add new" version list (poetry's cache section), which
 * is why this exists alongside [EvoAddNewOptionDto.installable] rather than instead of it.
 */
@ApiStatus.Internal
fun evoInstallPythonLeaf(title: @Nls String, version: @NlsSafe String): EvoLeafDto =
  EvoLeafDto(
    title = title,
    secondaryText = PySdkBundle.message("evolution.base.python.installable"),
    icon = AllIcons.Actions.Download.rpcId(),
    kind = EvoLeafKind.SELECT_ENV,
    ref = PyInterpreterRef.CreateEnv(token = version, installPythonVersion = version),
  )

/**
 * Builds a leaf for a declared-but-not-yet-materialized env (poetry per-version row, hatch declared env). Selecting
 * it creates the env via the tool's create logic and then assigns it; [token] is tool-specific — see
 * [com.intellij.python.sdk.common.evolution.PyInterpreterRef.CreateEnv].
 */
@ApiStatus.Internal
fun evoCreateEnvLeaf(
  title: @Nls String,
  token: String,
  icon: Icon,
  bases: List<EvoBasePythonDto> = emptyList(),
  /**
   * A second provider-owned token, carried as [PyInterpreterRef.CreateEnv.name]. It means whatever the provider that
   * built this leaf reads back from it, exactly like [token]. Hatch puts the environment's declared version specifier
   * here, so [PyEvoEnvironmentProvider.decorate] can offer the interpreters it admits and no other.
   */
  name: String? = null,
): EvoLeafDto =
  EvoLeafDto(title = title, icon = icon.rpcId(), kind = EvoLeafKind.SELECT_ENV, ref = PyInterpreterRef.CreateEnv(token, name = name), bases = bases)

/**
 * Builds a leaf for a *tool-enumerated* environment (conda/hatch/poetry-per-version) identified by [pythonBinary].
 * The interpreter [version] is NOT probed here — pass it only when the tool reports it cheaply; otherwise leave it
 * `null` and the frontend resolves it lazily on hover, so we never spawn a `python --version` process per env.
 * When [pythonBinary] is `null` the environment has no runnable interpreter yet, so a display-only "n/a" ACTION leaf is produced.
 */
@ApiStatus.Internal
fun evoEnvLeaf(title: @Nls String, pythonBinary: Path?, icon: Icon, version: @NlsSafe String? = null): EvoLeafDto {
  if (pythonBinary == null) {
    return evoActionLeaf(title = title, description = null, secondaryText = version ?: NO_VERSION, icon = icon)
  }
  return EvoLeafDto(
    title = title,
    description = pythonBinary.toDisplayPath(),
    descriptionElided = pythonBinary.toSectionLabel(),
    secondaryText = version,
    icon = icon.rpcId(),
    kind = EvoLeafKind.SELECT_ENV,
    ref = PyInterpreterRef.DetectedPath(pythonBinary.pathString),
  )
}

/** Convenience for providers that fail softly (tool not installed, etc.). */
@ApiStatus.Internal
fun evoWarning(message: @Nls String): EvoLoadResultDto = EvoLoadResultDto.Warning(message)

