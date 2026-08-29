package com.intellij.python.sdk.frontend.evolution

import com.intellij.ide.actions.ShowSettingsUtilImpl
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
import com.intellij.openapi.util.IconLoader
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
import com.intellij.python.sdk.common.evolution.requestEvoCurrentRecreate
import com.intellij.python.sdk.common.evolution.requestEvoNode
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
import com.intellij.python.sdk.frontend.evolution.components.EvoBasePythonPanel
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
import com.intellij.python.sdk.frontend.evolution.components.EvoWarningException
import com.intellij.python.sdk.frontend.icons.PythonSdkFrontendIcons
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
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
/** How solid a tool's icon stays on a row for an environment that does not exist yet. */
private const val CREATE_ROW_ICON_ALPHA = 0.5f

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

/** The project's Python interpreter settings page, matched by id the way `ShowSettingsUtil` matches one. */
private const val PY_INTERPRETER_CONFIGURABLE_ID: String = "com.jetbrains.python.configuration.PyActiveSdkModuleConfigurable"

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
   * Whether the tool list shows every tool, or only the one in use with a "Show More" row standing for the rest.
   *
   * Held by the widget, not by the tree this builds: the widget rebuilds the tree whenever its cache says to, and a
   * state kept in the tree went back to collapsed each time that happened.
   */
  val toolsExpanded: Boolean,
  /**
   * Reveals the tools the collapsed list leaves out — what the "Show More" row runs.
   *
   * The widget records the choice and shows the popup again over it. A popup is laid out and placed once, so a list this
   * much bigger has to be reopened rather than grown in place, and the widget owns the anchoring.
   */
  /** Folds the tool list away or unfolds it, and opens the popup again over the list that results. */
  val setToolsExpanded: (Boolean) -> Unit,
) {
  /** The tool's own name for [nodeId], as the popup writes it, falling back to the id when no node claims it. */
  private fun nodeLabel(nodeId: String): @NlsSafe String = nodes.firstOrNull { it.id == nodeId }?.label ?: nodeId

  /** The tool's own icon for [nodeId] — what a row of that node wears when it carries no icon of its own. */
  private fun nodeIcon(nodeId: String): Icon = nodes.firstOrNull { it.id == nodeId }?.icon?.icon() ?: AllIcons.Language.Python

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
   * carries the reason why, which matters more than the rest of its name. A row titled by its path
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
      // Disabled for selection, and still rebuildable: the environment belongs to another tool, so adopting it here
      // would type its SDK to the wrong one — but that tool rebuilds it from whichever node the user found it on.
      return EvoTreeUnavailableLeafElement(
        selectEnvAction(project, pyProjectKey, this, nodeId, nodeStats(nodeId), traceId, scope, basePythonPicker(nodeId, traceId)),
        reason,
      )
    }
    // A version-picker row (hatch not-yet-created env): a node whose submenu is the versions; choosing one creates the
    // env with that Python. The row's ref (CreateEnv) carries the per-row create token (hatch: the env name) → folder.
    val versions = createVersions
    if (!versions.isNullOrEmpty()) {
      // The row's own token is where the environment goes — a hatch env name, uv's and pip's `.venv` folder — and the
      // Python comes from whichever option is chosen.
      val createToken = (ref as? PyInterpreterRef.CreateEnv)?.token.orEmpty()
      return createEnvRow(nodeId, traceId, title, icon.icon(), createToken, versions, null, secondaryText)
    }
    return when (kind) {
      EvoLeafKind.SELECT_ENV -> EvoTreeLeafElement(
        selectEnvAction(project, pyProjectKey, this, nodeId, nodeStats(nodeId), traceId, scope, basePythonPicker(nodeId, traceId))
      )
      // A runnable backend action (advanced add-interpreter) carries an actionId; a display-only row stays a no-op stub.
      EvoLeafKind.ACTION -> EvoTreeLeafElement(
        if (actionId != null) evoBackendActionLeaf(project, pyProjectKey, nodeId, this, scope) else toStubAction()
      )
    }
  }

  private fun EvoLoadResultDto.toSections(nodeId: String, traceId: String): List<EvoTreeSection> = when (this) {
    is EvoLoadResultDto.Ok -> {
      sections.map { section ->
        val leaves = section.leaves.map { it.toElement(nodeId, traceId) }
        EvoTreeSection(
          label = section.label?.let { ListSeparator(it) },
          elements = leaves + listOfNotNull(addNewElement(section, nodeId, traceId)),
          // Only worth a tooltip when the header was actually shortened — otherwise it would just repeat what is on screen.
          labelTooltip = section.labelTooltip?.takeIf { it != section.label },
        )
      }
    }
    is EvoLoadResultDto.Warning -> throw EvoWarningException(message)
    is EvoLoadResultDto.Error -> throw EvoErrorException(message)
  }

  /**
   * The section's trailing row for the environment it does not have yet, or null when the tool offers no in-widget
   * creation at all.
   */
  private fun addNewElement(section: EvoSectionDto, nodeId: String, traceId: String): EvoTreeElement? {
    val addNewEnv = section.addNewEnv ?: return null
    if (addNewEnv.options.isEmpty()) return null
    return createEnvRow(nodeId, traceId, addNewEnv.name, nodeIcon(nodeId), addNewEnv.path, addNewEnv.options, addNewEnv.name)
  }

  /**
   * The row for an environment that does not exist yet: named as the one it will become, marked with a `+`, and showing
   * the Python it would be built on.
   *
   * It reads as an environment rather than as an errand. "Add New" named the act and hid every fact behind a submenu —
   * which name, which Python — so the user had to open it to learn what a click would even produce. This row states
   * both, so choosing it is one click, and the pencil offers the other Pythons for whoever wants a different one.
   *
   * It wears its tool's own icon, like the environments beside it. What tells the two apart is the version column: an
   * environment that exists reports the version it has, and this one names the Python it would be built on.
   *
   * The Python it names is the first option, which every provider sorts newest first.
   */
  private fun createEnvRow(
    nodeId: String,
    traceId: String,
    name: @NlsSafe String,
    icon: Icon,
    path: String,
    options: List<EvoAddNewOptionDto>,
    defaultName: String?,
    /** What the row shows in its version column, when the backend already said — a poetry row names its own Python. */
    secondaryText: @NlsSafe String? = null,
  ): EvoTreeElement {
    // Faded, so a row for an environment that is not there yet is told from one that is at a glance. The two are
    // otherwise the same row — same tool icon, same name — and only the version column separates them: one reports the
    // version it has, this one names the Python it would get.
    val fadedIcon = IconLoader.getTransparentIcon(icon, CREATE_ROW_ICON_ALPHA)
    val best = options.first()
    fun create(token: String, source: PyEvoWidgetCollector.Source, installVersion: String? = null) =
      createEnv(nodeId, traceId, token, path, defaultName, source, installVersion)
    val action = object : AnAction({ name }, { "" }, fadedIcon), DumbAware, EvoBasePythonPanel {
      /** The Pythons this row can be built on, offered by the right button. Picking one builds the environment. */
      override val basePythonPanel: EvoTreeNodeElement by lazy {
        basePythonPanel(name, fadedIcon, options) { token, _, installVersion ->
          create(token, PyEvoWidgetCollector.Source.ADD_NEW_VERSION, installVersion)
        }.apply { stepDescription = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.panel.build.step") }
      }

      init {
        // An environment name is a name off the machine, so no part of it may be read as a mnemonic marker.
        templatePresentation.setPlainText(name)
        // Where the version column of a real environment shows what it has, this shows what it would get: the version
        // the backend already named for the row, or the Python this would be built on when it named none.
        templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT, secondaryText ?: addVersionText(best))
      }

      /** A plain click builds on the Python the row names, which is the best one the backend offered. */
      override fun actionPerformed(e: AnActionEvent) =
        create(best.token, PyEvoWidgetCollector.Source.ADD_NEW_VERSION, best.installVersion())
    }
    return EvoTreeLeafElement(action)
  }

  /**
   * The panel a row's inline icon opens: the Pythons its environment could be built on, and [onChosen] when one is.
   *
   * A row standing for one Python version lists that version's own interpreters and nothing else — the version is not a
   * choice there, only which install backs it — so such a panel is a flat list with no view to switch. A row standing
   * for no particular version offers every version, and then the two views are worth having.
   */
  private fun basePythonPanel(
    name: @NlsSafe String,
    icon: Icon,
    options: List<EvoAddNewOptionDto>,
    /** The picked Python: its token, what to write in the row's version column, and the version to install first. */
    onChosen: (token: String, text: @NlsSafe String, installVersion: String?) -> Unit,
  ): EvoTreeNodeElement {
    // No heading: the row this opened over is still on screen behind it, saying which environment the choice is for.
    // The line along the bottom is set by the caller, since only it knows whether the pick builds or rebuilds.
    val single = options.singleOrNull()
    if (single != null && single.bases.size > 1) {
      return EvoTreeStaticNodeElement(
        text = name,
        icon = icon,
        sections = listOf(EvoTreeSection(elements = single.bases.map { base ->
          baseInterpreterRow(base) { onChosen(base.token, base.baseText(), base.installVersion) }
        })),
      )
    }
    return EvoTreeAddNewNode(
      text = name,
      icon = icon,
      sections = versionRows(
        options,
        { option -> versionAction(option) { onChosen(option.token, addVersionText(option), option.installVersion()) } },
        { base -> baseInterpreterRow(base) { onChosen(base.token, base.baseText(), base.installVersion) } },
        { option -> installActionRow { onChosen(option.token, addVersionText(option), option.installVersion()) } },
      ),
    )
  }

  /**
   * The picker the right button opens on this row, or null when the backend offered no rebuild for it.
   *
   * The same picker an environment that does not exist yet opens, and a pick acts the same way: it rebuilds this
   * environment on the Python chosen, after the confirmation. Built from the same [versionRows] the create row uses, so
   * the two cannot drift apart.
   */
  private fun EvoLeafDto.basePythonPicker(nodeId: String, traceId: String): EvoTreeNodeElement? {
    val spec = recreate ?: return null
    val envHomePath = (ref as? PyInterpreterRef.DetectedPath)?.homePath ?: return null
    val stats = nodeStats(nodeId)
    // This node's tool does the rebuilding, so an environment another tool made changes hands. Named for the
    // confirmation, which is the only place the user can learn that before it happens.
    val toolChange = ownerNodeId
      ?.let { owner -> nodeLabel(owner) to nodeLabel(nodeId) }
      ?.let { (from, to) -> EvoToolChange(from, to) }
    return basePythonPanel(title, icon.icon(), spec.options) { token, text, installVersion ->
      recreateEvoEnv(project, pyProjectKey, nodeId, stats, envHomePath, title, token, text, installVersion,
                     spec.canSyncPackages, toolChange, traceId, scope)
    }.apply { stepDescription = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.panel.rebuild.step") }
  }

  /**
   * The Pythons a version list offers: each version a header over its own installs.
   *
   * The one view there is. It used to be the expanded half of a pair, with a toggle back to one row per version — but a
   * version is not what an environment is built on, an interpreter is, and the row naming one is the row the user has to
   * reach in the end. The toggle only stood between them.
   *
   * A tool that names no interpreters at all — uv and conda fetch their own — has nothing to head, so its versions stay
   * one row each and the headers are left off.
   */
  private fun versionRows(
    options: List<EvoAddNewOptionDto>,
    versionRow: (EvoAddNewOptionDto) -> AnAction,
    baseRow: (EvoBasePythonDto) -> EvoTreeLeafElement,
    installRow: (EvoAddNewOptionDto) -> EvoTreeLeafElement,
  ): List<EvoTreeSection> {
    if (options.none { it.bases.isNotEmpty() }) {
      return listOf(EvoTreeSection(elements = options.map { EvoTreeLeafElement(versionRow(it)) }))
    }
    return options.map { option ->
      EvoTreeSection(
        label = ListSeparator(addVersionText(option)),
        // A version with no interpreters behind it is one offering to install itself: the header names the version, so
        // the single row under it names the action instead of repeating it.
        elements = if (option.bases.isEmpty()) listOf(installRow(option)) else option.bases.map { baseRow(it) },
      )
    }
  }

  /**
   * One Python-version row of a version list: its label, its icon, and [onChosen] when it is picked.
   *
   * Shared by the add-new panel and the rebuild one, so a version reads the same in both — including the note saying
   * the machine does not have that version yet.
   */
  private fun versionAction(option: EvoAddNewOptionDto, onChosen: () -> Unit): AnAction =
    object : AnAction({ addVersionText(option) }, { "" }, versionIcon(option)), DumbAware {
      init {
        // Says what picking this row will do before it does it, the way the v2 dialog labels its download entries.
        if (option.needsDownload()) {
          templatePresentation.putClientProperty(
            ActionUtil.SECONDARY_TEXT, PySdkFrontendBundle.message("evo.sdk.status.bar.popup.add.new.installable"))
        }
      }

      override fun actionPerformed(e: AnActionEvent) = onChosen()
    }

  /** Creates the environment a version or base-interpreter row stands for, named by the tool that makes it. */
  private fun createEnv(
    nodeId: String,
    traceId: String,
    token: String,
    path: String,
    defaultName: String?,
    source: PyEvoWidgetCollector.Source,
    installVersion: String? = null,
  ) = createEvoEnv(project, pyProjectKey, nodeId, nodeStats(nodeId), token, path, defaultName, installVersion, source, traceId, scope)

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
      // The rebuild leads, because it acts on the environment itself while the rows under it act on its packages. It
      // opens a list of Pythons rather than doing anything, and the confirmation is behind that, so leading costs a
      // mis-click nothing.
      elements = buildList {
        add(recreateCurrentEnvNode(traceId))
        addAll(packageManagerActions(context))
        add(EvoTreeLeafElement(managePackagesAction))
      },
      // Which environment, on hover — the identity the caption no longer spells out.
      labelTooltip = currentInterpreter.description,
    )
  }

  /** A single "Associated environments" node holding the interpreters the classic widget lists, shown inside the tool list. */
  private fun associatedInterpretersNode(traceId: String): EvoTreeStaticNodeElement =
    EvoTreeStaticNodeElement(
      text = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.associated.interpreters"),
      // The chain link, as "Related Symbol" uses it: these interpreters are tied to the module the widget speaks for.
      icon = AllIcons.Nodes.Related,
      sections = listOf(
        EvoTreeSection(
          label = null,
          elements = associated.map { EvoTreeLeafElement(selectEnvAction(project, pyProjectKey, it, ASSOCIATED_NODE_ID, EvoNodeStats(EvoNodeKind.ASSOCIATED), traceId, scope)) },
        ),
      ),
      // A static node, so it never runs the lazy loader that reports every other node's opening.
      onOpened = { PyEvoWidgetCollector.staticNodeOpened(project, EvoNodeStats(EvoNodeKind.ASSOCIATED)) },
    ).apply {
      stepDescription = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.panel.associated.step")
    }

  /**
   * "Recreate Environment": the Pythons the environment in use could be rebuilt on, and the rebuild when one is picked.
   *
   * A lazy node, because naming those Pythons means running processes and the answer is worth nothing until the user
   * asks. Until then the row is one line in the "Current Environment" section; opening it is what asks the backend.
   *
   * A tool that will not rebuild this environment — a system interpreter, an environment belonging to another project —
   * answers with nothing, and the row then reports itself unavailable, exactly as a tool node that offered nothing does.
   */
  private fun recreateCurrentEnvNode(traceId: String): EvoTreeElement =
    EvoTreeLazyNodeElement(
      text = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.recreate.current"),
      icon = AllIcons.Actions.Restart,
      nodeStats = EvoNodeStats(EvoNodeKind.OTHER),
      // An environment no tool here can rebuild — a remote interpreter, one no node owns — is not a fault to report.
      signsUnavailable = false,
    ) { _ ->
      val dto = evoRpc { requestEvoCurrentRecreate(project.projectId(), pyProjectKey, traceId) }
                ?: throw EvoWarningException(PySdkFrontendBundle.message("evo.sdk.status.bar.popup.recreate.current.unavailable"))
      val stats = nodeStats(dto.nodeId)
      fun rebuild(token: String, title: @NlsSafe String, installVersion: String?) =
        recreateEvoEnv(project, pyProjectKey, dto.nodeId, stats, dto.envHomePath, dto.title, token, title, installVersion,
                       dto.recreate.canSyncPackages, null, traceId, scope)
      EvoLoadedNode(
        sections = versionRows(
          dto.recreate.options,
          { option -> versionAction(option) { rebuild(option.token, addVersionText(option), option.installVersion()) } },
          { base -> baseInterpreterRow(base) { rebuild(base.token, base.baseText(), base.installVersion) } },
          { option -> installActionRow { rebuild(option.token, addVersionText(option), option.installVersion()) } },
        ),
        refreshable = false,
        stepDescription = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.panel.rebuild.step"),
      )
    }

  /**
   * The "Interpreter Settings…" row for [nodeId]'s submenu, under a rule, or nothing for a node that does not carry it.
   *
   * It belongs to "Advanced", which is where the widget keeps what it does not offer itself: the other nodes list this
   * project's environments, while that one is already the way out to the fuller machinery. Behind a rule, since it
   * leaves the popup rather than adding to the list above it.
   */
  private fun settingsSection(nodeId: String): List<EvoTreeSection> =
    if (nodeId != ADVANCED_NODE_ID) emptyList()
    else listOf(EvoTreeSection(label = ListSeparator(""), elements = listOf(EvoTreeLeafElement(interpreterSettingsAction()))))

  /**
   * "Interpreter Settings…" — the row the classic widget ended with, opening the project's Python interpreter page.
   *
   * Opened by the configurable's own id, as the settings gear opens the package-manager page: the frontend has no
   * handle on the backend's configurable classes, and an id is what `ShowSettingsUtil` matches on anyway.
   */
  private fun interpreterSettingsAction(): AnAction =
    object : AnAction({ PySdkFrontendBundle.message("evo.sdk.status.bar.popup.interpreter.settings") },
                      { "" }, AllIcons.General.Settings), DumbAware {
      override fun actionPerformed(e: AnActionEvent) {
        ShowSettingsUtilImpl.showSettingsDialog(project, PY_INTERPRETER_CONFIGURABLE_ID, null)
      }
    }

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
        // A node holding one row keeps that row and its panel: the step was dropped as friction, but it is the step that
        // says which tool the list belongs to and what choosing a row there does, so losing it cost more than it saved.
        val sections = result.toSections(node.id, traceId)
        // A tool that answered, but with nothing to offer, is not an error — and not something to leave as a row that
        // opens an empty submenu either. Report it the way a backend warning is reported: disabled, with a sign.
        // Asked of what the backend named, so a node carrying only the settings row still counts as empty.
        if (sections.none { it.elements.isNotEmpty() }) {
          throw EvoWarningException(PySdkFrontendBundle.message("evo.sdk.status.bar.popup.node.empty"))
        }
        EvoLoadedNode(sections + settingsSection(node.id), refreshable)
      }
      // No heading of its own: the row the user opened already names the tool, and the line along the bottom says what
      // its list is for.
      // The tool's own wording where it has one: only it knows what its list holds. The general line is the fallback.
      element.stepDescription = node.stepDescription ?: PySdkFrontendBundle.message("evo.sdk.status.bar.popup.panel.tool.step")
      if (node.id == ADVANCED_NODE_ID) {
        element.stepDescription = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.panel.advanced.step")
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

    // The list as it stands unfolded: every tool in its registered order, the active one among them rather than lifted
    // out of it, and the row that folds them away again below.
    val expandedSections = toolSections(allTools + showMoreRow(expanded = true, anyToolShown = true) { setToolsExpanded(false) })

    // Collapsed: the tool in use, then the "Show more" row standing in for the tools it hides. The rule below them is
    // the same one the expanded list has, so folding the tools away does not change the shape of the list.
    //
    // The row records the choice with the widget rather than swapping these sections in place. Editing the tree and
    // reopening over it only worked while the widget still had that very tree: any rebuild — a cache that expired while
    // the popup was open, a refresh — produced a fresh collapsed one, and the click appeared to do nothing.
    fun disclosure(anyToolShown: Boolean) = showMoreRow(expanded = false, anyToolShown = anyToolShown) { setToolsExpanded(true) }
    val collapsedSections = when {
      toolsExpanded || allTools.isEmpty() -> null
      // The tool in use leads, and the rest fold behind the row under it.
      activeTool != null -> if (allTools.size > 1) toolSections(listOf(activeTool, disclosure(anyToolShown = true))) else null
      // No tool is in use — a remote interpreter, or one no node claims — so none of them is worth singling out and the
      // whole list folds away. Leaving them all on screen made the widget of such a project the longest of any.
      else -> toolSections(listOf(disclosure(anyToolShown = false)))
    }

    // Collapsed when there is a tool in use, others to fold away, and the user has not asked for all of them.
    return EvoTreeStaticNodeElement(
      text = "",
      icon = AllIcons.Language.Python,
      sections = sectionsWith(collapsedSections ?: expandedSections),
    )
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
