package com.intellij.python.sdk.frontend.evolution

import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.common.evolution.EvoSelectResultDto
import com.intellij.python.sdk.common.evolution.PyInterpreterDto
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
import com.intellij.python.sdk.common.evolution.requestEvoAddInterpreter
import com.intellij.python.sdk.common.evolution.requestEvoPerformNodeAction
import com.intellij.python.sdk.common.evolution.requestEvoResolveVersion
import com.intellij.python.sdk.common.evolution.requestEvoSelectInterpreter
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
import com.intellij.python.sdk.frontend.evolution.components.EvoLazyDetail
import com.intellij.icons.AllIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opens the v2 "Add Python Interpreter" dialog for the module, preselecting the manager of the tool node
 * ([nodeId]) this row belongs to. The status-bar widget refreshes on the resulting `rootsChanged`.
 */
class AddNewEnvAction(
  private val project: Project,
  private val moduleName: String,
  private val nodeId: String,
  private val scope: CoroutineScope,
) : AnAction(
  { PySdkFrontendBundle.message("evolution.action.add.new.env.text") },
  { PySdkFrontendBundle.message("evolution.action.add.new.env.description") },
  AllIcons.General.InlineAdd,
), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    scope.launch {
      when (val result = requestEvoAddInterpreter(project.projectId(), moduleName, nodeId)) {
        is EvoSelectResultDto.Ok -> Unit
        is EvoSelectResultDto.Error -> LOG.warn("Evo: failed to open Add Interpreter dialog for '$moduleName': ${result.message}")
      }
    }
  }
}

/**
 * Creates (and assigns to the module) a uv/pip environment for the chosen version [token] in [folder] (the env folder
 * name under the module base dir, editable in the widget), via [requestEvoSelectInterpreter] with a
 * [PyInterpreterRef.CreateEnv]. The widget refreshes itself on the resulting `rootsChanged`. Used by the in-widget
 * "add new environment" node's per-version children.
 */
internal fun createEvoEnv(project: Project, moduleName: String, nodeId: String, token: String, folder: String, scope: CoroutineScope) {
  scope.launch {
    when (val result = requestEvoSelectInterpreter(project.projectId(), moduleName, PyInterpreterRef.CreateEnv(token, folder), nodeId)) {
      is EvoSelectResultDto.Ok -> Unit
      is EvoSelectResultDto.Error -> LOG.warn("Evo: failed to create '$nodeId' environment for '$moduleName': ${result.message}")
    }
  }
}

/**
 * A runnable backend ACTION leaf (e.g. an "Advanced" add-interpreter / add-on-target action). Performing it runs
 * the backend action over RPC ([requestEvoPerformNodeAction]); the widget refreshes on the resulting `rootsChanged`.
 */
internal fun evoBackendActionLeaf(
  project: Project,
  moduleName: String,
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
      when (val result = requestEvoPerformNodeAction(project.projectId(), moduleName, nodeId, actionId)) {
        is EvoSelectResultDto.Ok -> Unit
        is EvoSelectResultDto.Error -> LOG.warn("Evo: failed to perform '${leaf.title}' for '$moduleName': ${result.message}")
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
  private val moduleName: String,
  private val ref: PyInterpreterRef,
  /** Tool node this row came from; nests its version probe under that tool's trace context (e.g. conda). */
  private val nodeId: String,
  /** Trace root of the popup tree this row belongs to; groups its version probe under that tree's root. */
  private val traceId: String,
  title: @org.jetbrains.annotations.Nls String,
  description: @org.jetbrains.annotations.Nls String,
  secondaryText: @org.jetbrains.annotations.Nls String?,
  icon: IconId,
  private val scope: CoroutineScope,
) : AnAction({ title }, { description }, icon.icon()), EvoLazyDetail, DumbAware {
  @Volatile
  private var versionRequested = false

  init {
    secondaryText?.let { templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT, it) }
  }

  override fun actionPerformed(e: AnActionEvent) {
    scope.launch {
      when (val result = requestEvoSelectInterpreter(project.projectId(), moduleName, ref, nodeId)) {
        is EvoSelectResultDto.Ok -> Unit
        is EvoSelectResultDto.Error -> LOG.warn("Evo: failed to select interpreter for '$moduleName': ${result.message}")
      }
    }
  }

  /** Resolves the interpreter version once, on first focus, for a detected env that has no version yet. */
  override fun resolveOnFocus(onResolved: () -> Unit) {
    val detected = ref as? PyInterpreterRef.DetectedPath ?: return
    if (versionRequested || templatePresentation.getClientProperty(ActionUtil.SECONDARY_TEXT) != null) return
    versionRequested = true
    scope.launch {
      val version = runCatching { requestEvoResolveVersion(project.projectId(), moduleName, nodeId, detected.homePath, traceId) }.getOrNull() ?: "n/a"
      withContext(Dispatchers.EDT) {
        templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT, version)
        onResolved()
      }
    }
  }
}

private val LOG = logger<SelectEnvAction>()

internal fun selectEnvAction(project: Project, moduleName: String, leaf: EvoLeafDto, nodeId: String, traceId: String, scope: CoroutineScope): SelectEnvAction =
  SelectEnvAction(
    project = project,
    moduleName = moduleName,
    ref = requireNotNull(leaf.ref) { "SELECT_ENV leaf without a ref" },
    nodeId = nodeId,
    traceId = traceId,
    title = leaf.title,
    description = leaf.description ?: "",
    secondaryText = leaf.secondaryText,
    icon = leaf.icon,
    scope = scope,
  )

internal fun selectEnvAction(project: Project, moduleName: String, interpreter: PyInterpreterDto, nodeId: String, traceId: String, scope: CoroutineScope): SelectEnvAction =
  SelectEnvAction(
    project = project,
    moduleName = moduleName,
    ref = interpreter.ref,
    nodeId = nodeId,
    traceId = traceId,
    title = interpreter.title,
    description = interpreter.description,
    secondaryText = null,
    icon = interpreter.icon,
    scope = scope,
  )
