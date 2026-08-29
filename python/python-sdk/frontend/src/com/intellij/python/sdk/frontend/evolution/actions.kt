package com.intellij.python.sdk.frontend.evolution

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.CommonBundle
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.project.projectId
import com.intellij.python.sdk.common.evolution.EvoBasePythonDto
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.common.evolution.EvoSelectResultDto
import com.intellij.python.sdk.common.evolution.PyInterpreterDto
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
import com.intellij.python.sdk.common.evolution.EvoNodeIds
import com.intellij.python.sdk.common.evolution.EvoNodeStats
import com.intellij.python.sdk.common.evolution.EvoRecreateRequestDto
import com.intellij.python.sdk.common.evolution.PyEvoWidgetCollector
import com.intellij.python.sdk.common.evolution.evoRefKind
import com.intellij.python.sdk.common.evolution.evoRpcOrNull
import com.intellij.python.sdk.common.evolution.requestEvoPerformNodeAction
import com.intellij.python.sdk.common.evolution.requestEvoRecreateEnvironment
import com.intellij.python.sdk.common.evolution.requestEvoResolveVersion
import com.intellij.python.sdk.common.evolution.requestEvoSelectInterpreter
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
import com.intellij.python.sdk.frontend.evolution.components.EvoDisclosureRow
import com.intellij.python.sdk.frontend.evolution.components.EvoLazyDetail
import com.intellij.python.sdk.frontend.evolution.components.EvoBasePythonPanel
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeNodeElement
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeLeafElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.function.BiFunction

/**
 * Remembers the tool node whose interpreter configuration is currently in progress, so the status-bar widget shows that
 * tool's (fading) logo while the SDK-configuration lock is held. Set by the select/create actions before they call the
 * backend; cleared by the widget when the lock is released.
 */
@Service(Service.Level.PROJECT)
internal class EvoConfiguringTracker {
  @Volatile
  var nodeId: String? = null
}

/**
 * Creates (and assigns to the module) an environment for the chosen version [token] via [requestEvoSelectInterpreter]
 * with a [PyInterpreterRef.CreateEnv]. [folder] is the base location (uv/pip: the containing dir; other tools: tool
 * specific) and [name] the user-editable env name from the add-new field (uv/pip: the env folder name; conda: the env
 * name; null keeps the tool default). The widget refreshes itself on the resulting `rootsChanged`.
 */
internal fun createEvoEnv(
  project: Project,
  pyProjectKey: String,
  nodeId: String,
  /** What statistics report this node as — resolved by the caller, which holds the node list. */
  nodeStats: EvoNodeStats,
  token: String,
  folder: String,
  name: String?,
  /** Set for a row that offered an interpreter the machine lacks: the version to install before creating anything. */
  installPythonVersion: String?,
  /** Which popup section the row that triggered this belongs to — reported, never acted on. */
  source: PyEvoWidgetCollector.Source,
  scope: CoroutineScope,
) {
  project.service<EvoConfiguringTracker>().nodeId = nodeId   // so the widget fades this tool's logo while configuring
  scope.launch {
    val ref = PyInterpreterRef.CreateEnv(token, folder, name, installPythonVersion)
    PyEvoWidgetCollector.interpreterSelected(project, nodeStats, ref.evoRefKind(), source)
    when (val result = requestEvoSelectInterpreter(project.projectId(), pyProjectKey, ref, nodeId)) {
      is EvoSelectResultDto.Ok -> Unit
      is EvoSelectResultDto.Error -> LOG.warn("Evo: failed to create '$nodeId' environment for '$pyProjectKey': ${result.message}")
    }
  }
}

/**
 * Sets [text] as this row's label, with no part of it read as a mnemonic marker.
 *
 * `AnAction`'s text supplier reaches [Presentation.setText], which reads the text as text-with-mnemonic: the first `_`
 * or `&` marks the shortcut letter and is dropped from what is drawn. Every label in this popup is a name off the
 * machine — an environment folder, an interpreter path — so an environment called `2025_2_eap5_2` appeared as
 * `20252_eap5_2` (PY-91872). Nothing here navigates by mnemonic in any case:
 * [com.intellij.python.sdk.frontend.evolution.components.EvoActionPopupStep] turns that off.
 *
 * A label built from a bundle message needs no such call, because a translator writes the marker deliberately.
 */
internal fun Presentation.setPlainText(text: @NlsSafe String) = setText({ text }, false)

/**
 * A runnable backend ACTION leaf (e.g. an "Advanced" add-interpreter / add-on-target action). Performing it runs
 * the backend action over RPC ([requestEvoPerformNodeAction]); the widget refreshes on the resulting `rootsChanged`.
 */
internal fun evoBackendActionLeaf(
  project: Project,
  pyProjectKey: String,
  nodeId: String,
  leaf: EvoLeafDto,
  scope: CoroutineScope,
): AnAction = object : AnAction({ leaf.title }, { leaf.description ?: "" }, leaf.icon.icon()), DumbAware {
  init {
    templatePresentation.setPlainText(leaf.title)
    leaf.secondaryText?.let { templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT, it) }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val actionId = leaf.actionId ?: return
    scope.launch {
      when (val result = requestEvoPerformNodeAction(project.projectId(), pyProjectKey, nodeId, actionId)) {
        is EvoSelectResultDto.Ok -> Unit
        is EvoSelectResultDto.Error -> LOG.warn("Evo: failed to perform '${leaf.title}' for '$pyProjectKey': ${result.message}")
      }
    }
  }
}

/**
 * Switches the module interpreter to [ref] via [requestEvoSelectInterpreter]. The status-bar widget refreshes
 * itself on the resulting `rootsChanged` event, so no explicit refresh is needed here.
 */
internal class SelectEnvAction(
  private val project: Project,
  private val pyProjectKey: String,
  private val ref: PyInterpreterRef,
  /** Tool node this row came from; nests its version probe under that tool's trace context (e.g. conda). */
  private val nodeId: String,
  /** What statistics report this node as — see [EvoNodeStats]. */
  private val nodeStats: EvoNodeStats,
  /** Trace root of the popup tree this row belongs to; groups its version probe under that tree's root. */
  private val traceId: String,
  /**
   * The Pythons this environment can be rebuilt on, offered by the right button, or null when it offers no rebuild.
   *
   * Built by the caller, which holds the leaf the options came on: the panel's rows close over the rebuild call, and
   * this row is the only thing that outlives each popup.
   */
  private val basePythonPanelOrNull: EvoTreeNodeElement?,
  title: @org.jetbrains.annotations.Nls String,
  description: @org.jetbrains.annotations.Nls String,
  secondaryText: @org.jetbrains.annotations.Nls String?,
  icon: IconId,
  private val scope: CoroutineScope,
) : AnAction({ title }, { description }, icon.icon()), EvoLazyDetail, EvoBasePythonPanel, DumbAware {
  override val basePythonPanel: EvoTreeNodeElement? get() = basePythonPanelOrNull

  @Volatile
  private var versionRequested = false

  init {
    templatePresentation.setPlainText(title)
    secondaryText?.let { templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT, it) }
  }

  override fun actionPerformed(e: AnActionEvent) =
    selectInterpreter(project, pyProjectKey, ref, nodeId, nodeStats, evoSourceForNode(nodeId), scope)

  /** Resolves the interpreter version once, on first focus, for a detected env that has no version yet. */
  override fun resolveOnFocus(onResolved: () -> Unit) {
    val detected = ref as? PyInterpreterRef.DetectedPath ?: return
    if (versionRequested || templatePresentation.getClientProperty(ActionUtil.SECONDARY_TEXT) != null) return
    versionRequested = true
    scope.launch {
      val version = evoRpcOrNull { requestEvoResolveVersion(project.projectId(), pyProjectKey, nodeId, detected.homePath, traceId) } ?: "n/a"
      withContext(Dispatchers.EDT) {
        templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT, version)
        onResolved()
      }
    }
  }
}

private val LOG = logger<SelectEnvAction>()

/**
 * Destroys the environment at [envHomePath] and builds it again on [baseToken], once the user confirms.
 *
 * The confirmation is modal and comes first, because this is the one row in the widget that throws something away. It
 * is asked before [EvoConfiguringTracker] is set, so a cancelled dialog never fades the widget's tool logo, and after
 * the popup has closed — a leaf's action runs from `getFinalRunnable`, so the whole popup chain is already gone and the
 * dialog cannot appear behind it.
 */
internal fun recreateEvoEnv(
  project: Project,
  pyProjectKey: String,
  nodeId: String,
  /** What statistics report this node as — resolved by the caller, which holds the node list. */
  nodeStats: EvoNodeStats,
  /** The interpreter of the environment to destroy, and the name to show the user for it. */
  envHomePath: String,
  envTitle: @NlsSafe String,
  /** The base to build on, and what to call it in the confirmation. */
  baseToken: String,
  baseTitle: @NlsSafe String,
  /** Set for a row that offered an interpreter the machine lacks: the version to install before building anything. */
  installPythonVersion: String?,
  /** Whether this tool can fill the rebuilt environment again, which is whether the dialog offers that choice at all. */
  canSyncPackages: Boolean,
  /**
   * The tool that will manage the environment afterwards, and the one that manages it now — set only when the two
   * differ, so the confirmation can say the manager changes. Null when the tool stays the same.
   */
  toolChange: EvoToolChange?,
  scope: CoroutineScope,
) {
  scope.launch {
    val answer = withContext(Dispatchers.EDT) {
      confirmRecreate(project, envTitle, baseTitle, canSyncPackages, toolChange)
    } ?: return@launch
    project.service<EvoConfiguringTracker>().nodeId = nodeId   // so the widget fades this tool's logo while configuring
    PyEvoWidgetCollector.interpreterSelected(project, nodeStats, PyEvoWidgetCollector.RefKind.CREATE_ENV,
                                             PyEvoWidgetCollector.Source.RECREATE)
    val request = EvoRecreateRequestDto(envHomePath, baseToken, installPythonVersion, answer)
    when (val result = requestEvoRecreateEnvironment(project.projectId(), pyProjectKey, nodeId, request)) {
      is EvoSelectResultDto.Ok -> Unit
      is EvoSelectResultDto.Error -> LOG.warn("Evo: failed to rebuild '$envHomePath' for '$pyProjectKey': ${result.message}")
    }
  }
}

/**
 * Asks the user to confirm destroying [envTitle] and building it again on [baseTitle]; null when they decline.
 *
 * The answer is also the packages choice, because the dialog carries it: `true` fills the new environment from the
 * tool's lock or `pyproject.toml`, `false` leaves it as the tool made it. A tool that cannot fill one is asked plainly
 * and always answers `false` — a box that could do nothing is worse than no box.
 *
 * The choice lives here rather than in the panel behind it because this is the dialog that commits: everything the
 * rebuild does is decided on one screen, and a box the user ticked and then abandoned decides nothing.
 */
@RequiresEdt
private fun confirmRecreate(
  project: Project,
  envTitle: @NlsSafe String,
  baseTitle: @NlsSafe String,
  canSyncPackages: Boolean,
  toolChange: EvoToolChange?,
): Boolean? {
  val title = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.recreate.confirm.title")
  // Rebuilding from a node that does not manage this environment hands it to that node's tool. That is a bigger change
  // than the Python version, and the one thing the user cannot see from the row they clicked, so it is spelled out.
  val message =
    if (toolChange == null) PySdkFrontendBundle.message("evo.sdk.status.bar.popup.recreate.confirm.message", envTitle, baseTitle)
    else PySdkFrontendBundle.message("evo.sdk.status.bar.popup.recreate.confirm.message.tool",
                                     envTitle, toolChange.to, baseTitle, toolChange.from)
  val rebuild = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.recreate.confirm.yes")
  if (!canSyncPackages) {
    val confirmed = MessageDialogBuilder.yesNo(title, message)
      .yesText(rebuild)
      .icon(AllIcons.General.WarningDialog)
      .ask(project)
    return if (confirmed) false else null
  }
  // The platform's own two-step confirmation: the message, the buttons, and one checkbox under them. The exit code is
  // ours to define, so it carries both answers at once — declined, or confirmed with the box as the user left it.
  val answer = Messages.showCheckboxMessageDialog(
    message,
    title,
    arrayOf(rebuild, CommonBundle.getCancelButtonText()),
    PySdkFrontendBundle.message("evo.sdk.status.bar.popup.recreate.sync"),
    true,
    0,
    0,
    AllIcons.General.WarningDialog,
    BiFunction { exitCode, checkbox ->
      if (exitCode != 0) DECLINED else if (checkbox.isSelected) REBUILD_AND_FILL else REBUILD_ONLY
    },
  )
  return when (answer) {
    REBUILD_AND_FILL -> true
    REBUILD_ONLY -> false
    else -> null
  }
}

/** The tool a rebuild hands an environment to, and the one it takes it from — see [recreateEvoEnv]. */
internal class EvoToolChange(val from: @NlsSafe String, val to: @NlsSafe String)

/** The three answers [confirmRecreate]'s dialog can give. Ours to number, since the exit function defines them. */
private const val DECLINED = -1
private const val REBUILD_ONLY = 0
private const val REBUILD_AND_FILL = 1

/**
 * Switches the interpreter to [ref] — the one mutating call every row that picks an environment ends up in.
 *
 * Top-level so a base-interpreter row can reuse it without being a [SelectEnvAction] itself: such a row applies the ref
 * of the row it came from with its own interpreter substituted in, and nothing else about it is a select-env row.
 */
internal fun selectInterpreter(
  project: Project,
  pyProjectKey: String,
  ref: PyInterpreterRef,
  nodeId: String,
  /** What statistics report this node as — resolved by the caller, which holds the node list. */
  nodeStats: EvoNodeStats,
  /** Which popup section the row that triggered this belongs to — reported, never acted on. */
  source: PyEvoWidgetCollector.Source,
  scope: CoroutineScope,
) {
  project.service<EvoConfiguringTracker>().nodeId = nodeId   // so the widget fades this tool's logo while configuring
  scope.launch {
    PyEvoWidgetCollector.interpreterSelected(project, nodeStats, ref.evoRefKind(), source)
    when (val result = requestEvoSelectInterpreter(project.projectId(), pyProjectKey, ref, nodeId)) {
      is EvoSelectResultDto.Ok -> Unit
      is EvoSelectResultDto.Error -> LOG.warn("Evo: failed to select interpreter for '$pyProjectKey': ${result.message}")
    }
  }
}

/** The "download and install" row itself: the download icon and the action, with [onChosen] run when picked. */
internal fun installActionRow(onChosen: () -> Unit): EvoTreeLeafElement {
  val action = object : AnAction({ PySdkFrontendBundle.message("evo.sdk.status.bar.popup.add.new.install.action") },
                                 { "" }, AllIcons.Actions.Download), DumbAware {
    override fun actionPerformed(e: AnActionEvent) = onChosen()
  }
  return EvoTreeLeafElement(action)
}

/**
 * The row that folds the widget's tool list away and unfolds it again, running [onChosen] when picked.
 *
 * It reads as a disclosure rather than as a link: the text is the colour of the rows around it, and the chevron in its
 * own icon column points down while the list is folded and up while it is open — the way it will move when clicked. A
 * link colour said "this goes somewhere else", which is the one thing this row does not do.
 *
 * An ordinary leaf, so choosing it closes the popup and runs [onChosen] afterwards — which is what a list that has to be
 * rebuilt from the top wants anyway.
 */
internal fun showMoreRow(expanded: Boolean, anyToolShown: Boolean, onChosen: () -> Unit): EvoTreeLeafElement {
  val key = when {
    expanded -> "evo.sdk.status.bar.popup.show.less"
    // "Show More" needs something above it to be more than. With no tool row shown — an interpreter no node owns, so
    // none of them is the one in use — the row names what it opens instead.
    anyToolShown -> "evo.sdk.status.bar.popup.show.more"
    else -> "evo.sdk.status.bar.popup.show.all.tools"
  }
  val icon = if (expanded) AllIcons.General.ChevronUp else AllIcons.General.ChevronDown
  val action = object : AnAction({ PySdkFrontendBundle.message(key) }, { "" }, icon), DumbAware, EvoDisclosureRow {
    override fun actionPerformed(e: AnActionEvent) = onChosen()
  }
  return EvoTreeLeafElement(action)
}

/**
 * One base-interpreter row: badged with its tool's icon, titled by its (elided) path, with its version and whatever
 * qualifies it in the right-hand column, running [onChosen] when picked.
 *
 * The icon is what says where the interpreter came from — uv, Homebrew, pyenv — the way the v2 "Add Interpreter" dialog
 * does it, so a list of same-version installs is told apart at a glance instead of by reading a word off each row. The
 * plain Python logo stands in when the tool is unknown.
 */
internal fun baseInterpreterRow(base: EvoBasePythonDto, onChosen: () -> Unit): EvoTreeLeafElement {
  val icon = base.icon?.icon() ?: AllIcons.Language.Python
  val action = object : AnAction({ base.title }, { "" }, icon), DumbAware {
    override fun actionPerformed(e: AnActionEvent) = onChosen()
  }
  // An interpreter path is the one label here most likely to hold an underscore.
  action.templatePresentation.setPlainText(base.title)
  action.templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT, base.detail())
  base.titleTooltip?.let { action.templatePresentation.putClientProperty(ActionUtil.TOOLTIP_TEXT, it) }
  return EvoTreeLeafElement(action)
}

/** The row's right-hand column: the interpreter's version, then whatever qualifies it beyond the version. */
private fun EvoBasePythonDto.detail(): @NlsSafe String = listOfNotNull(version, qualifier).joinToString(", ")

internal fun selectEnvAction(
  project: Project,
  pyProjectKey: String,
  leaf: EvoLeafDto,
  nodeId: String,
  nodeStats: EvoNodeStats,
  traceId: String,
  scope: CoroutineScope,
  /** This row's rebuild picker, when the backend said it has one — see [EvoLeafDto.recreate]. */
  basePythonPanel: EvoTreeNodeElement? = null,
): SelectEnvAction =
  SelectEnvAction(
    project = project,
    pyProjectKey = pyProjectKey,
    ref = requireNotNull(leaf.ref) { "SELECT_ENV leaf without a ref" },
    nodeId = nodeId,
    nodeStats = nodeStats,
    traceId = traceId,
    basePythonPanelOrNull = basePythonPanel,
    title = leaf.title,
    description = leaf.description ?: "",
    secondaryText = leaf.secondaryText,
    icon = leaf.icon,
    scope = scope,
  )

internal fun selectEnvAction(project: Project, pyProjectKey: String, interpreter: PyInterpreterDto, nodeId: String, nodeStats: EvoNodeStats, traceId: String, scope: CoroutineScope): SelectEnvAction =
  SelectEnvAction(
    project = project,
    pyProjectKey = pyProjectKey,
    ref = interpreter.ref,
    nodeId = nodeId,
    nodeStats = nodeStats,
    traceId = traceId,
    // An "Associated" or "Shortcuts" row is not listed under the tool that owns its environment, so there is no tool
    // here to rebuild it with.
    basePythonPanelOrNull = null,
    title = interpreter.title,
    description = interpreter.description,
    secondaryText = null,
    icon = interpreter.icon,
    scope = scope,
  )

/**
 * The popup section a plain environment row belongs to, read off the node it was listed under.
 *
 * Derived rather than passed in because the three plain-select call sites already differ only by that node id, and the
 * two synthetic ids are exactly the two non-tool sections. Rows that are *not* a plain select — a base interpreter
 * behind the "…", an expanded per-version pick, an add-new version, an install row — name their section explicitly,
 * since the node id cannot tell them apart.
 */
internal fun evoSourceForNode(nodeId: String): PyEvoWidgetCollector.Source = when (nodeId) {
  EvoNodeIds.ASSOCIATED -> PyEvoWidgetCollector.Source.ASSOCIATED
  EvoNodeIds.SHORTCUTS -> PyEvoWidgetCollector.Source.SHORTCUTS
  else -> PyEvoWidgetCollector.Source.TOOL_NODE
}
