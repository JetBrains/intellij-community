package com.intellij.python.sdk.frontend.evolution

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.project.projectId
import com.intellij.python.sdk.common.evolution.EvoBasePythonDto
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.common.evolution.EvoSelectResultDto
import com.intellij.python.sdk.common.evolution.PyInterpreterDto
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
import com.intellij.python.sdk.common.evolution.evoRpcOrNull
import com.intellij.python.sdk.common.evolution.requestEvoPerformNodeAction
import com.intellij.python.sdk.common.evolution.requestEvoResolveVersion
import com.intellij.python.sdk.common.evolution.requestEvoSelectInterpreter
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
import com.intellij.python.sdk.frontend.evolution.components.EvoAlternatives
import com.intellij.python.sdk.frontend.evolution.components.EvoLazyDetail
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeLeafElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
  token: String,
  folder: String,
  name: String?,
  /** Set for a row that offered an interpreter the machine lacks: the version to install before creating anything. */
  installPythonVersion: String?,
  scope: CoroutineScope,
) {
  project.service<EvoConfiguringTracker>().nodeId = nodeId   // so the widget fades this tool's logo while configuring
  scope.launch {
    val ref = PyInterpreterRef.CreateEnv(token, folder, name, installPythonVersion)
    when (val result = requestEvoSelectInterpreter(project.projectId(), pyProjectKey, ref, nodeId)) {
      is EvoSelectResultDto.Ok -> Unit
      is EvoSelectResultDto.Error -> LOG.warn("Evo: failed to create '$nodeId' environment for '$pyProjectKey': ${result.message}")
    }
  }
}

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
  /** Trace root of the popup tree this row belongs to; groups its version probe under that tree's root. */
  private val traceId: String,
  /**
   * When [ref] creates an environment *from* a base interpreter (poetry's per-version rows), the other installs of that
   * same version — offered behind the row's inline "…". Empty when there is nothing to choose: an existing env already has
   * its interpreter, and a token that is not an interpreter path has no alternatives to speak of.
   */
  private val bases: List<EvoBasePythonDto>,
  title: @org.jetbrains.annotations.Nls String,
  description: @org.jetbrains.annotations.Nls String,
  secondaryText: @org.jetbrains.annotations.Nls String?,
  icon: IconId,
  private val scope: CoroutineScope,
) : AnAction({ title }, { description }, icon.icon()), EvoLazyDetail, EvoAlternatives, DumbAware {
  @Volatile
  private var versionRequested = false

  init {
    secondaryText?.let { templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT, it) }
  }

  override fun actionPerformed(e: AnActionEvent) = select(ref)

  override val alternativesTitle: String
    get() = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.add.new.base.title")

  // Built once: the renderer asks whether this row has alternatives on every repaint, and the hit-test on every mouse move.
  override val alternatives: List<EvoTreeLeafElement> by lazy {
    // Only a not-yet-created environment has a base interpreter left to choose; an existing one already has its own.
    when (val create = ref) {
      is PyInterpreterRef.CreateEnv -> bases.map { base ->
        baseInterpreterRow(base) { select(create.copy(token = base.token)) }
      }
      else -> emptyList()
    }
  }

  /** Applies [ref], which for an alternative is this row's own ref with the chosen interpreter substituted in. */
  private fun select(ref: PyInterpreterRef) =
    selectInterpreter(project, pyProjectKey, ref, nodeId, scope)

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
 * Switches the interpreter to [ref] — the one mutating call every row that picks an environment ends up in.
 *
 * Top-level so a base-interpreter row can reuse it without being a [SelectEnvAction] itself: such a row applies the ref
 * of the row it came from with its own interpreter substituted in, and nothing else about it is a select-env row.
 */
internal fun selectInterpreter(project: Project, pyProjectKey: String, ref: PyInterpreterRef, nodeId: String, scope: CoroutineScope) {
  project.service<EvoConfiguringTracker>().nodeId = nodeId   // so the widget fades this tool's logo while configuring
  scope.launch {
    when (val result = requestEvoSelectInterpreter(project.projectId(), pyProjectKey, ref, nodeId)) {
      is EvoSelectResultDto.Ok -> Unit
      is EvoSelectResultDto.Error -> LOG.warn("Evo: failed to select interpreter for '$pyProjectKey': ${result.message}")
    }
  }
}

/**
 * The rows [leaf] expands into: one per interpreter it could be created from, each applying [leaf]'s own ref with that
 * interpreter substituted in. Empty unless the leaf creates an environment *from* an interpreter — an existing
 * environment already has one.
 */
internal fun baseInterpreterRows(
  project: Project,
  pyProjectKey: String,
  leaf: EvoLeafDto,
  nodeId: String,
  scope: CoroutineScope,
): List<EvoTreeLeafElement> {
  val create = leaf.ref as? PyInterpreterRef.CreateEnv ?: return emptyList()
  return leaf.bases.map { base ->
    baseInterpreterRow(base) { selectInterpreter(project, pyProjectKey, create.copy(token = base.token), nodeId, scope) }
  }
}

/**
 * The single row an installable version expands into, or null when [leaf] is not one.
 *
 * Titled by the action rather than by the version, because in the expanded list the version is already the header above
 * it — see `EvoPySdkSwitchPopupFactory.toExpandedSections`.
 */
internal fun installInterpreterRow(
  project: Project,
  pyProjectKey: String,
  leaf: EvoLeafDto,
  nodeId: String,
  scope: CoroutineScope,
): EvoTreeLeafElement? {
  val create = leaf.ref as? PyInterpreterRef.CreateEnv ?: return null
  if (create.installPythonVersion == null) return null
  return installActionRow { selectInterpreter(project, pyProjectKey, create, nodeId, scope) }
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
 * One base-interpreter row: badged with its tool's icon, titled by its (elided) path, with its version and whatever
 * qualifies it in the right-hand column, running [onChosen] when picked.
 *
 * The icon is what says where the interpreter came from — uv, Homebrew, pyenv — the way the v2 "Add Interpreter" dialog
 * does it, so a list of same-version installs is told apart at a glance instead of by reading a word off each row. The
 * plain Python logo stands in when the tool is unknown.
 *
 * Shared by the expanded version list and the "…" menu, so the same interpreter can never be rendered two ways.
 */
internal fun baseInterpreterRow(base: EvoBasePythonDto, onChosen: () -> Unit): EvoTreeLeafElement {
  val icon = base.icon?.icon() ?: AllIcons.Language.Python
  val action = object : AnAction({ base.title }, { "" }, icon), DumbAware {
    override fun actionPerformed(e: AnActionEvent) = onChosen()
  }
  action.templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT, base.detail())
  base.titleTooltip?.let { action.templatePresentation.putClientProperty(ActionUtil.TOOLTIP_TEXT, it) }
  return EvoTreeLeafElement(action)
}

/** The row's right-hand column: the interpreter's version, then whatever qualifies it beyond the version. */
private fun EvoBasePythonDto.detail(): @NlsSafe String = listOfNotNull(version, qualifier).joinToString(", ")

internal fun selectEnvAction(project: Project, pyProjectKey: String, leaf: EvoLeafDto, nodeId: String, traceId: String, scope: CoroutineScope): SelectEnvAction =
  SelectEnvAction(
    project = project,
    pyProjectKey = pyProjectKey,
    ref = requireNotNull(leaf.ref) { "SELECT_ENV leaf without a ref" },
    nodeId = nodeId,
    traceId = traceId,
    bases = leaf.bases,
    title = leaf.title,
    description = leaf.description ?: "",
    secondaryText = leaf.secondaryText,
    icon = leaf.icon,
    scope = scope,
  )

internal fun selectEnvAction(project: Project, pyProjectKey: String, interpreter: PyInterpreterDto, nodeId: String, traceId: String, scope: CoroutineScope): SelectEnvAction =
  SelectEnvAction(
    project = project,
    pyProjectKey = pyProjectKey,
    ref = interpreter.ref,
    nodeId = nodeId,
    traceId = traceId,
    // An interpreter that already exists was built from whatever it was built from; there is nothing left to choose.
    bases = emptyList(),
    title = interpreter.title,
    description = interpreter.description,
    secondaryText = null,
    icon = interpreter.icon,
    scope = scope,
  )
