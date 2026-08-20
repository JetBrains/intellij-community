package com.intellij.python.sdk.common.evolution

import com.intellij.ide.ui.icons.IconId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

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
 * (display) plus a [PyInterpreterRef] (selection token). A module is referenced by [ProjectId] plus its
 * module name (resolved on the backend).
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
   * Whether the module is a Python one at all — a `PyProject`, i.e. a Python-typed module or one carrying a Python
   * facet. The widget hides itself entirely for anything else (a plain Java module in a mixed project), which it
   * cannot tell from [getCurrentInterpreter] alone: that returns `null` both here and for a Python module still
   * waiting for an interpreter, and only the latter may show "No interpreter".
   */
  suspend fun isPythonModule(projectId: ProjectId, moduleName: String): Boolean

  /** The Eel interpreter currently configured for the module, as display-ready data (or `null` if none). */
  suspend fun getCurrentInterpreter(projectId: ProjectId, moduleName: String): PyInterpreterDto?

  /**
   * The tool workspace (uv/poetry) the module takes part in — as its root or as a member — or `null` when it is
   * standalone. Every module of a workspace shares the one environment declared at its root, so the backend resolves
   * every directory it works with against that root; this tells the widget to name the workspace in its popup title.
   */
  suspend fun getWorkspace(projectId: ProjectId, moduleName: String): EvoWorkspaceDto?

  /**
   * The expandable nodes contributed by the backend `PyEvoEnvironmentProvider` extension point
   * (venv/uv/poetry/conda/hatch/advanced/…), filtered to the tools available on the module's Eel machine and shown
   * collapsed in the popup. Each tool-availability probe runs in that tool's
   * [com.jetbrains.python.TraceContext] under a transient "Python Interpreter Widget" root created for this call.
   */
  suspend fun listNodes(projectId: ProjectId, moduleName: String): List<EvoNodeDto>

  /**
   * The "Shortcuts" rows shown (in place of the current-interpreter actions) when the module has no interpreter: the
   * IDE's own setup suggestion for the module, computed by the same model-aware detector the "no interpreter
   * configured" inspection uses. Each row is a [PyInterpreterRef.Autoconfigure] leaf whose selection runs that
   * autoconfiguration. Empty when the module already has an interpreter or nothing can be suggested.
   */
  suspend fun listShortcuts(projectId: ProjectId, moduleName: String): List<EvoLeafDto>

  /**
   * Lazily loads the sections of the node with [nodeId] (from a backend provider) when it is expanded. Every
   * command this runs executes in the [nodeId] tool's [com.jetbrains.python.TraceContext] under the widget root
   * for [traceId] (the frontend's per-tree-build id, shared by all commands of one built popup tree).
   *
   * A `refreshable` tool's result is cached for 10 min; [forceRefresh] (from its reload icon) bypasses and refills
   * that cache. Non-refreshable tools are never cached here (the frontend's short-lived popup-tree cache covers them).
   */
  suspend fun loadNode(projectId: ProjectId, moduleName: String, nodeId: String, traceId: String, forceRefresh: Boolean): EvoLoadResultDto

  /**
   * The interpreters already configured for / assignable to the module — the same list the classic interpreter
   * widget shows. Rendered as a single "Associated environments" node; each is selectable by its SDK name, not
   * managed per-tool.
   */
  suspend fun listAssociatedInterpreters(projectId: ProjectId, moduleName: String): List<PyInterpreterDto>

  /**
   * Switches the module interpreter to the environment identified by [ref]. [nodeId] is the tool node the row came
   * from (`"uv"`, `"Poetry"`, `"Conda"`, `"Hatch"`, `"pip"`, `"associated"`), used to create a correctly-typed SDK:
   * an already-configured SDK ([PyInterpreterRef.ExistingSdk]) is assigned as-is; a detected env
   * ([PyInterpreterRef.DetectedPath]) or a not-yet-created env ([PyInterpreterRef.CreateEnv]) is created via that
   * tool's own "select existing"/"create" logic (the same the v2 Add dialog runs) and then assigned.
   */
  suspend fun selectInterpreter(projectId: ProjectId, moduleName: String, ref: PyInterpreterRef, nodeId: String): EvoSelectResultDto

  /**
   * Resolves the interpreter version (`python --version`) for the environment at [homePath], on demand — the
   * frontend calls this lazily as a row scrolls into view, so we never spawn a process per environment up front.
   * The probe runs in the [nodeId] tool's [com.jetbrains.python.TraceContext] (the same context the tool's env
   * listing used) under the "Python Interpreter Widget" root for [traceId], so it appears under that tool.
   */
  suspend fun resolveInterpreterVersion(projectId: ProjectId, moduleName: String, nodeId: String, homePath: String, traceId: String): String?

  /**
   * Opens the platform's "Add Python Interpreter" (v2) dialog for the module, preselecting the environment
   * manager of the node the "add new environment" row belongs to ([nodeId], e.g. `Conda` → conda, `uv` → uv) so
   * the user lands on the matching creator. On OK the created SDK is associated with the module and the widget
   * refreshes on the resulting `rootsChanged`.
   */
  suspend fun addInterpreter(projectId: ProjectId, moduleName: String, nodeId: String): EvoSelectResultDto

  /**
   * Runs the backend ACTION identified by [actionId] within node [nodeId] (e.g. an "Advanced" add-interpreter or
   * add-on-target action) — typically opening its dialog/wizard on the backend. When it creates an interpreter, the
   * SDK is associated with the module and the widget refreshes on the resulting `rootsChanged`.
   */
  suspend fun performNodeAction(projectId: ProjectId, moduleName: String, nodeId: String, actionId: String): EvoSelectResultDto

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
@ApiStatus.Internal
suspend fun requestEvoIsPythonModule(projectId: ProjectId, moduleName: String): Boolean =
  PyEvoSdkApi().isPythonModule(projectId, moduleName)

@ApiStatus.Internal
suspend fun requestEvoCurrentInterpreter(projectId: ProjectId, moduleName: String): PyInterpreterDto? =
  PyEvoSdkApi().getCurrentInterpreter(projectId, moduleName)

@ApiStatus.Internal
suspend fun requestEvoWorkspace(projectId: ProjectId, moduleName: String): EvoWorkspaceDto? =
  PyEvoSdkApi().getWorkspace(projectId, moduleName)

@ApiStatus.Internal
suspend fun requestEvoNodes(projectId: ProjectId, moduleName: String): List<EvoNodeDto> =
  PyEvoSdkApi().listNodes(projectId, moduleName)

@ApiStatus.Internal
suspend fun requestEvoShortcuts(projectId: ProjectId, moduleName: String): List<EvoLeafDto> =
  PyEvoSdkApi().listShortcuts(projectId, moduleName)

@ApiStatus.Internal
suspend fun requestEvoNode(projectId: ProjectId, moduleName: String, nodeId: String, traceId: String, forceRefresh: Boolean = false): EvoLoadResultDto =
  PyEvoSdkApi().loadNode(projectId, moduleName, nodeId, traceId, forceRefresh)

@ApiStatus.Internal
suspend fun requestEvoAssociatedInterpreters(projectId: ProjectId, moduleName: String): List<PyInterpreterDto> =
  PyEvoSdkApi().listAssociatedInterpreters(projectId, moduleName)

@ApiStatus.Internal
suspend fun requestEvoSelectInterpreter(projectId: ProjectId, moduleName: String, ref: PyInterpreterRef, nodeId: String): EvoSelectResultDto =
  PyEvoSdkApi().selectInterpreter(projectId, moduleName, ref, nodeId)

@ApiStatus.Internal
suspend fun requestEvoResolveVersion(projectId: ProjectId, moduleName: String, nodeId: String, homePath: String, traceId: String): String? =
  PyEvoSdkApi().resolveInterpreterVersion(projectId, moduleName, nodeId, homePath, traceId)

@ApiStatus.Internal
suspend fun requestEvoAddInterpreter(projectId: ProjectId, moduleName: String, nodeId: String): EvoSelectResultDto =
  PyEvoSdkApi().addInterpreter(projectId, moduleName, nodeId)

@ApiStatus.Internal
suspend fun requestEvoPerformNodeAction(projectId: ProjectId, moduleName: String, nodeId: String, actionId: String): EvoSelectResultDto =
  PyEvoSdkApi().performNodeAction(projectId, moduleName, nodeId, actionId)

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
  data class CreateEnv(val token: @NonNls String, val folder: @NonNls String? = null, val name: @NonNls String? = null) : PyInterpreterRef

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
 * The tool workspace a module takes part in — see [PyEvoSdkApi.getWorkspace]. Named in the popup title, since the
 * environments the widget lists are the workspace's, not the module's own.
 */
@ApiStatus.Internal
@Serializable
data class EvoWorkspaceDto(
  /**
   * Name of the module the workspace is rooted at (whose base dir the widget works in). Equal to the queried module's
   * own name when that module *is* the root.
   */
  val rootModuleName: @NlsSafe String,
)

/** A collapsed expandable node in the popup's "select environment" section, contributed by a backend provider. */
@ApiStatus.Internal
@Serializable
data class EvoNodeDto(
  val id: String,
  val label: @Nls String,
  val icon: IconId,
)

/** One leaf row inside a loaded node. */
@ApiStatus.Internal
@Serializable
data class EvoLeafDto(
  val title: @Nls String,
  val description: @Nls String? = null,
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
