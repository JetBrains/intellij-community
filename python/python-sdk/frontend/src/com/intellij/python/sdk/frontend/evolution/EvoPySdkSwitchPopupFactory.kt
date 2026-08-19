package com.intellij.python.sdk.frontend.evolution

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ActionUtil.getAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.project.projectId
import com.intellij.python.sdk.common.evolution.EvoAddNewOptionDto
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.common.evolution.EvoLeafKind
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoNodeDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.intellij.python.sdk.common.evolution.PyInterpreterDto
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
import com.intellij.python.sdk.common.evolution.requestEvoNode
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
import com.intellij.python.sdk.frontend.evolution.components.EvoErrorException
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeAddNewNode
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeElement
import com.intellij.python.sdk.frontend.evolution.components.EvoLoadedNode
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeLazyNodeElement
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeLeafElement
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeNodeElement
import com.intellij.python.sdk.frontend.evolution.components.EvoTreePopup
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeSection
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeStaticNodeElement
import com.intellij.python.sdk.frontend.evolution.components.EvoWarningException
import com.intellij.python.sdk.frontend.icons.PythonSdkFrontendIcons
import kotlinx.coroutines.CoroutineScope
import java.util.UUID

private val managePackagesAction = object : AnAction(
  { PySdkFrontendBundle.message("evo.sdk.python.packaging.interpreter.widget.manage.packages") },
  { "" },
  PythonSdkFrontendIcons.PythonPackages,
), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let {
      ToolWindowManager.getInstance(it).getToolWindow("Python Packages")?.show()
    }
  }
}

/** Backend id of the `advanced` node (`AdvancedEvoEnvironmentProvider`), the anchor for the target-interpreters node. */
private const val ADVANCED_NODE_ID: String = "advanced"

/** Synthetic node id for the frontend-only "Associated environments" node (its rows are existing SDKs, never version-probed). */
private const val ASSOCIATED_NODE_ID: String = "associated"

private fun EvoLeafDto.toStubAction(): AnAction = object : AnAction({ title }, { description ?: "" }, icon.icon()), DumbAware {
  init {
    secondaryText?.let { templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT, it) }
  }

  override fun actionPerformed(e: AnActionEvent) {}
}

/** Synthetic node id for the "Shortcuts" autoconfigure rows (the backend ignores it for a [PyInterpreterRef.Autoconfigure] ref). */
private const val SHORTCUTS_NODE_ID: String = "shortcuts"

internal class EvoPySdkSwitchPopupFactory(
  val project: Project,
  val moduleName: @NlsSafe String,
  val currentInterpreter: PyInterpreterDto?,
  val nodes: List<EvoNodeDto>,
  val associated: List<PyInterpreterDto>,
  /** The "Shortcuts" rows shown when there is no current interpreter — the IDE's autoconfigure suggestion(s). */
  val shortcuts: List<EvoLeafDto>,
  val scope: CoroutineScope,
) {
  private fun EvoLeafDto.toElement(nodeId: String, traceId: String): EvoTreeElement {
    // A version-picker row (hatch not-yet-created env): a node whose submenu is the versions; choosing one creates the
    // env with that Python. The row's ref (CreateEnv) carries the per-row create token (hatch: the env name) → folder.
    val versions = createVersions
    if (!versions.isNullOrEmpty()) {
      val createToken = (ref as? PyInterpreterRef.CreateEnv)?.token.orEmpty()
      return EvoTreeAddNewNode(
        text = title,
        icon = icon.icon(),
        versions = versions.map { EvoTreeLeafElement(addVersionAction(nodeId, it, createToken)) },
      )
    }
    return when (kind) {
      EvoLeafKind.SELECT_ENV -> EvoTreeLeafElement(selectEnvAction(project, moduleName, this, nodeId, traceId, scope))
      // A runnable backend action (advanced add-interpreter) carries an actionId; a display-only row stays a no-op stub.
      EvoLeafKind.ACTION -> EvoTreeLeafElement(
        if (actionId != null) evoBackendActionLeaf(project, moduleName, nodeId, this, scope) else toStubAction()
      )
    }
  }

  private fun EvoLoadResultDto.toSections(nodeId: String, traceId: String): List<EvoTreeSection> = when (this) {
    is EvoLoadResultDto.Ok -> sections.map { section ->
      val leaves = section.leaves.map { it.toElement(nodeId, traceId) }
      EvoTreeSection(label = section.label?.let { ListSeparator(it) }, elements = leaves + listOfNotNull(addNewElement(section, nodeId)))
    }
    is EvoLoadResultDto.Warning -> throw EvoWarningException(message)
    is EvoLoadResultDto.Error -> throw EvoErrorException(message)
  }

  /**
   * The section's trailing "add new environment" element: for uv/pip (version options present) an expandable node
   * showing the auto-generated (non-editable) env folder name; its submenu lists the Python versions and is
   * repositioned to the left (see EvoTreePopup). Otherwise the classic modal "Add new environment" row; or null.
   */
  private fun addNewElement(section: EvoSectionDto, nodeId: String): EvoTreeElement? {
    val addNewEnv = section.addNewEnv
    return when {
      addNewEnv == null || addNewEnv.options.isEmpty() ->
        if (section.addNew) EvoTreeLeafElement(AddNewEnvAction(project, moduleName, nodeId, scope)) else null
      else -> EvoTreeAddNewNode(
        text = addNewEnv.name,
        icon = AllIcons.General.InlineAdd,
        versions = addNewEnv.options.map { EvoTreeLeafElement(addVersionAction(nodeId, it, addNewEnv.path)) },
      )
    }
  }

  /** A single Python-version row in the "add new environment" submenu: creates that version's env in [path] on click. */
  private fun addVersionAction(nodeId: String, option: EvoAddNewOptionDto, path: String): AnAction =
    object : AnAction({ addVersionText(option) }, { "" }, AllIcons.Language.Python), DumbAware {
      override fun actionPerformed(e: AnActionEvent) = createEvoEnv(project, moduleName, nodeId, option.token, path, scope)
    }

  /** Version row text: "Default" for uv's default (blank token), otherwise "Python <version>". */
  private fun addVersionText(option: EvoAddNewOptionDto): @NlsActions.ActionText String =
    if (option.token.isBlank()) PySdkFrontendBundle.message("evolution.action.add.env.child.default")
    else PySdkFrontendBundle.message("evolution.action.add.env.child.version", option.title)

  /** A single "Associated environments" node holding the interpreters the classic widget lists, shown inside the tool list. */
  private fun associatedInterpretersNode(traceId: String): EvoTreeStaticNodeElement =
    EvoTreeStaticNodeElement(
      text = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.associated.interpreters"),
      icon = AllIcons.Language.Python,
      sections = listOf(
        EvoTreeSection(
          label = null,
          elements = associated.map { EvoTreeLeafElement(selectEnvAction(project, moduleName, it, ASSOCIATED_NODE_ID, traceId, scope)) },
        ),
      ),
    )

  /**
   * Builds the popup tree. A fresh trace root (`traceId`) is minted here, so all of this tree's backend commands
   * (tool listing, version probes) group under one "Python Interpreter Widget" root — the widget builds a tree once
   * per data change and reuses it across re-opens, so a re-open makes no new calls and mints no new root.
   */
  fun buildTree(): EvoTreeStaticNodeElement {
    val projectId = project.projectId()
    val traceId = UUID.randomUUID().toString()

    // Tool nodes, with a single "Associated environments" node inserted just before "Advanced".
    val toolNodeElements = buildList<EvoTreeElement> {
      for (node in nodes) {
        if (node.id == ADVANCED_NODE_ID && associated.isNotEmpty()) add(associatedInterpretersNode(traceId))
        add(EvoTreeLazyNodeElement(node.label, node.icon.icon()) { force ->
          val result = requestEvoNode(projectId, moduleName, node.id, traceId, force)
          val refreshable = (result as? EvoLoadResultDto.Ok)?.refreshable == true
          EvoLoadedNode(result.toSections(node.id, traceId), refreshable)
        })
      }
      if (associated.isNotEmpty() && nodes.none { it.id == ADVANCED_NODE_ID }) add(associatedInterpretersNode(traceId))
    }

    val externalToolsSection = EvoTreeSection(
      label = ListSeparator(PySdkFrontendBundle.message("evo.sdk.status.bar.popup.select.environment")),
      elements = toolNodeElements,
    )

    val currentEnvSection = when (currentInterpreter) {
      // No interpreter: the "Shortcuts" section holds the IDE's autoconfigure suggestion(s); selecting one runs it.
      // Omitted entirely when there is nothing to suggest (no empty "Shortcuts" separator).
      null -> shortcuts.takeIf { it.isNotEmpty() }?.let { leaves ->
        EvoTreeSection(
          label = ListSeparator(PySdkFrontendBundle.message("evo.sdk.status.bar.popup.shortcuts")),
          elements = leaves.map { EvoTreeLeafElement(selectEnvAction(project, moduleName, it, SHORTCUTS_NODE_ID, traceId, scope)) },
        )
      }
      else -> EvoTreeSection(
        label = ListSeparator(currentInterpreter.title, currentInterpreter.icon.icon()),
        // The package-manager actions applicable to the current SDK (resolved by id on the backend), plus Manage Packages.
        elements = currentInterpreter.packageManagerActionIds.mapNotNull { getAction(it) }.map { EvoTreeLeafElement(it) } +
                   EvoTreeLeafElement(managePackagesAction),
      )
    }

    // Current-interpreter group is the last group.
    return EvoTreeStaticNodeElement(
      text = "",
      icon = AllIcons.Language.Python,
      sections = listOfNotNull(externalToolsSection, currentEnvSection),
    )
  }

  /** Wraps an already-built [tree] into a popup; [onClose] fires when it is dismissed (the widget starts its TTL then). */
  fun createPopup(tree: EvoTreeStaticNodeElement, context: DataContext, onClose: () -> Unit): ListPopup =
    EvoSdkManagerTreePopup(
      title = moduleName,
      evoTreeNodeElement = tree,
      dataContext = context,
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
