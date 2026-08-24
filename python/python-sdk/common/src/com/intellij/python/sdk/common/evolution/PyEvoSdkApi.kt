package com.intellij.python.sdk.common.evolution

import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.client.RpcClientException
import fleet.rpc.core.RpcException
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

private val LOG: Logger = fileLogger()

/**
 * Split-mode wire contract for the "Evo" Python interpreter widget
 * (registry `python.evolution.widget`).
 *
 * All Python-SDK discovery is backend-only, so the frontend widget/popup obtain their display data
 * through these read-only RPC calls and render it into UI; [selectInterpreter] is the single mutating
 * call that actually switches the module interpreter.
 *
 * The interpreter model is the platform's [com.jetbrains.python.sdk.PythonInterpreter] /
 * `PythonInterpreterPresentation` on the backend; on the wire it is flattened into [PyInterpreterDto]
 * (display) plus a [PyInterpreterRef] (selection token). What every call is addressed to is a `PyProject`,
 * referenced by [ProjectId] plus its [EvoPyProjectDto.key] — the project's base dir, resolved on the backend
 * against a cached snapshot. A key is deliberately not a module name: a module rename would invalidate every
 * key the frontend is holding, while a base dir survives one.
 *
 * Every process the backend launches for the widget is grouped in the process/trace view under a root
 * "Python Interpreter Widget" [com.jetbrains.python.TraceContext], with a per-tool child context nested under it.
 * A fresh root is created for each popup tree the frontend builds: the frontend passes a [String] `traceId` for
 * that build, and the backend keys the root scope on it, so a re-open served from the frontend cache reuses the
 * tree (no new root), while a rebuilt tree gets a new root.
 */
@ApiStatus.Internal
@Rpc
interface PyEvoSdkApi : RemoteApi<Unit> {
  /**
   * Every `PyProject` in the project, re-emitted whenever that set changes (a module added or removed, a Python
   * facet gained or lost, a content root moved).
   *
   * This is the frontend's whole model of the project's Python structure: which keys exist (so it never asks about
   * one that does not), which is the [EvoPyProjectDto.isMain] one (so a file belonging to no module still has an
   * answer), and how they cluster into tool workspaces (so the popup can name the workspace a member belongs to).
   * The frontend cannot compute any of it — `PyProject` is a backend notion — and it is a fact about the project
   * rather than about any one target, so it is pushed once instead of asked per target.
   */
  suspend fun pyProjects(projectId: ProjectId): Flow<List<EvoPyProjectDto>>

  /** The Eel interpreter currently configured for the target, as display-ready data (or `null` if none). */
  suspend fun getCurrentInterpreter(projectId: ProjectId, pyProjectKey: String): PyInterpreterDto?

  /**
   * The expandable nodes contributed by the backend `PyEvoEnvironmentProvider` extension point
   * (venv/uv/poetry/conda/hatch/advanced/…), filtered to the tools available on the module's Eel machine and shown
   * collapsed in the popup. Each tool-availability probe runs in that tool's
   * [com.jetbrains.python.TraceContext] under a transient "Python Interpreter Widget" root created for this call.
   */
  suspend fun listNodes(projectId: ProjectId, pyProjectKey: String): List<EvoNodeDto>

  /**
   * The "Shortcuts" rows shown (in place of the current-interpreter actions) when the module has no interpreter: the
   * IDE's own setup suggestion for the module, computed by the same model-aware detector the "no interpreter
   * configured" inspection uses. Each row is a [PyInterpreterRef.Autoconfigure] leaf whose selection runs that
   * autoconfiguration. Empty when the module already has an interpreter or nothing can be suggested.
   */
  suspend fun listShortcuts(projectId: ProjectId, pyProjectKey: String): List<EvoLeafDto>

  /**
   * Lazily loads the sections of the node with [nodeId] (from a backend provider) when it is expanded. Every
   * command this runs executes in the [nodeId] tool's [com.jetbrains.python.TraceContext] under the widget root
   * for [traceId] (the frontend's per-tree-build id, shared by all commands of one built popup tree).
   *
   * A `refreshable` tool's result is cached for 10 min; [forceRefresh] (from its reload icon) bypasses and refills
   * that cache. Non-refreshable tools are never cached here (the frontend's short-lived popup-tree cache covers them).
   */
  suspend fun loadNode(projectId: ProjectId, pyProjectKey: String, nodeId: String, traceId: String, forceRefresh: Boolean): EvoLoadResultDto

  /**
   * The interpreters already configured for / assignable to the module — the same list the classic interpreter
   * widget shows. Rendered as a single "Associated environments" node; each is selectable by its SDK name, not
   * managed per-tool.
   */
  suspend fun listAssociatedInterpreters(projectId: ProjectId, pyProjectKey: String): List<PyInterpreterDto>

  /**
   * Switches the module interpreter to the environment identified by [ref]. [nodeId] is the tool node the row came
   * from (`"uv"`, `"Poetry"`, `"Conda"`, `"Hatch"`, `"pip"`, `"associated"`), used to create a correctly-typed SDK:
   * an already-configured SDK ([PyInterpreterRef.ExistingSdk]) is assigned as-is; a detected env
   * ([PyInterpreterRef.DetectedPath]) or a not-yet-created env ([PyInterpreterRef.CreateEnv]) is created via that
   * tool's own "select existing"/"create" logic (the same the v2 Add dialog runs) and then assigned.
   */
  suspend fun selectInterpreter(projectId: ProjectId, pyProjectKey: String, ref: PyInterpreterRef, nodeId: String): EvoSelectResultDto

  /**
   * Resolves the interpreter version (`python --version`) for the environment at [homePath], on demand — the
   * frontend calls this lazily as a row scrolls into view, so we never spawn a process per environment up front.
   * The probe runs in the [nodeId] tool's [com.jetbrains.python.TraceContext] (the same context the tool's env
   * listing used) under the "Python Interpreter Widget" root for [traceId], so it appears under that tool.
   */
  suspend fun resolveInterpreterVersion(projectId: ProjectId, pyProjectKey: String, nodeId: String, homePath: String, traceId: String): String?

  /**
   * Opens the platform's "Add Python Interpreter" (v2) dialog for the module, preselecting the environment
   * manager of the node the "add new environment" row belongs to ([nodeId], e.g. `Conda` → conda, `uv` → uv) so
   * the user lands on the matching creator. On OK the created SDK is associated with the module and the widget
   * refreshes on the resulting `rootsChanged`.
   */
  suspend fun addInterpreter(projectId: ProjectId, pyProjectKey: String, nodeId: String): EvoSelectResultDto

  /**
   * Runs the backend ACTION identified by [actionId] within node [nodeId] (e.g. an "Advanced" add-interpreter or
   * add-on-target action) — typically opening its dialog/wizard on the backend. When it creates an interpreter, the
   * SDK is associated with the module and the widget refreshes on the resulting `rootsChanged`.
   */
  suspend fun performNodeAction(projectId: ProjectId, pyProjectKey: String, nodeId: String, actionId: String): EvoSelectResultDto

  /**
   * Opens the Python Process Output tool window on the process a tool's last run produced, addressed by that run's
   * trace — what the widget offers when a tool node reports a failure.
   *
   * Done from the backend because that is where the trace is: the widget's `traceId` keys the coroutine scope a tool
   * runs in, and the [com.jetbrains.python.TraceContext] in that scope is what the tool window knows the process by.
   * `false` when that run is gone (an expired scope) or the window could not find it, which the frontend answers by
   * opening the window without a selection.
   */
  suspend fun showToolProcessOutput(projectId: ProjectId, nodeId: String, traceId: String): Boolean

  /**
   * A flow of the project's SDK-configuration lock state (`com.jetbrains.python.sdk.isSdkConfigurationInProgress`):
   * `true` while any interpreter configuration (create/select) holds the lock. The widget shows a spinner and
   * disables its popup while `true`, instead of the current interpreter and its actions.
   */
  suspend fun sdkConfigurationInProgress(projectId: ProjectId): Flow<Boolean>
}

@ApiStatus.Internal
suspend fun PyEvoSdkApi(): PyEvoSdkApi = RemoteApiProviderService.resolve(remoteApiDescriptor<PyEvoSdkApi>())

/**
 * Frontend-facing wrappers that hide the RPC/`fleet.rpc` types behind plain DTO results, so the frontend
 * module does not need to depend on `intellij.platform.rpc`.
 */
/**
 * Runs an Evo RPC call, returning `null` when the backend could not answer.
 *
 * The widget must survive a backend that is not there — a closing project, a reconnecting remote-dev host, a handler
 * that failed — by degrading to "no data" rather than by breaking the status bar. Only the two RPC failure families are
 * caught: [RpcException] for a call the backend failed, [RpcClientException] for transport (route closed, timeout,
 * disconnect, unresolved service). Anything else is not an RPC problem and propagates.
 */
/**
 * An Evo RPC call the backend could not answer — it failed there, or the call never reached it.
 *
 * Declared here so callers can react to "the backend is not available" without depending on the RPC library: the
 * frontend renders it, and only this module knows which fleet exceptions mean it.
 */
@ApiStatus.Internal
class EvoRpcFailedException internal constructor(cause: Throwable) : Exception(cause.message, cause)

/** Runs an Evo RPC call, translating either RPC failure family into [EvoRpcFailedException]. */
@ApiStatus.Internal
suspend fun <T> evoRpc(call: suspend () -> T): T =
  try {
    call()
  }
  catch (e: RpcException) {
    throw EvoRpcFailedException(e)
  }
  catch (e: RpcClientException) {
    throw EvoRpcFailedException(e)
  }

@ApiStatus.Internal
suspend fun <T> evoRpcOrNull(call: suspend () -> T): T? =
  try {
    call()
  }
  catch (e: RpcException) {
    LOG.info("Evo RPC call failed on the backend", e); null
  }
  catch (e: RpcClientException) {
    LOG.info("Evo RPC call could not reach the backend", e); null
  }

@ApiStatus.Internal
suspend fun requestEvoPyProjects(projectId: ProjectId): Flow<List<EvoPyProjectDto>> =
  PyEvoSdkApi().pyProjects(projectId)

@ApiStatus.Internal
suspend fun requestEvoCurrentInterpreter(projectId: ProjectId, pyProjectKey: String): PyInterpreterDto? =
  PyEvoSdkApi().getCurrentInterpreter(projectId, pyProjectKey)

@ApiStatus.Internal
suspend fun requestEvoNodes(projectId: ProjectId, pyProjectKey: String): List<EvoNodeDto> =
  PyEvoSdkApi().listNodes(projectId, pyProjectKey)

@ApiStatus.Internal
suspend fun requestEvoShortcuts(projectId: ProjectId, pyProjectKey: String): List<EvoLeafDto> =
  PyEvoSdkApi().listShortcuts(projectId, pyProjectKey)

@ApiStatus.Internal
suspend fun requestEvoNode(projectId: ProjectId, pyProjectKey: String, nodeId: String, traceId: String, forceRefresh: Boolean = false): EvoLoadResultDto =
  PyEvoSdkApi().loadNode(projectId, pyProjectKey, nodeId, traceId, forceRefresh)

@ApiStatus.Internal
suspend fun requestEvoAssociatedInterpreters(projectId: ProjectId, pyProjectKey: String): List<PyInterpreterDto> =
  PyEvoSdkApi().listAssociatedInterpreters(projectId, pyProjectKey)

@ApiStatus.Internal
suspend fun requestEvoSelectInterpreter(projectId: ProjectId, pyProjectKey: String, ref: PyInterpreterRef, nodeId: String): EvoSelectResultDto =
  PyEvoSdkApi().selectInterpreter(projectId, pyProjectKey, ref, nodeId)

@ApiStatus.Internal
suspend fun requestEvoResolveVersion(projectId: ProjectId, pyProjectKey: String, nodeId: String, homePath: String, traceId: String): String? =
  PyEvoSdkApi().resolveInterpreterVersion(projectId, pyProjectKey, nodeId, homePath, traceId)

@ApiStatus.Internal
suspend fun requestEvoAddInterpreter(projectId: ProjectId, pyProjectKey: String, nodeId: String): EvoSelectResultDto =
  PyEvoSdkApi().addInterpreter(projectId, pyProjectKey, nodeId)

@ApiStatus.Internal
suspend fun requestEvoPerformNodeAction(projectId: ProjectId, pyProjectKey: String, nodeId: String, actionId: String): EvoSelectResultDto =
  PyEvoSdkApi().performNodeAction(projectId, pyProjectKey, nodeId, actionId)

@ApiStatus.Internal
suspend fun requestEvoShowToolProcessOutput(projectId: ProjectId, nodeId: String, traceId: String): Boolean =
  PyEvoSdkApi().showToolProcessOutput(projectId, nodeId, traceId)

@ApiStatus.Internal
suspend fun requestEvoSdkConfigurationInProgress(projectId: ProjectId): Flow<Boolean> =
  PyEvoSdkApi().sdkConfigurationInProgress(projectId)

/**
 * Frontend-safe, serializable projection of a `PythonInterpreterPresentation` (an interpreter's display
 * label/icon), plus the [ref] needed to select it. All fields are pre-computed on the backend so the
 * frontend never resolves paths or spawns a process.
 */
@ApiStatus.Internal
@Serializable
data class PyInterpreterDto(
  /** Compact label, e.g. `myenv [3.12.1]` (from `PythonInterpreterPresentation.shortName`). */
  val title: @Nls String,
  /** Tooltip / interpreter binary path (from `PythonInterpreterPresentation.description`). */
  val description: @Nls String,
  /** RPC-transferable icon; supplied by the backend via `Icon.rpcId()`. */
  val icon: IconId,
  /** Selection token for [PyEvoSdkApi.selectInterpreter]. */
  val ref: PyInterpreterRef,
  /**
   * URL of the dependency file this interpreter's package manager works with (`pyproject.toml`, `environment.yml`,
   * `requirements.txt`, …), or `null` when it has none.
   *
   * The popup shows the whole `PythonPackageManagerActions` group and puts this file into its data context, which is
   * what each action gates on in `update()` and acts on in `actionPerformed`. Naming it here — instead of whatever the
   * editor happens to show, which has nothing to do with the interpreter — is what keeps a conda `environment.yml`
   * action from firing against a `pyproject.toml`, and lets the applicable rows work whatever file is open.
   *
   * `null` is a statement, not a gap: the popup masks the file keys outright, so an interpreter with no dependency file
   * shows no package-manager rows regardless of what is open, rather than borrowing the editor's file.
   */
  val dependencyFileUrl: @NonNls String? = null,
)

/** Opaque, serializable selector telling the backend which interpreter [PyEvoSdkApi.selectInterpreter] must apply. */
@ApiStatus.Internal
@Serializable
sealed interface PyInterpreterRef {
  /** An interpreter that is already a registered PyCharm SDK, identified by its unique SDK name. */
  @Serializable
  data class ExistingSdk(val sdkName: @NonNls String) : PyInterpreterRef

  /** A detected environment that is not yet an SDK; the backend creates it from its home path on select. */
  @Serializable
  data class DetectedPath(val homePath: @NonNls String) : PyInterpreterRef

  /**
   * A declared-but-not-yet-materialized environment (poetry per-version row, hatch declared env, or an
   * "add new" version pick for uv/pip): the backend creates it via the tool's create logic, then assigns it.
   * [token] is tool-specific — poetry: the base/system Python path; hatch: the declared env name; uv: the
   * chosen Python version (empty = uv's default); pip: the chosen system Python's binary path. [folder] (uv/pip
   * only) is the env folder location (absolute path of the auto-generated first-free `.venv{X}` in the section's
   * folder); when null the backend uses the first free `.venv`, `.venv1`, … under the module base dir.
   *
   * [name] is the user-editable env name from the in-widget "add new" name field: for uv/pip it is the env **folder
   * name** created inside [folder] (the containing dir); for conda it is the **env name**. `null` keeps the tool's
   * default (the pre-filled name).
   */
  @Serializable
  data class CreateEnv(
    val token: @NonNls String,
    val folder: @NonNls String? = null,
    val name: @NonNls String? = null,
    /**
     * The Python version to install before creating anything, for a row that offered an interpreter the machine does
     * not have (see [EvoAddNewOptionDto.installable]). The backend installs it and then carries on with [token] pointing
     * at what landed, so a tool never has to know that installation was involved.
     */
    val installPythonVersion: @NonNls String? = null,
  ) : PyInterpreterRef

  /**
   * Configure the module's interpreter using one of the IDE's setup options (the "Shortcuts" rows — the same options
   * the "no interpreter configured" inspection ranks), identified by [toolId] (a `PyProjectSdkConfigurationExtension`
   * tool id). The backend re-resolves that option for the module and applies it: it creates the env (or, when the
   * option's tool isn't installed yet, installs the tool and then creates the env) under the SDK-configuration lock.
   */
  @Serializable
  data class Autoconfigure(val toolId: @NonNls String) : PyInterpreterRef
}

/**
 * One `PyProject` as the frontend sees it — see [PyEvoSdkApi.pyProjects].
 *
 * Everything here is either identity ([key]) or display ([name]); nothing the frontend could get wrong by guessing.
 */
@ApiStatus.Internal
@Serializable
data class EvoPyProjectDto(
  /**
   * Wire identity: the `PyProject`'s base dir, in system-independent form, which is what every other call on
   * [PyEvoSdkApi] is addressed by. Chosen over the module name because it survives a module rename, and because the
   * frontend can match it against a content root's [com.intellij.openapi.vfs.VirtualFile.getPath] by plain string
   * equality — no path parsing, no VFS lookup, on either side.
   */
  val key: @NonNls String,
  /** The module's name — for the popup title only. Never an address; see [key]. */
  val name: @NlsSafe String,
  /**
   * Whether this `PyProject`'s base dir is the project's own base dir, i.e. whether the project *is* this Python
   * project. That is the one the widget speaks for when the focused file belongs to no module at all — a scratch, a
   * file dragged in from outside, or nothing focused. In PyCharm such a `PyProject` always exists (a plain Python
   * module is kept at the project root even when no `pyproject.toml` declares one); in IDEA it exists only when the
   * project root really is Python, which is exactly when the widget should answer for the project.
   */
  val isMain: Boolean,
  /**
   * [key] of the root of the tool workspace (uv/poetry) this takes part in, as its root or as a member; `null` when
   * standalone. Every member of a workspace shares the one environment declared at its root, so the backend resolves
   * every directory it works with against that root — this is what lets the popup name the workspace in its title.
   */
  val workspaceRootKey: @NonNls String? = null,
)

/**
 * Node ids that are not a tool's, declared here — the one module both sides of the RPC see — because a node id is a
 * wire value: the backend names a node and the frontend sends the same string back. A tool node's id is its
 * `ToolId`, which the backend resolves through its provider list; these are the ids no `ToolId` can supply, so they
 * are the ones at risk of being spelled twice and drifting.
 *
 * [ADVANCED] is the only one both sides use ([ASSOCIATED] and [SHORTCUTS] name frontend-synthetic nodes the backend
 * never dispatches on), but all three are reserved: a provider claiming one would shadow it, which is what the
 * backend's startup uniqueness check rejects.
 */
@ApiStatus.Internal
object EvoNodeIds {
  /** The "advanced" node — the full set of add-interpreter actions. Not a tool; the only backend-dispatched id here. */
  const val ADVANCED: @NonNls String = "advanced"

  /** Frontend-synthetic: the "Associated environments" node, whose rows are existing SDKs. */
  const val ASSOCIATED: @NonNls String = "associated"

  /** Frontend-synthetic: the "Shortcuts" autoconfigure rows. */
  const val SHORTCUTS: @NonNls String = "shortcuts"

  /** Every id above — the set a tool provider may not claim. */
  val RESERVED: Set<String> = setOf(ADVANCED, ASSOCIATED, SHORTCUTS)
}

/**
 * A collapsed expandable node in the popup's "select environment" section, contributed by a backend provider.
 *
 * [id] is the provider's `ToolId` string (or one of [EvoNodeIds]); the frontend passes it back verbatim to address
 * the node, so it is a wire value and never a display string — [label] is what the user reads.
 */
@ApiStatus.Internal
@Serializable
data class EvoNodeDto(
  val id: @NonNls String,
  val label: @Nls String,
  val icon: IconId,
)

/** One leaf row inside a loaded node. */
@ApiStatus.Internal
@Serializable
data class EvoLeafDto(
  val title: @Nls String,
  val description: @Nls String? = null,
  /**
   * A shortened form of [description] for the places this row is titled by its path rather than by its name — under a
   * version header in the expanded view, where the version is already written above it. Middle-elided the same way a
   * section header's folder path is, since a full interpreter path is wider than a popup row and widening the row would
   * push the whole popup off the widget. `null` when the row has no path to show.
   */
  val descriptionElided: @NlsSafe String? = null,
  val secondaryText: @Nls String? = null,
  val icon: IconId,
  val kind: EvoLeafKind,
  /** The interpreter this row selects, when [kind] is [EvoLeafKind.SELECT_ENV]. */
  val ref: PyInterpreterRef? = null,
  /**
   * For an [EvoLeafKind.ACTION] row that runs a backend action (e.g. an "Advanced" add-interpreter/target action):
   * an opaque id the backend maps back to that action in [PyEvoSdkApi.performNodeAction]. `null` → display-only row.
   */
  val actionId: String? = null,
  /**
   * When set, this row is a Python-version picker (hatch's not-yet-created declared envs): the frontend renders it as a
   * submenu of these versions instead of a plain row, and choosing one creates the env with that Python. The row's
   * [ref] ([PyInterpreterRef.CreateEnv]) carries the tool-specific create token (hatch: the env name), and each
   * option's token is the chosen base Python — passed back as [PyInterpreterRef.CreateEnv] `token`/`folder`.
   */
  val createVersions: List<EvoAddNewOptionDto>? = null,
  /**
   * Why this row cannot be acted on, when it cannot — a hatch environment declared in `pyproject.toml` with no
   * interpreter on the machine to build it from, say.
   *
   * The row is then shown disabled with a warning sign carrying this text, rather than looking selectable and failing
   * only once the user clicks it.
   */
  val unavailable: @Nls String? = null,
  /**
   * The Python version this row stands for, when it stands for a version rather than for one concrete environment —
   * poetry's per-version cache rows, each of which may already have an environment, may still need one, or may need the
   * interpreter installed first.
   *
   * In the expanded view it becomes the header the row sits under, so every version reads the same way regardless of
   * which of those three it is. `null` for a row that is already a concrete thing (an existing environment), which stays
   * a plain row under its own section's header.
   */
  val versionGroup: @NlsSafe String? = null,
  /**
   * For a [PyInterpreterRef.CreateEnv] row whose token *is* a base interpreter (poetry's per-version cache rows): the
   * other installs of that same version, so the row can offer the finer choice the same way an "add new" version row
   * does. Empty everywhere else — including a hatch declared env, whose token is an env name and not an interpreter.
   */
  val bases: List<EvoBasePythonDto> = emptyList(),
)

@ApiStatus.Internal
@Serializable
enum class EvoLeafKind {
  /** Selects an interpreter ([EvoLeafDto.ref] is set). */
  SELECT_ENV,

  /** A labeled, display-only action row (autoconfigure options, advanced add-interpreter actions, …). */
  ACTION,
}

@ApiStatus.Internal
@Serializable
data class EvoSectionDto(
  val label: @Nls String? = null,
  /**
   * The un-elided form of [label], shown as its tooltip. Section labels are folder paths shortened to a fixed budget
   * (`toSectionLabel`), so for a deeply nested folder the visible header is lossy and this is the only way back to the
   * real path. Null when there is nothing more to show than the label itself.
   */
  val labelTooltip: @NlsSafe String? = null,
  val leaves: List<EvoLeafDto>,
  /** When true, the frontend appends its localized "Add new environment" row (opens the modal Add dialog). */
  val addNew: Boolean = false,
  /**
   * In-widget "add new environment" flow for uv/pip. When set, the frontend replaces the modal [addNew] row with a
   * row that opens the add-new popup (location + version); when null it keeps the modal row.
   */
  val addNewEnv: EvoAddNewDto? = null,
  /**
   * Absolute path of the folder this section's environments live in (the group's containing folder, or the module
   * base dir for the ungrouped section). The backend uses it to seed the add-new location per folder.
   */
  val addNewFolderPath: @NonNls String? = null,
)

/** The in-widget "add new environment" flow for a section: the pre-filled target name and the Python versions. */
@ApiStatus.Internal
@Serializable
data class EvoAddNewDto(
  /** Pre-filled env name shown on the row and in the name field: the env folder name for uv/pip (e.g. `.venv`), the env name for conda. */
  val name: @NlsSafe String,
  /**
   * The base location passed back as [PyInterpreterRef.CreateEnv.folder]: for uv/pip the **containing dir** the env
   * folder is created in; for conda unused (the name is the env name). See [PyInterpreterRef.CreateEnv].
   */
  val path: @NonNls String,
  /** Version choices, best/default first (uv leads with its default; pip with the newest system Python). */
  val options: List<EvoAddNewOptionDto>,
  /**
   * When true, the add-new submenu shows an editable name field (pre-filled with [name]) on top and turns off speed
   * search so typing edits the name; the chosen version then creates the env with the edited name. When false the row
   * uses [name] as-is (e.g. poetry, whose in-project env is always `.venv`).
   */
  val nameEditable: Boolean = false,
  /**
   * Names already taken in the target location, so the name field can flag a collision (red + hint) and block creation.
   * For uv/pip this is **every existing entry** in the containing dir (not only virtualenvs), since any file/folder with
   * that name blocks creating the env there; for conda the existing env names are conveyed by the visible env rows.
   */
  val takenNames: List<@NlsSafe String> = emptyList(),
)

/** One selectable Python version for the in-widget "add new environment" flow. */
@ApiStatus.Internal
@Serializable
data class EvoAddNewOptionDto(
  /** Short version label, e.g. `3.13`; empty for uv's "default" (uv picks the version). */
  val title: @NlsSafe String,
  /** Tool-specific creation token passed back as [PyInterpreterRef.CreateEnv.token] (uv: version, empty = default; pip: python path). */
  val token: @NonNls String,
  /**
   * The individual interpreters this one version stands for, when the machine has several of it (a `pyenv` 3.12 and a
   * python.org 3.12), newest-listed first. [token] is the first of them — the one the row creates from when the user
   * does not choose — so this list is a refinement of that choice, never a replacement for it.
   *
   * Empty when there is nothing to choose: only tools that build an environment *from* an existing interpreter (pip,
   * poetry, hatch) can offer it. uv and conda provide the interpreter themselves, so their [token] is a version rather
   * than a path and a base interpreter is not a thing the user could pick.
   */
  val bases: List<EvoBasePythonDto> = emptyList(),
  /**
   * This version is not on the machine, but the IDE can install it — so the row offers to, the way the v2 "Add
   * Interpreter" dialog offers its download entries. [bases] is then empty (there is no install to choose between) and
   * [token] is the version to install rather than a path to one.
   *
   * Only set for tools that build an environment *from* an existing interpreter, and only where installing is possible
   * at all: uv and conda fetch their own interpreters, and a remote machine cannot be installed onto from here.
   */
  val installable: Boolean = false,
  /**
   * This version is not on the machine either, but the *tool* fetches it as part of creating the environment — uv, which
   * downloads whatever `--python` names when it does not find it.
   *
   * Reads the same as [installable] (download icon, "will be downloaded"), and deliberately does not share its flag: the
   * IDE must not run its own Python installer first, because the tool is about to do that job itself.
   */
  val downloadedByTool: Boolean = false,
)

/**
 * One concrete interpreter behind an [EvoAddNewOptionDto] — see [EvoAddNewOptionDto.bases].
 *
 * The path is the title because that is what actually tells two installs of the same version apart; the version is
 * already known from the option this belongs to.
 */
@ApiStatus.Internal
@Serializable
data class EvoBasePythonDto(
  /**
   * The interpreter binary for display: home-shortened and, past the row budget, middle-elided the same way a section
   * header's folder path is (`~/.cache/…/conda/Min…/envs/child/bin/python`). A full interpreter path is far wider than
   * a popup row, and widening the row would push the whole popup off the widget.
   */
  val title: @NlsSafe String,
  /** The un-elided form of [title], shown as the row's tooltip. `null` when [title] is already the whole path. */
  val titleTooltip: @NlsSafe String? = null,
  /**
   * The interpreter's version, patch included (`3.15.0`) — taken from the `--version` output the backend's scan already
   * ran, so it costs nothing here. Falls back to major.minor for an interpreter whose version was never captured.
   */
  val version: @NlsSafe String,
  /**
   * The icon of the tool this interpreter came from (uv, Homebrew, pyenv, …), which is how the v2 "Add Interpreter"
   * dialog distinguishes them — one glance instead of a word per row. `null` when the tool is unknown, and the frontend
   * then falls back to the plain Python logo.
   */
  val icon: IconId? = null,
  /**
   * What qualifies this interpreter beyond its version and its tool — currently only whether it is free-threaded, which
   * is a property of the build rather than of where it came from and so has no icon of its own. `null` when there is
   * nothing to add.
   */
  val qualifier: @NlsSafe String? = null,
  /** Creation token: this interpreter's binary path, passed back as [PyInterpreterRef.CreateEnv.token]. */
  val token: @NonNls String,
)

/** Result of [PyEvoSdkApi.loadNode]: sections on success, or a warning/critical error message. */
@ApiStatus.Internal
@Serializable
sealed interface EvoLoadResultDto {
  /**
   * [refreshable] is set by the backend when this tool's last scan was slow (> 5 s): such tools are cached for 10 min
   * and shown with an inline reload icon; fast tools stay uncached (covered by the frontend's short popup-tree cache).
   */
  @Serializable data class Ok(val sections: List<EvoSectionDto>, val refreshable: Boolean = false) : EvoLoadResultDto
  @Serializable data class Warning(val message: @Nls String) : EvoLoadResultDto
  @Serializable data class Error(val message: @Nls String) : EvoLoadResultDto
}

/** Result of [PyEvoSdkApi.selectInterpreter]. */
@ApiStatus.Internal
@Serializable
sealed interface EvoSelectResultDto {
  @Serializable data object Ok : EvoSelectResultDto
  @Serializable data class Error(val message: @Nls String) : EvoSelectResultDto
}
