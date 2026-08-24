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
import com.intellij.python.sdk.common.evolution.evoRpc
import com.intellij.python.sdk.common.evolution.requestEvoNode
import com.intellij.python.sdk.frontend.PySdkFrontendBundle
import com.intellij.python.sdk.frontend.evolution.components.EvoAlternatives
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
import com.intellij.python.sdk.frontend.evolution.components.EvoVersionRows
import com.intellij.python.sdk.frontend.evolution.components.EvoWarningException
import com.intellij.python.sdk.frontend.icons.PythonSdkFrontendIcons
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.NonNls

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
private const val ADVANCED_NODE_ID: String = EvoNodeIds.ADVANCED

/** Synthetic node id for the frontend-only "Associated environments" node (its rows are existing SDKs, never version-probed). */
private const val ASSOCIATED_NODE_ID: String = EvoNodeIds.ASSOCIATED

private fun EvoLeafDto.toStubAction(): AnAction = object : AnAction({ title }, { description ?: "" }, icon.icon()), DumbAware {
  init {
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
        versionRows = versionRows(
          versions,
          { addVersionAction(nodeId, it, createToken) },
          { base -> baseInterpreterRow(base) { createEnv(nodeId, base.token, createToken, null, null) } },
        ),
        // There is no interpreter to probe yet, so the row carries the backend's "n/a" in the same column where the
        // already-materialized envs show their resolved version.
        secondaryText = secondaryText,
      )
    }
    return when (kind) {
      EvoLeafKind.SELECT_ENV -> EvoTreeLeafElement(selectEnvAction(project, pyProjectKey, this, nodeId, traceId, scope))
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
    return sections.flatMap { section ->
      val (expandable, plain) = section.leaves.partition { it.bases.isNotEmpty() }
      buildList {
        val plainElements = plain.map { it.toElement(nodeId, traceId) } +
                            listOfNotNull(addNewElement(section, nodeId, takenNames))
        if (plainElements.isNotEmpty()) {
          add(EvoTreeSection(
            label = section.label?.let { ListSeparator(it) },
            elements = plainElements,
            labelTooltip = section.labelTooltip?.takeIf { it != section.label },
          ))
        }
        for (leaf in expandable) {
          add(EvoTreeSection(
            label = ListSeparator(leaf.title),
            elements = baseInterpreterRows(project, pyProjectKey, leaf, nodeId, scope),
          ))
        }
      }
    }
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
            { base -> baseInterpreterRow(base) { createEnv(nodeId, base.token, addNewEnv.path, editable, addNewEnv.name) } },
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
  ): EvoVersionRows {
    val collapsed = listOf(EvoTreeSection(elements = options.map { EvoTreeLeafElement(versionRow(it)) }))
    val expanded = when {
      options.none { it.bases.size > 1 } -> emptyList()
      else -> options.map { option ->
        EvoTreeSection(
          label = ListSeparator(addVersionText(option)),
          elements = option.bases.map { baseRow(it) },
        )
      }
    }
    return EvoVersionRows(collapsed, expanded)
  }

  /**
   * A single Python-version row in the "add new environment" submenu: on click creates that version's env with the
   * (possibly user-edited) name — from [editableName] when present, else [defaultName] — in the base location [path].
   *
   * When the machine has several installs of that version, the row also offers them behind its inline "…" ([EvoAlternatives]).
   * The row itself keeps creating from [EvoAddNewOptionDto.token], the IDE's pick among them, so the choice is there for
   * whoever wants it and invisible to everyone else.
   */
  private fun addVersionAction(
    nodeId: String,
    option: EvoAddNewOptionDto,
    path: String,
    editableName: EvoEditableName? = null,
    defaultName: String? = null,
  ): AnAction =
    object : AnAction({ addVersionText(option) }, { "" }, AllIcons.Language.Python), DumbAware, EvoAlternatives {
      override fun actionPerformed(e: AnActionEvent) = createEnv(nodeId, option.token, path, editableName, defaultName)

      override val alternativesTitle: String
        get() = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.add.new.base.title")

      // Built once: the renderer asks whether this row has alternatives on every repaint, and the hit-test on every
      // mouse move. Each lambda still reads the edited name when it runs, not now.
      override val alternatives: List<EvoTreeLeafElement> by lazy {
        option.bases.map { base ->
          baseInterpreterRow(base) { createEnv(nodeId, base.token, path, editableName, defaultName) }
        }
      }
    }

  /** Creates the environment a version or base-interpreter row stands for, unless the typed name rules it out. */
  private fun createEnv(nodeId: String, token: String, path: String, editableName: EvoEditableName?, defaultName: String?) {
    // A taken/blank name can't back a new env (the field shows it in red) — don't create.
    if (editableName != null && !editableName.isValid) return
    createEvoEnv(project, pyProjectKey, nodeId, token, path, editableName?.value ?: defaultName, scope)
  }

  /** Version row text: "Default" for uv's default (blank token), otherwise "Python <version>". */
  private fun addVersionText(option: EvoAddNewOptionDto): @NlsActions.ActionText String =
    if (option.token.isBlank()) PySdkFrontendBundle.message("evolution.action.add.env.child.default")
    else PySdkFrontendBundle.message("evolution.action.add.env.child.version", option.title)

  /**
   * Drops the intermediate one-row popup from a tool whose entire node is a single "add new environment" row (a tool with
   * no environments yet): with nothing to choose between, that step is pure friction, so expanding the tool lands straight
   * on the name field + Python versions. The row's name holder and its version rows are carried over, since the popup
   * renders the field — and the caption above it, the only thing left saying what the step does, and the
   * expand/collapse toggle below it — from the node it is showing. A node with any environment of its own keeps its
   * normal listing.
   */
  private fun EvoLoadedNode.withoutLoneAddNewStep(): EvoLoadedNode {
    val onlyRow = sections.singleOrNull()?.elements?.singleOrNull() as? EvoTreeAddNewNode ?: return this
    return EvoLoadedNode(onlyRow.sections.toList(), refreshable, onlyRow.editableName, onlyRow.versionRows)
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

  /** A single "Associated environments" node holding the interpreters the classic widget lists, shown inside the tool list. */
  private fun associatedInterpretersNode(traceId: String): EvoTreeStaticNodeElement =
    EvoTreeStaticNodeElement(
      text = PySdkFrontendBundle.message("evo.sdk.status.bar.popup.associated.interpreters"),
      icon = AllIcons.Language.Python,
      sections = listOf(
        EvoTreeSection(
          label = null,
          elements = associated.map { EvoTreeLeafElement(selectEnvAction(project, pyProjectKey, it, ASSOCIATED_NODE_ID, traceId, scope)) },
        ),
      ),
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

    // Tool nodes, with a single "Associated environments" node inserted just before "Advanced".
    val toolNodeElements = buildList<EvoTreeElement> {
      for (node in nodes) {
        if (node.id == ADVANCED_NODE_ID && associated.isNotEmpty()) add(associatedInterpretersNode(traceId))
        add(EvoTreeLazyNodeElement(node.label, node.icon.icon()) { force ->
          val result = evoRpc { requestEvoNode(projectId, pyProjectKey, node.id, traceId, force) }
          val refreshable = (result as? EvoLoadResultDto.Ok)?.refreshable == true
          val collapsed = result.toSections(node.id, traceId)
          // A tool whose rows carry interpreters (poetry) can be expanded like the add-new list; one whose rows do not
          // gets no toggle, since toExpandedSections is then empty.
          val expanded = result.toExpandedSections(node.id, traceId)
          val versionRows = if (expanded.isEmpty()) null else EvoVersionRows(collapsed, expanded)
          EvoLoadedNode(versionRows?.sections() ?: collapsed, refreshable, versionRows = versionRows)
            .withoutLoneAddNewStep()
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
          elements = leaves.map { EvoTreeLeafElement(selectEnvAction(project, pyProjectKey, it, SHORTCUTS_NODE_ID, traceId, scope)) },
        )
      }
      else -> EvoTreeSection(
        label = ListSeparator(currentInterpreter.title, currentInterpreter.icon.icon()),
        elements = packageManagerActions(context) + EvoTreeLeafElement(managePackagesAction),
      )
    }

    // Current-interpreter group is the last group.
    return EvoTreeStaticNodeElement(
      text = "",
      icon = AllIcons.Language.Python,
      sections = listOfNotNull(externalToolsSection, currentEnvSection),
    )
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
