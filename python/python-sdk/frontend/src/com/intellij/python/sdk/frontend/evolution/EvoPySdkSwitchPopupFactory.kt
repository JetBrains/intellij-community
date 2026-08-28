package com.intellij.python.sdk.frontend.evolution

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ActionUtil.getAction
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.project.projectId
import com.intellij.psi.PsiManager
import com.intellij.python.sdk.common.evolution.EvoAddNewOptionDto
import com.intellij.python.sdk.common.evolution.EvoBasePythonDto
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.common.evolution.EvoLeafKind
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoNodeDto
import com.intellij.python.sdk.common.evolution.EvoNodeIds
import com.intellij.python.sdk.common.evolution.EvoPyProjectDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.intellij.python.sdk.common.evolution.PyInterpreterDto
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
import com.intellij.python.sdk.common.evolution.EvoNodeKind
import com.intellij.python.sdk.common.evolution.EvoNodeStats
import com.intellij.python.sdk.common.evolution.PyEvoWidgetCollector
import com.intellij.python.sdk.common.evolution.evoRpc
import com.intellij.python.sdk.common.evolution.evoRpcOrNull
import com.intellij.python.sdk.common.evolution.requestEvoNode
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
import com.intellij.python.sdk.frontend.evolution.components.EvoEditableName
import com.intellij.python.sdk.frontend.evolution.components.EvoErrorException
import com.intellij.python.sdk.frontend.evolution.components.EvoLoadedNode
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeActionLeafElement
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeAddNewNode
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeElement
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeLazyNodeElement
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeLeafElement
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeNodeElement
import com.intellij.python.sdk.frontend.evolution.components.EvoTreePopup
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeSection
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeStaticNodeElement
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeUnavailableLeafElement
import com.intellij.python.sdk.frontend.evolution.components.EvoVersionRows
import com.intellij.python.sdk.frontend.evolution.components.EvoWarningException
import com.intellij.python.sdk.frontend.icons.PythonSdkFrontendIcons
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.Icon
import com.intellij.python.sdk.common.evolution.requestEvoShowToolProcessOutput
import com.intellij.openapi.application.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.intellij.openapi.util.text.StringUtil

private val managePackagesAction = object : AnAction(
  { PySdkFrontendBundle.message("evo.sdk.python.packaging.interpreter.widget.manage.packages") },
  { "" },
  PythonSdkFrontendIcons.PythonPackages,
), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let {
      PyEvoWidgetCollector.controlUsed(it, PyEvoWidgetCollector.Control.MANAGE_PACKAGES)
      ToolWindowManager.getInstance(it).getToolWindow("Python Packages")?.show()
    }
  }
}

/** Backend id of the `advanced` node (`AdvancedEvoEnvironmentProvider`), the anchor for the target-interpreters node. */
private const val ADVANCED_NODE_ID: String = EvoNodeIds.ADVANCED

/** Synthetic node id for the frontend-only "Associated environments" node (its rows are existing SDKs, never version-probed). */
/**
 * The Python Process Output tool window, opened by a failed row's sign when the backend could not address the run's
 * process. A literal because the constant declaring it is internal to the process-output module — the packaging tool
 * window is reached the same way elsewhere in this plugin.
 */
/**
 * Longest environment name a row shows before its middle is elided, with the whole of it moving to the row's tooltip.
 *
 * The same budget a section header's path gets (`toSectionLabel`), so a name and a path are cut to one width.
 */
private const val ROW_TITLE_MAX_CHARS: Int = 50

private const val PROCESS_OUTPUT_TOOL_WINDOW_ID: String = "PythonProcessOutput"

private const val ASSOCIATED_NODE_ID: String = EvoNodeIds.ASSOCIATED

private fun EvoLeafDto.toStubAction(): AnAction = object : AnAction({ title }, { description ?: "" }, icon.icon()), DumbAware {
  init {
    templatePresentation.setPlainText(title)
    secondaryText?.let { templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT, it) }
  }

  override fun actionPerformed(e: AnActionEvent) {}
}

/** Synthetic node id for the "Shortcuts" autoconfigure rows (the backend ignores it for a [PyInterpreterRef.Autoconfigure] ref). */
private const val SHORTCUTS_NODE_ID: String = EvoNodeIds.SHORTCUTS

/** The platform group holding every tool's package-manager actions (uv lock/sync, conda export/update, …). */
private const val PACKAGE_MANAGER_ACTIONS_GROUP: String = "PythonPackageManagerActions"

/**
 * Puts [value] under [key], or masks [key] with an explicit null when [value] is absent.
 *
 * [SimpleDataContext.Builder.add] silently ignores a null value, which leaves the key falling through to the parent
 * context — the opposite of what a caller supplying null means. This states the absence instead.
 */
private fun <T : Any> SimpleDataContext.Builder.addOrNull(key: DataKey<T>, value: T?): SimpleDataContext.Builder =
  if (value == null) addNull(key) else add(key, value)

internal class EvoPySdkSwitchPopupFactory(
  val project: Project,
  /** Wire identity of the `PyProject` every backend call below is addressed to — see [EvoPyProjectDto.key]. */
  val pyProjectKey: @NonNls String,
  /** Its display name — the popup title, never an address. */
  val displayName: @NlsSafe String,
  /** Display name of the workspace it takes part in, or `null` when it is standalone — see [popupTitle]. */
  val workspaceRootName: @NlsSafe String?,
  val currentInterpreter: PyInterpreterDto?,
  val nodes: List<EvoNodeDto>,
  val associated: List<PyInterpreterDto>,
  /** The "Shortcuts" rows shown when there is no current interpreter — the IDE's autoconfigure suggestion(s). */
  val shortcuts: List<EvoLeafDto>,
  val scope: CoroutineScope,
  /**
   * Shows the widget's popup again, over the tree it already built — how the "More Tools" row reveals the rest.
   *
   * A popup is laid out and placed once, so a list this much bigger has to be reopened rather than grown in place; the
   * widget owns the anchoring, and reopening within its tree-reuse window costs no rescan.
   */
  val reopenPopup: () -> Unit,
) {
  /**
   * The statistics identity of [nodeId] among the nodes this popup was built from.
   *
   * The two frontend-synthetic sections have no DTO to read it off, so they name themselves; anything else the backend
   * did not send is reported as unknown rather than guessed at.
   */
  private fun nodeStats(nodeId: String): EvoNodeStats = when (nodeId) {
    ASSOCIATED_NODE_ID -> EvoNodeStats(EvoNodeKind.ASSOCIATED)
    SHORTCUTS_NODE_ID -> EvoNodeStats(EvoNodeKind.SHORTCUTS)
    else -> nodes.firstOrNull { it.id == nodeId }?.let { EvoNodeStats.of(it) } ?: EvoNodeStats(EvoNodeKind.OTHER)
  }

  /**
   * A leaf row, with a long environment name middle-elided and the whole of it on hover.
   *
   * Every row of every tool passes through here, which is why the eliding sits here rather than in each of the backend's
   * leaf builders: one place decides how wide a row may be. A name is cut the way a section header's path already is —
   * a row wider than the popup widens the popup, and it is anchored on the widget, so it grows away from the screen.
   *
   * The tooltip is set only where the cut actually dropped something, so a name that fits is not given a tooltip
   * repeating what is already on the row — and never over a tooltip the row already has: a row that cannot be acted on
   * carries the reason why, which matters more than the rest of its name. A row titled by its path ([asPathTitledRow])
   * replaces it afterwards with the full path, which is the more useful of the two there.
   */
  private fun EvoLeafDto.toElement(nodeId: String, traceId: String): EvoTreeElement {
    val elided = StringUtil.trimMiddle(title, ROW_TITLE_MAX_CHARS)
    val element = (if (elided == title) this else copy(title = elided)).toRowElement(nodeId, traceId)
    if (elided != title && element.presentation.getClientProperty(ActionUtil.TOOLTIP_TEXT) == null) {
      element.presentation.putClientProperty(ActionUtil.TOOLTIP_TEXT, title)
    }
    return element
  }

  private fun EvoLeafDto.toRowElement(nodeId: String, traceId: String): EvoTreeElement {
    // Checked first: a row that cannot be acted on has no version picker to offer either.
    unavailable?.let { reason ->
      return EvoTreeUnavailableLeafElement(selectEnvAction(project, pyProjectKey, this, nodeId, nodeStats(nodeId), traceId, scope), reason)
    }
    // A version-picker row (hatch not-yet-created env): a node whose submenu is the versions; choosing one creates the
    // env with that Python. The row's ref (CreateEnv) carries the per-row create token (hatch: the env name) → folder.
    val versions = createVersions
    if (!versions.isNullOrEmpty()) {
      val createToken = (ref as? PyInterpreterRef.CreateEnv)?.token.orEmpty()
      return EvoTreeAddNewNode(
        text = title,
        icon = icon.icon(),
        versionRows = versionRows(
          versions,
          { addVersionAction(nodeId, it, createToken) },
          { base ->
            baseInterpreterRow(base) {
              createEnv(nodeId, base.token, createToken, null, null, source = PyEvoWidgetCollector.Source.EXPANDED_VERSION)
            }
          },
          { option ->
            installActionRow {
              createEnv(nodeId, option.token, createToken, null, null,
                        source = PyEvoWidgetCollector.Source.INSTALL_ROW, installVersion = option.installVersion())
            }
          },
        ),
        // The env this is picking a base for, for the submenu's header. Declared in pyproject.toml, so it is shown
        // rather than offered for editing — unlike the name uv and pip make up for a folder they are about to create.
        fixedName = title,
        // There is no interpreter to probe yet, so the row carries the backend's "n/a" in the same column where the
        // already-materialized envs show their resolved version.
        secondaryText = secondaryText,
      )
    }
    return when (kind) {
      EvoLeafKind.SELECT_ENV -> EvoTreeLeafElement(selectEnvAction(project, pyProjectKey, this, nodeId, nodeStats(nodeId), traceId, scope))
      // A runnable backend action (advanced add-interpreter) carries an actionId; a display-only row stays a no-op stub.
      EvoLeafKind.ACTION -> EvoTreeLeafElement(
        if (actionId != null) evoBackendActionLeaf(project, pyProjectKey, nodeId, this, scope) else toStubAction()
      )
    }
  }

  private fun EvoLoadResultDto.toSections(nodeId: String, traceId: String): List<EvoTreeSection> = when (this) {
    is EvoLoadResultDto.Ok -> {
      // Names already in use across this tool's node (existing envs) — the add-new field rejects these (red, no create).
      val takenNames = sections.flatMap { it.leaves }.mapTo(mutableSetOf()) { it.title }
      sections.map { section ->
        val leaves = section.leaves.map { it.toElement(nodeId, traceId) }
        EvoTreeSection(
          label = section.label?.let { ListSeparator(it) },
          elements = leaves + listOfNotNull(addNewElement(section, nodeId, takenNames)),
          // Only worth a tooltip when the header was actually shortened — otherwise it would just repeat what is on screen.
          labelTooltip = section.labelTooltip?.takeIf { it != section.label },
        )
      }
    }
    is EvoLoadResultDto.Warning -> throw EvoWarningException(message)
    is EvoLoadResultDto.Error -> throw EvoErrorException(message)
  }

  /**
   * The node's sections restructured so that every row standing for several interpreters becomes a header over them —
   * the expanded half of [EvoVersionRows] for a *tool* node, the way the add-new version list already has one. Poetry's
   * per-version cache rows are what this is for: each is a Python version that may have more than one install behind it.
   *
   * Empty when nothing here has more than one interpreter behind it, which is what tells the caller not to offer the
   * toggle at all.
   *
   * Rows that stand only for themselves (an existing environment) keep their original section and its header, and are
   * emitted before the expanded ones. That reordering is only theoretical for the tools that send interpreters today —
   * poetry's per-version section is entirely expandable rows, and its in-project section entirely plain ones.
   */
  private fun EvoLoadResultDto.toExpandedSections(nodeId: String, traceId: String): List<EvoTreeSection> {
    if (this !is EvoLoadResultDto.Ok) return emptyList()
    if (sections.none { section -> section.leaves.any { it.bases.size > 1 } }) return emptyList()
    val takenNames = sections.flatMap { it.leaves }.mapTo(mutableSetOf()) { it.title }
    return sections.flatMap { section -> section.toExpandedSections(nodeId, traceId, takenNames) }
  }

  /**
   * One section's expanded form: every row that stands for a Python version ([EvoLeafDto.versionGroup]) becomes a header
   * over what that version offers — the interpreters it could be built from, the single "download and install" row when
   * the version is not on the machine, or the environment it already has.
   *
   * All three get a header, so the list does not alternate between headed groups and bare rows depending on which case a
   * version happens to be in. Rows that are not versions at all (an existing environment in its own right) stay plain
   * under the section's own header, and that group is flushed whenever a version header interrupts it — which is what
   * keeps every version in the order it arrived in.
   */
  private fun EvoSectionDto.toExpandedSections(nodeId: String, traceId: String, takenNames: Set<String>): List<EvoTreeSection> {
    val out = mutableListOf<EvoTreeSection>()
    var plain = mutableListOf<EvoTreeElement>()
    // The section's own header belongs to the first plain group; later ones have nothing left to be titled by.
    var plainLabel: @Nls String? = label
    fun flushPlain() {
      if (plain.isEmpty()) return
      out += EvoTreeSection(
        label = plainLabel?.let { ListSeparator(it) },
        elements = plain.toList(),
        labelTooltip = labelTooltip?.takeIf { it != label && plainLabel == label },
      )
      plain = mutableListOf()
      plainLabel = null
    }
    for (leaf in leaves) {
      val header = leaf.versionGroup
      if (header == null) {
        plain += leaf.toElement(nodeId, traceId)
        continue
      }
      val rows = installInterpreterRow(project, pyProjectKey, leaf, nodeId, nodeStats(nodeId), scope)?.let { listOf(it) }
                 ?: baseInterpreterRows(project, pyProjectKey, leaf, nodeId, nodeStats(nodeId), scope).takeIf { it.isNotEmpty() }
                 // Nothing to choose between: the version already has its environment, so the row is that environment.
                 ?: listOf(leaf.asPathTitledRow(nodeId, traceId))
      flushPlain()
      out += EvoTreeSection(label = ListSeparator(header), elements = rows)
    }
    // The add-new row closes the section, as it does in the collapsed form.
    plain += listOfNotNull(addNewElement(this, nodeId, takenNames))
    flushPlain()
    return out
  }

  /**
   * A version row as it reads *under* its own version header: titled by what distinguishes it from the header — its
   * interpreter path — rather than repeating the version that is already written above it.
   *
   * The elided path is what the row shows, for the same reason the base-interpreter rows show one; the un-elided form
   * becomes the row's tooltip, so a path the elision cut through can still be read in full.
   */
  private fun EvoLeafDto.asPathTitledRow(nodeId: String, traceId: String): EvoTreeElement {
    val path = descriptionElided ?: description
    val row = (if (path == null) this else copy(title = path)).toElement(nodeId, traceId)
    description?.takeIf { it != path }?.let { row.presentation.putClientProperty(ActionUtil.TOOLTIP_TEXT, it) }
    return row
  }

  /**
   * The section's trailing "add new environment" element: for uv/pip (version options present) an expandable node whose
   * submenu lists the Python versions above the pre-filled env name. When it ends up being a node's only row, the row
   * itself is dropped and its submenu becomes the node's ([withoutLoneAddNewStep]); or null when the tool offers no
   * in-widget creation at all.
   */
  private fun addNewElement(section: EvoSectionDto, nodeId: String, takenNames: Set<String>): EvoTreeElement? {
    val addNewEnv = section.addNewEnv
    return when {
      // No in-widget picker (no version options) → no add-new row. We no longer fall back to the modal Add dialog.
      addNewEnv == null || addNewEnv.options.isEmpty() -> null
      else -> {
        // When the tool allows renaming (uv/pip/conda), share one editable-name holder between the submenu's name
        // field and its version rows; poetry's `.venv` is fixed, so no holder — the rows use the pre-filled name.
        // Taken names combine the visible env rows (conda's named envs) with the backend's full directory listing (uv/pip).
        val editable = if (addNewEnv.nameEditable) EvoEditableName(addNewEnv.name, takenNames + addNewEnv.takenNames) else null
        EvoTreeAddNewNode(
          text = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.add.new.title"),
          icon = AllIcons.General.Add,
          versionRows = versionRows(
            addNewEnv.options,
            { addVersionAction(nodeId, it, addNewEnv.path, editable, addNewEnv.name) },
            { base ->
              baseInterpreterRow(base) {
                createEnv(nodeId, base.token, addNewEnv.path, editable, addNewEnv.name, source = PyEvoWidgetCollector.Source.EXPANDED_VERSION)
              }
            },
            { option ->
              installActionRow {
                createEnv(nodeId, option.token, addNewEnv.path, editable, addNewEnv.name,
                          source = PyEvoWidgetCollector.Source.INSTALL_ROW, installVersion = option.installVersion())
              }
            },
          ),
          editableName = editable,
        )
      }
    }
  }

  /**
   * The two views of the version list: collapsed, one row per Python version, and expanded, each version a header over
   * its actual installs. Both are built here from the same options so the toggle between them is instant.
   *
   * The expanded view is left empty — and so the toggle is not offered — unless some version really has more than one
   * install. Otherwise it would show exactly the rows the collapsed view already shows, each under a header repeating
   * what the row says.
   */
  private fun versionRows(
    options: List<EvoAddNewOptionDto>,
    versionRow: (EvoAddNewOptionDto) -> AnAction,
    baseRow: (EvoBasePythonDto) -> EvoTreeLeafElement,
    installRow: (EvoAddNewOptionDto) -> EvoTreeLeafElement,
  ): EvoVersionRows {
    val collapsed = listOf(EvoTreeSection(elements = options.map { EvoTreeLeafElement(versionRow(it)) }))
    val expanded = when {
      options.none { it.bases.size > 1 } -> emptyList()
      else -> options.map { option ->
        EvoTreeSection(
          label = ListSeparator(addVersionText(option)),
          // A version with no interpreters behind it is one offering to install itself: the header names the version, so
          // the single row under it names the action instead of repeating it.
          elements = if (option.bases.isEmpty()) listOf(installRow(option)) else option.bases.map { baseRow(it) },
        )
      }
    }
    return EvoVersionRows(collapsed, expanded)
  }

  /**
   * A single Python-version row in the "add new environment" submenu: on click creates that version's env with the
   * (possibly user-edited) name — from [editableName] when present, else [defaultName] — in the base location [path].
   *
   * The row creates from [EvoAddNewOptionDto.token], the IDE's pick among the installs of that version. Whoever wants
   * one of the others expands the list, which turns every version into its own installs.
   */
  private fun addVersionAction(
    nodeId: String,
    option: EvoAddNewOptionDto,
    path: String,
    editableName: EvoEditableName? = null,
    defaultName: String? = null,
  ): AnAction =
    object : AnAction({ addVersionText(option) }, { "" }, versionIcon(option)), DumbAware {
      init {
        // Says what picking this row will do before it does it, the way the v2 dialog labels its download entries.
        if (option.needsDownload()) {
          templatePresentation.putClientProperty(
            ActionUtil.SECONDARY_TEXT, PySdkFrontendBundle.message("evo.sdk.status.bar.popup.add.new.installable"))
        }
      }

      override fun actionPerformed(e: AnActionEvent) =
        createEnv(nodeId, option.token, path, editableName, defaultName,
                  source = PyEvoWidgetCollector.Source.ADD_NEW_VERSION, installVersion = option.installVersion())
    }

  /** Creates the environment a version or base-interpreter row stands for, unless the typed name rules it out. */
  private fun createEnv(
    nodeId: String,
    token: String,
    path: String,
    editableName: EvoEditableName?,
    defaultName: String?,
    source: PyEvoWidgetCollector.Source,
    installVersion: String? = null,
  ) {
    // A taken/blank name can't back a new env (the field shows it in red) — don't create.
    if (editableName != null && !editableName.isValid) {
      // A discrete user action that produced nothing: worth reporting once here, where the click is refused, rather
      // than per keystroke from the name field's validator.
      editableName.problem?.let { PyEvoWidgetCollector.controlUsed(project, PyEvoWidgetCollector.Control.NAME_REJECTED, nodeStats(nodeId), it.toEvoNameProblem()) }
      return
    }
    createEvoEnv(project, pyProjectKey, nodeId, nodeStats(nodeId), token, path, editableName?.value ?: defaultName,
                 installVersion, source, scope)
  }

  /**
   * A version the machine has gets the Python logo; one that would have to be fetched gets the download icon —
   * regardless of who does the fetching, since what the row is telling the user is the same either way.
   */
  private fun versionIcon(option: EvoAddNewOptionDto): Icon =
    if (option.needsDownload()) AllIcons.Actions.Download else AllIcons.Language.Python

  /** True when picking this row means fetching the interpreter first, whether the IDE or the tool itself does it. */
  private fun EvoAddNewOptionDto.needsDownload(): Boolean = installable || downloadedByTool

  /** The version to install before creating, or null when the interpreter is already here. */
  private fun EvoAddNewOptionDto.installVersion(): String? = token.takeIf { installable }

  /** Version row text: "Default" for uv's default (blank token), otherwise "Python <version>". */
  private fun addVersionText(option: EvoAddNewOptionDto): @NlsActions.ActionText String =
    if (option.token.isBlank()) PySdkFrontendBundle.message("evolution.action.add.env.child.default")
    else PySdkFrontendBundle.message("evolution.action.add.env.child.version", option.title)

  /**
   * Drops the intermediate one-row popup from a tool whose entire node is a single "add new environment" row (a tool with
   * no environments yet): with nothing to choose between, that step is pure friction, so expanding the tool lands straight
   * on the name field + Python versions.
   *
   * The row's name — the editable holder, or the fixed label of a hatch env — and its version rows are carried over,
   * since the popup renders the header (the name and the caption beside it, the only thing left saying what the step
   * does) and the expand/collapse toggle below it from the node it is showing. A node with any environment of its own
   * keeps its normal listing.
   */
  private fun EvoLoadedNode.withoutLoneAddNewStep(): EvoLoadedNode {
    val onlyRow = sections.singleOrNull()?.elements?.singleOrNull() as? EvoTreeAddNewNode ?: return this
    return EvoLoadedNode(onlyRow.sections.toList(), refreshable, onlyRow.editableName, onlyRow.fixedName, onlyRow.versionRows)
  }

  /**
   * The tool actions of the current interpreter, taken as-is from the platform's `PythonPackageManagerActions` group —
   * the same group the dependency-file editor banner renders — so a tool that adds an action there gets it here for
   * free, and the widget never has to know which action belongs to which package manager.
   *
   * Every action decides for itself whether it applies: each row is an [EvoTreeActionLeafElement], whose own `update()`
   * the popup step runs against the dependency-file context and which it drops when the action reports itself
   * invisible. The group's separators are dropped here, since which rows survive is only known after that.
   */
  private fun packageManagerActions(context: DataContext): List<EvoTreeElement> {
    val group = getAction(PACKAGE_MANAGER_ACTIONS_GROUP) as? ActionGroup ?: return emptyList()
    val event = AnActionEvent.createEvent(context, Presentation(), ActionPlaces.POPUP, ActionUiKind.POPUP, null)
    return group.getChildren(event).filterNot { it is Separator }.map { EvoTreeActionLeafElement(it) }
  }

  /**
   * The group that acts on whatever interpreter is current — the last group of the popup.
   *
   * With an interpreter it is titled generically rather than by that interpreter: the rows below act on whatever is
   * current, and the widget itself already names it. It carries no icon either, so the header renders as an ordinary
   * separator (see `GearGroupHeaderSeparator`), unlike the tool section above it.
   *
   * Without one it holds the "Shortcuts" rows instead — the IDE's autoconfigure suggestion(s), selecting one runs it —
   * and is omitted entirely when there is nothing to suggest, rather than leaving an empty "Shortcuts" header.
   */
  private fun currentEnvSection(traceId: String, context: DataContext): EvoTreeSection? = when (currentInterpreter) {
    null -> shortcuts.takeIf { it.isNotEmpty() }?.let { leaves ->
      EvoTreeSection(
        label = ListSeparator(PySdkFrontendBundle.message("evo.sdk.status.bar.popup.shortcuts")),
        elements = leaves.map { EvoTreeLeafElement(selectEnvAction(project, pyProjectKey, it, SHORTCUTS_NODE_ID, EvoNodeStats(EvoNodeKind.SHORTCUTS), traceId, scope)) },
      )
    }
    else -> EvoTreeSection(
      label = ListSeparator(PySdkFrontendBundle.message("evo.sdk.status.bar.popup.current.environment")),
      elements = packageManagerActions(context) + EvoTreeLeafElement(managePackagesAction),
      // Which environment, on hover — the identity the caption no longer spells out.
      labelTooltip = currentInterpreter.description,
    )
  }

  /** A single "Associated environments" node holding the interpreters the classic widget lists, shown inside the tool list. */
  private fun associatedInterpretersNode(traceId: String): EvoTreeStaticNodeElement =
    EvoTreeStaticNodeElement(
      text = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.associated.interpreters"),
      icon = PythonSdkFrontendIcons.Associated,
      sections = listOf(
        EvoTreeSection(
          label = null,
          elements = associated.map { EvoTreeLeafElement(selectEnvAction(project, pyProjectKey, it, ASSOCIATED_NODE_ID, EvoNodeStats(EvoNodeKind.ASSOCIATED), traceId, scope)) },
        ),
      ),
      // A static node, so it never runs the lazy loader that reports every other node's opening.
      onOpened = { PyEvoWidgetCollector.staticNodeOpened(project, EvoNodeStats(EvoNodeKind.ASSOCIATED)) },
    )

  /**
   * Builds the popup tree. A fresh trace root (`traceId`) is minted here, so all of this tree's backend commands
   * (tool listing, version probes) group under one "Python Interpreter Widget" root — the widget builds a tree once
   * per data change and reuses it across re-opens, so a re-open makes no new calls and mints no new root.
   *
   * [context] only enumerates the package-manager action group; what each of those actions *does* with a context is
   * decided per popup open, against the enriched one [createPopup] builds.
   */
  fun buildTree(context: DataContext): EvoTreeStaticNodeElement {
    val projectId = project.projectId()
    val traceId = UUID.randomUUID().toString()

    // The node whose tool made the environment in use, and the rest. The active one leads the list, and while the list
    // is collapsed it is the only tool row on it. Null when the backend named no node (see PyInterpreterDto.activeNodeId)
    // or named one this machine does not have — the list then holds every tool, exactly as it did before.
    val activeNodeId = currentInterpreter?.activeNodeId
    // Every tool, in the order the providers are registered in — the order the expanded list keeps. The active one is
    // remembered as well, but not moved: only the collapsed list singles it out.
    val toolsInOrder = mutableListOf<EvoTreeElement>()
    var activeTool: EvoTreeElement? = null
    // "Associated environments" lists interpreters already configured and "Advanced" opens the full add-interpreter set,
    // so neither switches the interpreter with a tool the project uses. They are never folded away.
    val nonToolNodes = mutableListOf<EvoTreeElement>()
    for (node in nodes) {
      val element = EvoTreeLazyNodeElement(node.label, node.icon.icon(), EvoNodeStats.of(node), showOutput = { showToolOutput(node.id, traceId) }) { force ->
        val result = evoRpc { requestEvoNode(projectId, pyProjectKey, node.id, traceId, force) }
        val refreshable = (result as? EvoLoadResultDto.Ok)?.refreshable == true
        val collapsed = result.toSections(node.id, traceId)
        // A tool whose rows carry interpreters (poetry) can be expanded like the add-new list; one whose rows do not
        // gets no toggle, since toExpandedSections is then empty.
        val expanded = result.toExpandedSections(node.id, traceId)
        val versionRows = if (expanded.isEmpty()) null else EvoVersionRows(collapsed, expanded)
        val loaded = EvoLoadedNode(versionRows?.sections() ?: collapsed, refreshable, versionRows = versionRows)
          .withoutLoneAddNewStep()
        // A tool that answered, but with nothing to offer, is not an error — and not something to leave as a row that
        // opens an empty submenu either. Report it the way a backend warning is reported: disabled, with a sign.
        if (loaded.sections.none { it.elements.isNotEmpty() }) {
          throw EvoWarningException(PySdkFrontendBundle.message("evo.sdk.status.bar.popup.node.empty"))
        }
        loaded
      }
      if (node.id == ADVANCED_NODE_ID) {
        nonToolNodes += element
        continue
      }
      toolsInOrder += element
      // The slot has to be free as well, which guards a backend that named one node twice: the first wins.
      if (node.id == activeNodeId && activeTool == null) activeTool = element
    }
    // Above "Advanced", where it has always sat.
    if (associated.isNotEmpty()) nonToolNodes.add(0, associatedInterpretersNode(traceId))

    val toolsCaption = ListSeparator(PySdkFrontendBundle.message(
      // "Change" once there is something to change: the section switches the interpreter rather than setting a first one.
      if (currentInterpreter == null) "evo.sdk.status.bar.popup.select.environment"
      else "evo.sdk.status.bar.popup.change.environment"))

    val currentEnvSection = currentEnvSection(traceId, context)

    /** [toolSections] plus the group that acts on whatever interpreter is current, which is always last. */
    fun sectionsWith(toolSections: List<EvoTreeSection>): List<EvoTreeSection> =
      toolSections + listOfNotNull(currentEnvSection)

    // Copied, so a section never holds a list still being built above it.
    val allTools = toolsInOrder.toList()
    val nonTools = nonToolNodes.toList()

    /**
     * [tools] under the caption, then a rule over the nodes that are not a tool.
     *
     * The rule is a separator with no caption, which draws as a plain full-width line — see
     * `GearGroupHeaderSeparator.isPlain`. With no tool row at all there is nothing to divide, so those nodes keep the
     * caption above them instead of following a rule that separates them from nothing.
     */
    fun toolSections(tools: List<EvoTreeElement>): List<EvoTreeSection> = when {
      tools.isEmpty() -> listOf(EvoTreeSection(label = toolsCaption, elements = nonTools))
      else -> listOfNotNull(
        EvoTreeSection(label = toolsCaption, elements = tools),
        nonTools.takeIf { it.isNotEmpty() }?.let { EvoTreeSection(label = ListSeparator(""), elements = it) },
      )
    }

    // The list as it stands before anything is folded away: every tool in its registered order, the active one among
    // them rather than lifted out of it.
    val expandedSections = toolSections(allTools)

    // The tree the "Show more" row swaps the expanded sections into. It cannot be built before that row exists, and
    // the row cannot be built before the tree it acts on — so the row reaches it through this.
    var builtTree: EvoTreeStaticNodeElement? = null
    val showMore = showMoreRow {
      val tree = builtTree ?: return@showMoreRow
      tree.sections.clear()
      tree.sections.addAll(sectionsWith(expandedSections))
      // The widget reuses this very tree within its reuse window, so the reopened popup shows what was just swapped in.
      reopenPopup()
    }

    // Collapsed: the tool in use, then the "Show more" row standing in for the tools it hides. The rule below them is
    // the same one the expanded list has, so folding the tools away does not change the shape of the list.
    val collapsedSections = activeTool?.takeIf { allTools.size > 1 }?.let { active ->
      toolSections(listOf(active, showMore))
    }

    // Collapsed when there is a tool in use and others to fold away; the full list otherwise.
    return EvoTreeStaticNodeElement(
      text = "",
      icon = AllIcons.Language.Python,
      sections = sectionsWith(collapsedSections ?: expandedSections),
    ).also { builtTree = it }
  }

  /**
   * Opens the Python Process Output tool window on what this tool's last run produced — the failure sign's action.
   *
   * The backend does the addressing, since the trace the tool window knows the process by lives there. When it reports
   * that it found nothing — a run whose scope has since expired, or a failure with no process behind it at all, like a
   * tool that simply offered nothing — the window is opened without a selection, which is still better than a sign that
   * does nothing when clicked.
   */
  private fun showToolOutput(nodeId: String, traceId: String) {
    PyEvoWidgetCollector.controlUsed(project, PyEvoWidgetCollector.Control.PROCESS_OUTPUT, nodeStats(nodeId))
    scope.launch {
      val opened = evoRpcOrNull { requestEvoShowToolProcessOutput(project.projectId(), nodeId, traceId) } == true
      if (opened) return@launch
      withContext(Dispatchers.EDT) {
        ToolWindowManager.getInstance(project).getToolWindow(PROCESS_OUTPUT_TOOL_WINDOW_ID)?.activate(null)
      }
    }
  }

  /**
   * The popup's title. The environments listed below belong to the *workspace*, not to the module on its own, so a
   * module taking part in one is titled by its workspace: `monorepo` at the root, `monorepo[pkg-a]` for a member.
   * A standalone module keeps its plain name.
   */
  private fun popupTitle(): @PopupTitle String =
    // Standalone, or the workspace's own root (where the two names are the same string): the plain name.
    if (workspaceRootName == null || workspaceRootName == displayName) displayName
    else PySdkFrontendBundle.message("evo.sdk.status.bar.popup.title.workspace", workspaceRootName, displayName)

  /**
   * The data context the popup's actions see: [context] with the interpreter's dependency file put in front of it.
   *
   * The package-manager actions take their file from the context — they gate on it in `update()` and write to it in
   * `actionPerformed`. Left alone, that file would be whatever the editor happens to show, which for the status bar is
   * unrelated to the interpreter: conda's "export to environment.yml" would then overwrite an open `pyproject.toml`.
   * Naming the dependency file here makes the rows both correct and usable whatever is open.
   *
   * The file keys are therefore *always* decided here, never inherited — an interpreter with no dependency file masks
   * them with an explicit null instead of leaving the editor's file showing through. Otherwise exactly the interpreters
   * with nothing to act on are the ones whose rows would be driven by the open editor: they would appear only while a
   * matching file happens to be open, and act on that unrelated file — `PipSetDefaultRequirementsAction` would adopt a
   * stranger's `requirements.txt` as the SDK default.
   */
  private fun popupDataContext(context: DataContext): DataContext {
    val file = currentInterpreter?.dependencyFileUrl?.let { VirtualFileManager.getInstance().findFileByUrl(it) }
    // Called while opening the popup, i.e. on the EDT, which already holds read access.
    val psiFile = file?.let { PsiManager.getInstance(project).findFile(it) }
    return SimpleDataContext.builder()
      .setParent(context)
      .addOrNull(CommonDataKeys.VIRTUAL_FILE, file)
      // PythonPackageManagerAction.actionPerformed bails out without a PSI file (it restarts the daemon on it).
      .addOrNull(CommonDataKeys.PSI_FILE, psiFile)
      .build()
  }

  /** Wraps an already-built [tree] into a popup; [onClose] fires when it is dismissed (the widget starts its TTL then). */
  fun createPopup(tree: EvoTreeStaticNodeElement, context: DataContext, onClose: () -> Unit): ListPopup =
    EvoSdkManagerTreePopup(
      title = popupTitle(),
      evoTreeNodeElement = tree,
      dataContext = popupDataContext(context),
      disposeCallback = onClose,
      scope = scope,
    ).apply {
      setExecuteExpandedItemOnClick(true)
    }
}

/**
 * Maps the name field's own reason onto the reported one.
 *
 * Mapped here rather than sharing one enum: `EvoEditableName.Problem` is a frontend UI concern, and a metric's value
 * space should not become something a UI refactor can change by accident.
 */
private fun EvoEditableName.Problem.toEvoNameProblem(): PyEvoWidgetCollector.NameProblem = when (this) {
  EvoEditableName.Problem.BLANK -> PyEvoWidgetCollector.NameProblem.BLANK
  EvoEditableName.Problem.ILLEGAL -> PyEvoWidgetCollector.NameProblem.ILLEGAL
  EvoEditableName.Problem.TAKEN -> PyEvoWidgetCollector.NameProblem.TAKEN
}

internal class EvoSdkManagerTreePopup(
  title: @PopupTitle String?,
  evoTreeNodeElement: EvoTreeNodeElement,
  dataContext: DataContext,
  disposeCallback: (() -> Unit)? = null,
  scope: CoroutineScope,
) : EvoTreePopup(
  parentPopup = null,
  title = title,
  evoTreeNodeElement = evoTreeNodeElement,
  dataContext = dataContext,
  scope = scope,
  maxRowCount = -1,
  disposeCallback = disposeCallback,
)
