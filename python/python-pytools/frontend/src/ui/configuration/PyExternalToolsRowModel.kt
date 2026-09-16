// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.frontend.ui.configuration

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version
import com.intellij.platform.project.projectId
import com.intellij.python.pytools.common.PyToolApi
import com.intellij.python.pytools.common.PyToolConfigurationDto
import com.intellij.python.pytools.common.PyToolDescriptorDto
import com.intellij.python.pytools.common.PyToolPathKind
import com.intellij.python.pytools.common.PyToolPathRequest
import com.intellij.python.pytools.common.PyToolPathStateDto
import com.intellij.python.pytools.common.PyToolRequest
import com.intellij.python.pytools.common.PyToolSdkStateDto
import com.intellij.python.pytools.common.PyToolStateDto
import com.intellij.python.pytools.common.PyToolValidationDto
import com.intellij.python.pytools.common.PyToolsRequest
import com.intellij.python.pytools.frontend.PyToolFrontend as PyTool
import com.intellij.python.pytools.frontend.ui.PyToolsUiBundle
import com.intellij.python.pytools.frontend.ExternalPyToolFrontend as ExternalPyTool
import com.intellij.python.pytools.frontend.icons.PythonPytoolsFrontendIcons
import com.intellij.util.SlowOperations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.awt.Component
import javax.swing.Icon

/**
 * Snapshot of the user-editable per-row state.
 * The executable-discovery mode is no longer user-selectable — the page always runs the fixed
 * `SDK → Path → uvx` chain — so only the enable flag and the optional custom-path override are staged here.
 */
internal data class RowState(
  val enabled: Boolean,
  val customPath: String?,
)

internal class ToolRow(
  val tool: PyTool,
  var staged: RowState,
  var persistedEnabled: Boolean = false,
  var persistedCustomPath: String? = null,
  var detail: UnnamedConfigurable? = null,
  /** Non-null when the most recent validation of [staged].customPath failed. */
  var pathError: String? = null,
  /** Currently-running validation coroutine; cancelled on the next edit. */
  var validationJob: Job? = null,
  /** Version of the resolved executable; null while [versionLoaded] is false, or when the probe found none. */
  var version: Version? = null,
  /**
   * True once a version answer has arrived for this row, even a `null` one. The version comes after the
   * path, and for a file the tool manager does not know it costs a process, so this separates "the version
   * is still on its way" from "there is none".
   */
  var versionLoaded: Boolean = false,
  /**
   * The path [version] belongs to. A version is an answer about one file, so a state that resolves another
   * path drops it, while a state that carries no version of its own leaves the known one alone.
   */
  var versionedFor: String? = null,
  /**
   * Currently-detected path snapshot, populated asynchronously. `null` means the initial detection
   * is still in flight, so the cell renders the spinner of [pathValueLabel] until then.
   */
  var pathFieldValue: PathFieldValue? = null,
  /**
   * Non-null when the resolved binary's version is below [minimumSupportedVersion]. The
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
   * that the action did something. Cleared on next panel show via [PyExternalToolsList.onShown].
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
  var canInstall: Boolean = false,
  var latestVersion: Version? = null,
  var minimumSupportedVersion: Version? = null,
  var descriptor: PyToolDescriptorDto = PyToolDescriptorDto(),
  var configuration: PyToolConfigurationDto? = null,
  var selectedAsTypeEngine: Boolean = false,
) {
  val detailConfigurableProvider: ExternalPyTool<*>? = tool as? ExternalPyTool<*>
}

/**
 * Project-SDK detection snapshot for one [ToolRow]: an ordered list of SDKs with the tool's
 * resolved binary path inside each (or `null` when the tool isn't installed in that SDK).
 */
internal data class SdkAvailability(val entries: List<PyToolSdkStateDto>) {
  val totalCount: Int get() = entries.size
  val matchedCount: Int get() = entries.count { it.path != null }

  companion object {
    val NoProjectSdks: SdkAvailability = SdkAvailability(emptyList())
  }
}

internal sealed interface PathFieldValue {
  /** A user-supplied custom executable path (stored per Eel machine in `PyCustomExecutablePaths`). */
  data class Custom(val path: String) : PathFieldValue

  /** Path auto-detected on PATH or in a well-known per-user install directory. */
  data class AutoDetected(val path: String) : PathFieldValue

  /** Neither configured nor discoverable. */
  data object NotFound : PathFieldValue
}

/**
 * Right-edge action icon kinds for the Path column. After a successful install / upgrade the
 * renderer paints a ✓ in this slot instead, driven by [ToolRow.lastSuccessMessage]; the ✓ path
 * is not modeled here because it is purely a visual swap and uses no different hit-test.
 */
internal enum class PathIconKind(val icon: Icon?) {
  NONE(null),
  INSTALL(PythonPytoolsFrontendIcons.Install),
  UPGRADE(PythonPytoolsFrontendIcons.Upgrade),
  RESET(AllIcons.Diff.Revert),
}

/**
 * Compute the hover-only icon for a Path cell given the row's current state. The function is
 * deliberately pure: the caller supplies the "is an upgrade available" predicate, so the renderer
 * doesn't need to know how it is sourced.
 */
internal fun iconKindFor(
  toolRow: ToolRow?,
  detected: PathFieldValue?,
  canInstall: Boolean,
  isUpgradeAvailable: (ToolRow) -> Boolean,
): PathIconKind = when {
  toolRow == null -> PathIconKind.NONE
  // A manually-selected path overrides auto-detection entirely; the only meaningful hover
  // action there is "revert to auto-detection". Skip install / upgrade / info — none of them
  // apply to a user-pointed-at executable.
  detected is PathFieldValue.Custom -> PathIconKind.RESET
  // No installer for this tool on this target (a manager-less tool, or e.g. conda on a remote
  // interpreter): path-only, just the browse button. (Reset above still applies to a custom path.)
  !canInstall -> PathIconKind.NONE
  // Offer install for any undiscovered tool; the installer uses the tool's manager (uv/pip by default).
  detected is PathFieldValue.NotFound -> PathIconKind.INSTALL
  toolRow.version == null -> PathIconKind.NONE
  isUpgradeAvailable(toolRow) -> PathIconKind.UPGRADE
  // Otherwise no actionable icon — the path text + version tooltip already conveys the state.
  else -> PathIconKind.NONE
}

/**
 * Request the row state from the backend and update the row in a coroutine. For a custom path,
 * validate the path first. Invoke [onUpdated] after the state changes.
 *
 * Replaces any previously-running probe via [ToolRow.validationJob]. When [isCustomEdit] is
 * true, surface validation errors for the just-edited custom path via [ToolRow.pathError]; on
 * non-custom probes (initial detection, post-install refresh) the error is left untouched so a
 * transient failure of `<path> --version` doesn't ghost in as if the user mistyped the path.
 */
internal fun ToolRow.probeVersion(
  scope: CoroutineScope,
  project: Project,
  isCustomEdit: Boolean = false,
  onUpdated: (ToolRow) -> Unit,
) {
  validationJob?.cancel()
  val customPath = staged.customPath
  validationJob = scope.launch {
    if (isCustomEdit && customPath != null) {
      pathFieldValue = PathFieldValue.Custom(customPath)
      versionLoaded = false
      versionedFor = customPath
      when (val result = PyToolApi.getInstance().validatePath(
        PyToolPathRequest(PyToolRequest(project.projectId(), tool.toolId), customPath),
      )) {
        is PyToolValidationDto.Valid -> {
          pathError = null
          version = result.version?.let(Version::parseVersion)
        }
        is PyToolValidationDto.Invalid -> {
          pathError = result.message
          version = null
        }
      }
      belowMinVersionMessage = computeBelowMinMessage(tool, minimumSupportedVersion, version)
      versionLoaded = true
      onUpdated(this@probeVersion)
      return@launch
    }
    val state = PyToolApi.getInstance().getStates(
      PyToolsRequest(project.projectId(), listOf(tool.toolId)),
    ).singleOrNull() ?: return@launch
    applyBackendState(state, updateStagedPath = !isCustomEdit)
    onUpdated(this@probeVersion)
  }
}

/**
 * Fill the row's path from the cheap paths-only backend call, and report whether it did.
 *
 * The full state call runs at the same time and carries the version, the manager data, and the
 * enable flag. This call only fills the initial hole, so a row that already knows its path, or that
 * the user has edited, keeps what it has whichever answer lands first.
 */
internal fun ToolRow.applyBackendPath(state: PyToolPathStateDto): Boolean {
  if (pathFieldValue != null) return false
  val path = state.path
  val customPath = when (path?.kind) {
    PyToolPathKind.CUSTOM -> path.value
    PyToolPathKind.DETECTED, null -> null
  }
  persistedCustomPath = customPath
  staged = staged.copy(customPath = customPath)
  pathFieldValue = when (path?.kind) {
    PyToolPathKind.CUSTOM -> PathFieldValue.Custom(path.value)
    PyToolPathKind.DETECTED -> PathFieldValue.AutoDetected(path.value)
    null -> PathFieldValue.NotFound
  }
  return true
}

/**
 * Fetch the version of the row's resolved executable, unless the row already has an answer.
 *
 * Kept apart from the state on purpose: for a file the tool manager does not know this runs
 * `<path> --version`, so it happens where the version is shown — on the Package Managers page for every
 * row, and on the External Tools page when the user expands a row.
 */
internal fun ToolRow.loadVersion(scope: CoroutineScope, project: Project, onUpdated: (ToolRow) -> Unit) {
  val resolvedPath = pathFieldValue.pathOrNull()
  if (versionLoaded && versionedFor == resolvedPath) return
  scope.launch {
    val reported = PyToolApi.getInstance().getVersion(PyToolRequest(project.projectId(), tool.toolId))
    version = reported?.let(Version::parseVersion)
    versionLoaded = true
    versionedFor = pathFieldValue.pathOrNull()
    belowMinVersionMessage = computeBelowMinMessage(tool, minimumSupportedVersion, version)
    onUpdated(this@loadVersion)
  }
}

/** The resolved executable path, or `null` while none is resolved. */
internal fun PathFieldValue?.pathOrNull(): String? = when (this) {
  is PathFieldValue.Custom -> path
  is PathFieldValue.AutoDetected -> path
  PathFieldValue.NotFound, null -> null
}

internal fun ToolRow.applyBackendState(
  state: PyToolStateDto,
  updateStagedPath: Boolean = false,
  updateStagedEnabled: Boolean = false,
) {
  persistedEnabled = state.enabled
  if (updateStagedEnabled) staged = staged.copy(enabled = state.enabled)
  val path = state.path
  val customPath = when (path?.kind) {
    PyToolPathKind.CUSTOM -> path.value
    PyToolPathKind.DETECTED, null -> null
  }
  persistedCustomPath = customPath
  if (updateStagedPath) staged = staged.copy(customPath = customPath)
  pathFieldValue = when (path?.kind) {
    PyToolPathKind.CUSTOM -> PathFieldValue.Custom(path.value)
    PyToolPathKind.DETECTED -> PathFieldValue.AutoDetected(path.value)
    null -> PathFieldValue.NotFound
  }
  // A state carries a version only when the tool manager already knew it. One that carries none says nothing
  // about the version, so it may not drop an answer this row already has for the same file.
  val reported = state.version
  when {
    reported != null -> {
      version = Version.parseVersion(reported)
      versionLoaded = true
      versionedFor = path?.value
    }
    versionedFor != path?.value -> {
      version = null
      versionLoaded = false
      versionedFor = null
    }
  }
  pathError = null
  descriptor = state.descriptor
  minimumSupportedVersion = descriptor.minimumSupportedVersion?.let(Version::parseVersion)
  belowMinVersionMessage = computeBelowMinMessage(tool, minimumSupportedVersion, version)
  canInstall = state.canInstall
  latestVersion = state.latestVersion?.let(Version::parseVersion)
  configuration = state.configuration
  selectedAsTypeEngine = state.selectedAsTypeEngine
}

/**
 * Returns a localized "Below minimum" hint when [version] is older than [ToolRow.minimumSupportedVersion],
 * or `null` if the tool declares no minimum, the probe hasn't completed yet, or the version is fine.
 * Parse the backend version string through the platform's comparable version type.
 */
private fun computeBelowMinMessage(tool: PyTool, minimum: Version?, version: Version?): String? {
  minimum ?: return null
  version ?: return null
  if (version >= minimum) return null
  return PyToolsUiBundle.message(
    "settings.external.tools.path.below.minimum.tooltip",
    tool.presentableName,
    formatVersion(minimum),
    formatVersion(version),
  )
}

private fun formatVersion(v: Version): String =
  if (v.bugfix > 0) "${v.major}.${v.minor}.${v.bugfix}" else "${v.major}.${v.minor}"

/**
 * Open a single-file picker preselected to the row's current path (custom or auto-detected),
 * and on confirmation hand the chosen path off to [onPathChosen]. The caller is responsible
 * for routing the result back into the row's `staged.customPath` (typically via the path
 * column's `setValueAt` so the standard cell-edit flow — re-probe, validation, repaint —
 * takes over).
 */
internal fun browseExecutablePath(
  project: Project,
  parent: Component?,
  onPathChosen: (String) -> Unit,
) {
  val descriptor = FileChooserDescriptorFactory.singleFile()
    .withTitle(PyToolsUiBundle.message("select.path.to.executable"))
  descriptor.isForcedToUseIdeaFileChooser = true
  // The IDEA chooser does synchronous VFS lookups (UniversalFileChooser.toVirtualFiles →
  // VfsUtil.findFile) inside its EDT-bound modal loop, which trips the slow-ops assertion.
  // The lookup is unavoidable for converting the picked file back to a VirtualFile, and the
  // chooser keeps the EDT busy by design, so wrap the call in a known-issue suppression.
  SlowOperations.knownIssue("PY-89945").use {
    FileChooser.chooseFile(descriptor, project, parent, null) { file ->
      onPathChosen(file.path)
    }
  }
}
