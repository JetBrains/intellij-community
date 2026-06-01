// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui.configuration

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.ModuleManager
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.entities
import com.intellij.util.SlowOperations
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Version as PlatformVersion
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.PyToolsState
import com.intellij.python.pytools.Version
import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode
import com.intellij.python.pytools.findExecutableInPath
import com.intellij.python.pytools.findExecutableInSdk
import com.jetbrains.python.sdk.pyInterpreterPresentation
import com.intellij.python.pytools.ui.PyToolsUiBundle
import com.intellij.python.pytools.ui.icons.PythonPytoolsUIIcons
import com.jetbrains.python.Result
import com.intellij.python.pytools.validateCustomPath
import com.jetbrains.python.sdk.pyRichSdk
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Component
import java.nio.file.Path
import javax.swing.Icon

/** Snapshot of the user-editable per-row state, comparable to the persisted [PyToolsState] entry. */
internal data class RowState(
  val enabled: Boolean,
  val mode: ExecutableDiscoveryMode,
  val customPath: Path?,
)

internal class ToolRow(
  val tool: PyTool,
  var staged: RowState,
  var detail: UnnamedConfigurable? = null,
  var dirty: Boolean = false,
  /** Non-null when the most recent validation of [staged].customPath failed. */
  var pathError: String? = null,
  /** Currently-running validation coroutine; cancelled on the next edit. */
  var validationJob: Job? = null,
  /** Version reported by `<path> --version` for [versionedFor]; null if probe is pending or failed. */
  var version: Version? = null,
  /** Path for which [version] was probed. Used to skip re-probing the same binary on repaint. */
  var versionedFor: Path? = null,
  /**
   * Currently-detected path snapshot, populated asynchronously. `null` means the initial detection
   * is still in flight (the cell renders empty until then) — the renderer must never call [detect]
   * itself, since `findInPath` does blocking disk I/O.
   */
  var pathFieldValue: PathFieldValue? = null,
  /**
   * Non-null when the resolved binary's version is below [PyTool.minimumSupportedVersion]. The
   * string is a short human-readable hint suitable for the path tooltip; the renderer also uses
   * its presence as a signal to switch the path text to an attention color.
   */
  var belowMinVersionMessage: String? = null,
  /**
   * True between the moment a uv install/upgrade is kicked off on this row and the moment the
   * modal closes. While set, the hover action-icon slot renders a spinner frame instead of the
   * regular install/upgrade icon, so the user sees that the click registered before the modal
   * comes up.
   */
  var actionInProgress: Boolean = false,
  /**
   * Set after a successful `uv tool install` / `uv tool upgrade` on this row to a short status
   * message (e.g. "ruff upgraded to 0.15.6"). While non-null the hover action icon switches to
   * a ✓ that, when hovered, surfaces this message — giving the user a quiet but visible cue
   * that the action did something. Cleared on next panel show via [PyExternalToolsTable.onShown].
   */
  var lastSuccessMessage: String? = null,
  /**
   * Per-SDK detection result for the project's Python SDKs. `null` while the initial probe is
   * still in flight; non-null afterwards even when the project has no Python SDKs (the field
   * holds [SdkAvailability.NoProjectSdks] in that case).
   *
   * Surfaced in the Lookup column as a ✓ (all SDKs have it), ✗ (none have it), or ◐ (partial)
   * glyph next to `Sdk`, plus a tooltip listing the resolved binary path per SDK.
   */
  var sdkAvailability: SdkAvailability? = null,
)

/**
 * Project-SDK detection snapshot for one [ToolRow]: an ordered list of SDKs with the tool's
 * resolved binary path inside each (or `null` when the tool isn't installed in that SDK).
 */
internal data class SdkAvailability(val entries: List<SdkEntry>) {
  val totalCount: Int get() = entries.size
  val matchedCount: Int get() = entries.count { it.binaryPath != null }

  companion object {
    val NoProjectSdks: SdkAvailability = SdkAvailability(emptyList())
  }
}

/** A single project SDK plus the resolved binary path, or `null` when the SDK doesn't have it. */
internal data class SdkEntry(
  /** Short presentable label — the same one used elsewhere in the IDE for this SDK. */
  val sdkLabel: String,
  val binaryPath: Path?,
)

internal sealed interface PathFieldValue {
  /** User-supplied [PyToolsState.ToolEntry.customPathToExecutable]. */
  data class Custom(val path: Path) : PathFieldValue

  /** Path derived from system PATH. */
  data class AutoDetected(val path: Path) : PathFieldValue

  /** Neither configured nor discoverable. */
  data object NotFound : PathFieldValue
}

internal fun detect(tool: PyTool, customPath: Path?): PathFieldValue {
  if (customPath != null) return PathFieldValue.Custom(customPath)
  val auto = tool.findExecutableInPath()
  return if (auto != null) PathFieldValue.AutoDetected(auto) else PathFieldValue.NotFound
}

/**
 * Right-edge action icon kinds for the Path column. After a successful install / upgrade the
 * renderer paints a ✓ in this slot instead, driven by [ToolRow.lastSuccessMessage]; the ✓ path
 * is not modeled here because it is purely a visual swap and uses no different hit-test.
 */
internal enum class PathIconKind(val icon: Icon?) {
  NONE(null),
  INSTALL(PythonPytoolsUIIcons.Install),
  UPGRADE(PythonPytoolsUIIcons.Upgrade),
  RESET(AllIcons.Diff.Revert),
}

/**
 * Compute the hover-only icon for a Path cell given the row's current state. The function is
 * deliberately pure: the caller supplies the live uv-availability snapshot (`null` while
 * detection is in flight) and the "is this tool uv-managed" predicate, so the renderer doesn't
 * need to know how those are sourced.
 */
internal fun iconKindFor(
  toolRow: ToolRow?,
  detected: PathFieldValue?,
  uvAvailable: Boolean?,
  isUvManaged: (ToolRow) -> Boolean,
  isUpgradeAvailable: (ToolRow) -> Boolean,
): PathIconKind = when {
  toolRow == null -> PathIconKind.NONE
  // A manually-selected path overrides auto-detection entirely; the only meaningful hover
  // action there is "revert to auto-detection". Skip install / upgrade / info — none of them
  // apply to a user-pointed-at executable.
  detected is PathFieldValue.Custom -> PathIconKind.RESET
  detected is PathFieldValue.NotFound && uvAvailable == true -> PathIconKind.INSTALL
  detected is PathFieldValue.NotFound -> PathIconKind.NONE
  toolRow.version == null -> PathIconKind.NONE
  isUvManaged(toolRow) && isUpgradeAvailable(toolRow) -> PathIconKind.UPGRADE
  // Otherwise no actionable icon — the path text + version tooltip already conveys the state.
  else -> PathIconKind.NONE
}

/**
 * Resolve the row's path (via [detect]) and then probe `<path> --version`, fully on background
 * coroutines. Both steps post their results back to the EDT, mutating the row in place and
 * invoking [onUpdated] (on EDT) so the caller can refresh whatever UI surface reads the row.
 *
 * Replaces any previously-running probe via [ToolRow.validationJob]. When [isCustomEdit] is
 * true, surface validation errors for the just-edited custom path via [ToolRow.pathError]; on
 * non-custom probes (initial detection, post-install refresh) the error is left untouched so a
 * transient failure of `<path> --version` doesn't ghost in as if the user mistyped the path.
 */
internal fun ToolRow.probeVersion(
  scope: CoroutineScope,
  isCustomEdit: Boolean = false,
  onUpdated: (ToolRow) -> Unit,
) {
  validationJob?.cancel()
  val mode = staged.mode
  val customPath = staged.customPath
  validationJob = scope.launch {
    // Step 1: resolve the displayed path off the EDT — `findInPath` does disk I/O.
    val detected = withContext(Dispatchers.IO) {
      detect(tool, customPath)
    }
    val path = when (detected) {
      is PathFieldValue.Custom -> detected.path
      is PathFieldValue.AutoDetected -> detected.path
      PathFieldValue.NotFound -> null
    }

    // Step 2: publish the resolved path so the cell can render it before the version arrives.
    withContext(Dispatchers.Main) {
      if (staged.mode != mode || staged.customPath != customPath) return@withContext
      pathFieldValue = detected
      if (versionedFor != path) {
        version = null
        versionedFor = path
        belowMinVersionMessage = null
      }
      if (path == null) pathError = null
      onUpdated(this@probeVersion)
    }
    if (path == null) return@launch

    // Step 3: run `<path> --version` on background.
    val result = tool.validateCustomPath(path)
    val error = (result as? Result.Failure<*>)?.error?.toString()
    val resolvedVersion = (result as? Result.Success<*>)?.result as? Version

    // Step 4: publish the version (and any custom-edit error) on the EDT, but only if the
    // user input we probed against is still the staged value.
    withContext(Dispatchers.Main) {
      if (staged.mode != mode || staged.customPath != customPath) return@withContext
      if (versionedFor != path) return@withContext
      if (isCustomEdit) pathError = error
      version = resolvedVersion
      belowMinVersionMessage = computeBelowMinMessage(tool, resolvedVersion)
      onUpdated(this@probeVersion)
    }
  }
}

/**
 * Returns a localized "Below minimum" hint when [version] is older than [PyTool.minimumSupportedVersion],
 * or `null` if the tool declares no minimum, the probe hasn't completed yet, or the version is fine.
 * The pytools [Version] is a string wrapper; parse it through the platform's comparable Version.
 */
private fun computeBelowMinMessage(tool: PyTool, version: Version?): String? {
  val minimum = tool.minimumSupportedVersion ?: return null
  val actual = version?.value?.let { PlatformVersion.parseVersion(it) } ?: return null
  if (actual >= minimum) return null
  return PyToolsUiBundle.message(
    "settings.external.tools.path.below.minimum.tooltip",
    tool.presentableName,
    formatVersion(minimum),
    formatVersion(actual),
  )
}

private fun formatVersion(v: PlatformVersion): String =
  if (v.bugfix > 0) "${v.major}.${v.minor}.${v.bugfix}" else "${v.major}.${v.minor}"

/**
 * Open a single-file picker preselected to the row's current path (custom or auto-detected),
 * and on confirmation hand the chosen path off to [onPathChosen]. The caller is responsible
 * for routing the result back into the row's `staged.customPath` (typically via the path
 * column's `setValueAt` so the standard cell-edit flow — re-probe, validation, repaint —
 * takes over).
 */
internal fun ToolRow.browseExecutablePath(
  project: Project,
  parent: Component?,
  onPathChosen: (Path) -> Unit,
) {
  val current = staged.customPath ?: when (val d = pathFieldValue) {
    is PathFieldValue.Custom -> d.path
    is PathFieldValue.AutoDetected -> d.path
    else -> null
  }
  val toSelect = current?.let { VirtualFileManager.getInstance().findFileByNioPath(it) }
  val descriptor = FileChooserDescriptorFactory.singleFile()
    .withTitle(PyToolsUiBundle.message("select.path.to.executable"))
  descriptor.isForcedToUseIdeaFileChooser = true
  // The IDEA chooser does synchronous VFS lookups (UniversalFileChooser.toVirtualFiles →
  // VfsUtil.findFile) inside its EDT-bound modal loop, which trips the slow-ops assertion.
  // The lookup is unavoidable for converting the picked file back to a VirtualFile, and the
  // chooser keeps the EDT busy by design, so wrap the call in a known-issue suppression.
  SlowOperations.knownIssue("PY-89945").use {
    FileChooser.chooseFile(descriptor, project, parent, toSelect) { file ->
      onPathChosen(file.toNioPath())
    }
  }
}

/**
 * Lightweight read-action snapshot of a project Python SDK: the [com.intellij.openapi.projectRoots.Sdk]
 * itself plus its precomputed display label. Captured once per probe pass so the per-tool SDK
 * detection doesn't have to keep re-touching the project model.
 */
internal data class ProjectSdkSnapshot(
  val sdk: Sdk,
  val label: String,
)

/**
 * Take a snapshot of the project's Python SDKs. Modules are enumerated via the workspace-model
 * snapshot ([WorkspaceModel.currentSnapshot]) which is thread-safe without a read action; the
 * resulting list is unique and alphabetically ordered for stable downstream display.
 */
internal fun snapshotProjectSdks(project: Project): List<ProjectSdkSnapshot> {
  val moduleEntities = project.workspaceModel.currentSnapshot.entities<ModuleEntity>().toList()
  val moduleManager = ModuleManager.getInstance(project)
  return moduleEntities
    .mapNotNull { moduleManager.findModuleByName(it.name) }
    .mapNotNull { it.pythonSdk }
    .distinct()
    .sortedBy { it.name }
    .map { sdk -> ProjectSdkSnapshot(sdk, sdk.pyInterpreterPresentation().shortName) }
}

/**
 * Compute [SdkAvailability] for [this] tool against a previously-taken [snapshotProjectSdks]
 * result. Performs blocking I/O (SDK enrichment + executable existence checks), so callers
 * must run it off the EDT. Pure with respect to [this] — multiple tools can share the same
 * snapshot without re-touching the project model.
 */
internal fun PyTool.detectInSdks(snapshot: List<ProjectSdkSnapshot>): SdkAvailability {
  if (snapshot.isEmpty()) return SdkAvailability.NoProjectSdks
  return SdkAvailability(snapshot.map { sdk ->
    SdkEntry(sdkLabel = sdk.label, binaryPath = findExecutableInSdk(sdk.sdk.pyRichSdk()))
  })
}
