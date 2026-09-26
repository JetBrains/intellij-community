// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.execution.ExecutionException
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil.BW
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.hover.TreeHoverListener
import com.intellij.ui.paint.RectanglePainter
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.packageRequirements.PackageTreeNode
import com.jetbrains.python.packaging.toolwindow.ModuleDepName
import com.jetbrains.python.packaging.toolwindow.PyPackageIcons
import com.jetbrains.python.packaging.toolwindow.WorkspaceMemberName
import com.jetbrains.python.packaging.toolwindow.buildDisplayablePackages
import com.jetbrains.python.packaging.toolwindow.model.DependencyGroupNode
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstallablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.model.LoadingNode
import com.jetbrains.python.packaging.toolwindow.model.ModuleDependencyDisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.RequirementPackage
import com.jetbrains.python.packaging.toolwindow.model.UndeclaredPackagesGroup
import com.jetbrains.python.packaging.toolwindow.model.WorkspaceMember
import com.jetbrains.python.packaging.toolwindow.ui.PyChangeVersionPopupLauncher
import com.jetbrains.python.packaging.toolwindow.ui.PyInstallPackageDialogLauncher
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.JViewport
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Typed tree-row payload the pane attaches to every `DefaultMutableTreeNode.userObject`. Classified
 * once at tree build (`toSwingNode`) so the renderer, navigator and mouse handler read a sealed
 * variant instead of running a `when {}` on name sets each paint / hover.
 */
internal sealed interface PkgTreeRow {
  val pkg: PackageTreeNode
  data class Package(override val pkg: PackageTreeNode) : PkgTreeRow
  data class WorkspaceMember(override val pkg: PackageTreeNode) : PkgTreeRow
  data class ModuleDep(override val pkg: PackageTreeNode) : PkgTreeRow
}

/**
 * Narrows an opaque tree-component reference (`TreePath.lastPathComponent`, `DefaultTreeModel.root`
 * — both typed `Any` / `Object` by Swing) to the [PkgTreeRow] payload the settings pane paints.
 * Kept `internal` so [com.intellij.python.junit5Tests.unit.PyPackagesTreePaneNavigationTest] can
 * pin every branch — the guard is the only thing standing between the Swing API and typed
 * downstream reads, so a silent shape change (userObject stops being a [PkgTreeRow]) is worth
 * catching in a unit test.
 */
internal fun rowFromTreeComponent(component: Any?): PkgTreeRow? {
  if (component !is DefaultMutableTreeNode) return null
  val userObject = component.userObject
  if (userObject !is PkgTreeRow) return null
  return userObject
}

/** Convenience: extract the [PackageTreeNode] payload from a tree component. */
internal fun packageFromTreeComponent(component: Any?): PackageTreeNode? =
  rowFromTreeComponent(component)?.pkg

/**
 * Row → package lookup facade. Splits the pane's Swing tree walk from the business logic on top
 * of it — [isActionable], [workspaceMemberNameForRow] and the mouse handler read a plain
 * `packageAt(row)` API instead of a `TreePath` chain. Tests plug a fake navigator via
 * [com.intellij.python.junit5Tests.unit.PyPackagesTreePaneNavigationTest]; production uses
 * [SwingPkgRowNavigator], the only place that still touches `TreePath` directly.
 */
internal interface PkgRowNavigator {
  fun packageAt(row: Int): PackageTreeNode?
  fun parentPackageOf(row: Int): PackageTreeNode?
  fun parentIsRoot(row: Int): Boolean
}

private class SwingPkgRowNavigator(
  private val tree: Tree,
  private val rootNode: DefaultMutableTreeNode,
) : PkgRowNavigator {
  override fun packageAt(row: Int): PackageTreeNode? {
    val path = tree.getPathForRow(row) ?: return null
    return packageFromTreeComponent(path.lastPathComponent)
  }

  override fun parentPackageOf(row: Int): PackageTreeNode? {
    val parent = tree.getPathForRow(row)?.parentPath ?: return null
    return packageFromTreeComponent(parent.lastPathComponent)
  }

  override fun parentIsRoot(row: Int): Boolean {
    val parent = tree.getPathForRow(row)?.parentPath ?: return false
    return parent.lastPathComponent === rootNode
  }
}

/**
 * Inputs the [isActionableRow] decision reads. Extracted into a plain data class so the rule can
 * be pinned by [com.intellij.python.junit5Tests.unit.PyPackagesTreePaneNavigationTest] without a
 * Swing tree — the pane just projects its Swing state into this shape and calls the pure function.
 */
internal data class RowActionabilityInputs(
  val nodeName: String,
  val parentIsRoot: Boolean,
  val parentNodeName: String?,
  val workspaceMembers: Set<WorkspaceMemberName>,
  val moduleDeps: Set<ModuleDepName>,
)

/**
 * Pure "is this row actionable" rule, mirroring PPTW: only **declared** packages act — workspace
 * member headers, JPS module-dep pseudo-rows and transitive sub-tree entries do not. Split from
 * the pane so a regression in the rule is caught by [RowActionabilityInputs] tests instead of a
 * missed hover-icon click during smoke.
 */
internal fun isActionableRow(inputs: RowActionabilityInputs): Boolean {
  if (WorkspaceMemberName(inputs.nodeName) in inputs.workspaceMembers) return false
  if (ModuleDepName(inputs.nodeName) in inputs.moduleDeps) return false
  if (inputs.parentIsRoot) return true
  val parentName = inputs.parentNodeName ?: return false
  return WorkspaceMemberName(parentName) in inputs.workspaceMembers
}

/**
 * Wraps [pkg] in the [PkgTreeRow] variant that matches the two "special" name sets the pane tracks.
 * Pure function so [com.intellij.python.junit5Tests.unit.PyPackagesTreePaneNavigationTest] can pin
 * every branch — a name that appears in both sets falls through to [PkgTreeRow.WorkspaceMember]
 * first, matching the renderer's precedence.
 */
internal fun classifyPkgRow(
  pkg: PackageTreeNode,
  workspaceMembers: Set<WorkspaceMemberName>,
  moduleDeps: Set<ModuleDepName>,
): PkgTreeRow = when {
  WorkspaceMemberName(pkg.name.name) in workspaceMembers -> PkgTreeRow.WorkspaceMember(pkg)
  ModuleDepName(pkg.name.name) in moduleDeps -> PkgTreeRow.ModuleDep(pkg)
  else -> PkgTreeRow.Package(pkg)
}

/**
 * Tree view of installed packages for a given Python SDK. Hierarchical `PackageTreeNode` from
 * [com.jetbrains.python.packaging.management.DependencyTreeProvider] when available, falls back to a flat
 * list. Uninstall + change-version go through the public launchers; install opens the shared dialog.
 */
internal class PyPackagesTreePane(
  private val moduleOrProject: ModuleOrProject.ModuleAndProject,
  parentDisposable: Disposable,
) {
  private val project = moduleOrProject.project
  private val moduleContext = moduleOrProject.module
  private val preselectModuleName: String = moduleContext.name
  private val rootNode: DefaultMutableTreeNode = DefaultMutableTreeNode()
  private val treeModel: DefaultTreeModel = DefaultTreeModel(rootNode)
  private val rowActionsState = PkgRowActionsState()

  /**
   * Assigned inside the tree's `.apply { }` init block because [SwingPkgRowNavigator] needs the
   * Tree instance. Every read on this field happens after construction (paint, mouse events),
   * so `lateinit` is safe — the alternative would be a `by lazy` that reads the still-`null`
   * `tree` field during `.apply { }`.
   */
  private lateinit var navigator: PkgRowNavigator

  private val tree: Tree = object : Tree(treeModel) {
    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      paintRowActions(g)
    }

    private fun paintRowActions(g: Graphics) {
      val visible = visibleRect
      val firstRow = getClosestRowForLocation(visible.x, visible.y).takeIf { it >= 0 } ?: return
      val lastRow = getClosestRowForLocation(visible.x, visible.y + visible.height - 1).takeIf { it >= 0 } ?: (rowCount - 1)
      val hoveredRow = TreeHoverListener.getHoveredRow(this)
      for (row in firstRow..lastRow) {
        val isRowHovered = row == hoveredRow
        val isRowSelected = isRowSelected(row)
        if (!isRowHovered && !isRowSelected) continue
        // Transitive-dep rows and workspace-member headers have no actions — matches PPTW.
        if (!isActionable(row)) continue
        navigator.packageAt(row) ?: continue
        val bounds = getRowBounds(row) ?: continue
        // Row-hover uses the same accent band styling as selection, so the icons must also flip to the
        // selection foreground; otherwise the mid-grey stroke reads as disabled against the hover fill.
        val paintAsSelected = isRowSelected || isRowHovered
        for ((slot, rect) in pkgRowActionRects(this, bounds)) {
          val icon = iconForPkgSlot(slot, paintAsSelected)
          val isSlotHovered = rowActionsState.hoveredRow == row && rowActionsState.hoveredSlot == slot
          val isSlotPressed = rowActionsState.pressedRow == row && rowActionsState.pressedSlot == slot
          if (isSlotHovered || isSlotPressed) paintIconHoverBg(g, rect.x, rect.y, icon.iconWidth, icon.iconHeight, isSlotPressed)
          icon.paintIcon(this, g, rect.x, rect.y)
        }
      }
    }
  }.apply {
    navigator = SwingPkgRowNavigator(this, rootNode)
    isRootVisible = false
    showsRootHandles = true
    emptyText.text = PyBundle.message("configurable.PyWorkspaceStructureConfigurable.packages.no.sdk")
    cellRenderer = PackageCellRenderer()
    border = JBUI.Borders.empty()
    TreeHoverListener.DEFAULT.addTo(this)
    val handler = PkgRowActionsMouseHandler(
      this, rowActionsState, navigator,
      isActionableRow = { row -> isActionable(row) },
      workspaceMemberOf = { row -> workspaceMemberNameForRow(row) },
      onChangeVersion = { pkg, member, anchor -> triggerChangeVersionDialog(pkg, member, anchor) },
      onDelete = { pkg, member -> triggerDeletePackage(pkg, member) },
    )
    addMouseListener(handler)
    addMouseMotionListener(handler)
  }
  private val scrollPane: JBScrollPane = JBScrollPane(tree).apply {
    border = JBUI.Borders.empty()
    viewportBorder = JBUI.Borders.empty()
    minimumSize = Dimension(0, 0)
    preferredSize = Dimension(0, 0)
  }
  private val searchField: ExtendableTextField = ExtendableTextField().apply {
    emptyText.text = PyBundle.message("configurable.PyWorkspaceStructureConfigurable.packages.search.placeholder")
    TextComponentEmptyText.setupPlaceholderVisibility(this)
    margin = JBUI.insets(BW.get(), UIUtil.DEFAULT_VGAP)
    addExtension(object : ExtendableTextComponent.Extension {
      override fun getIcon(hovered: Boolean) = AllIcons.Actions.Search
      override fun isIconBeforeText() = true
    })
    addExtension(ExtendableTextComponent.Extension.create(
      ADD_PACKAGE_ICON,
      PyBundle.message("configurable.PyWorkspaceStructureConfigurable.packages.install.tooltip"),
      Runnable { triggerInstallPackageDialog() },
    ))
    document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        searchQuery = text
        rebuildTreeModel()
      }
    })
  }

  val component: JComponent = JPanel(BorderLayout()).apply {
    border = JBUI.Borders.empty()
    add(JPanel(BorderLayout()).apply {
      border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP + BW.get(), BW.get())
      add(searchField, BorderLayout.CENTER)
    }, BorderLayout.NORTH)
    add(scrollPane, BorderLayout.CENTER)
  }

  /** Latest hierarchical package tree loaded for [currentSdk], filtered on demand by [searchQuery]. */
  private var loadedNodes: List<PackageTreeNode> = emptyList()
  /** Names of workspace-member roots (uv/poetry); the renderer swaps their icon to the Python module glyph. */
  private var workspaceMemberNames: Set<WorkspaceMemberName> = emptySet()
  /**
   * Names of JPS `ModuleOrderEntry` targets rendered as pseudo-package rows in plain multi-module
   * projects that don't declare a uv/poetry workspace. Same icon swap as [workspaceMemberNames] so
   * both flavours read as "another module" rather than "installed pip package".
   */
  private var moduleDepNames: Set<ModuleDepName> = emptySet()
  private var searchQuery: String = ""
  private var currentSdk: Sdk? = null

  /** Cached package manager for [currentSdk]; kept alongside so the loader / reload paths do not resolve it twice. */
  private var currentManager: PythonPackageManager? = null

  init {
    // Application bus, not project — `PACKAGE_MANAGEMENT_TOPIC` is published on the app bus and only
    // propagates to direct children, but PPTW's own listener uses the app bus too, so match it here
    // to avoid a subtle propagation mismatch that made change-version updates land only on reopen.
    ApplicationManager.getApplication().messageBus.connect(parentDisposable).subscribe(
      PythonPackageManager.PACKAGE_MANAGEMENT_TOPIC,
      object : PythonPackageManagementListener {
        override fun packagesChanged(sdk: Sdk) {
          // Match by name: `currentSdk` may be an editable copy from `ProjectSdksModel` while the
          // event fires against the original SDK from `ProjectJdkTable`, so `===` would miss it.
          if (sdk.name != currentSdk?.name) return
          ApplicationManager.getApplication().invokeLater({ reloadCurrentSdk() }, project.disposed)
        }
      },
    )
  }

  /**
   * Re-runs the async load for the SDK currently displayed. Called from the packaging-event listener
   * so the tree reflects install / uninstall / change-version outcomes that the user triggered from
   * inline row actions. Cache is invalidated first so uv / poetry re-scan `uv tree` instead of
   * returning the pre-op snapshot.
   *
   * `treeProvider?.invalidateCache()` uses a safe call because a manager without a tree provider
   * still drives this pane (via the flat-list fallback path in [setSdk]); there is nothing to
   * invalidate on that side, so the safe call is intentional, not defensive.
   */
  private fun reloadCurrentSdk() {
    val sdk = currentSdk ?: return
    currentManager?.treeProvider?.invalidateCache()
    currentSdk = null
    currentManager = null
    setSdk(sdk)
  }

  /**
   * Returns the workspace-member name the row's package belongs to, or `null` if it lives directly
   * at the tree root or is itself a workspace-member header. Used to route inline uninstall /
   * change-version calls through the right `--package <member>` scope for uv / poetry workspaces.
   */
  private fun workspaceMemberNameForRow(row: Int): String? {
    val parentPkg = navigator.parentPackageOf(row) ?: return null
    val name = parentPkg.name.name
    return if (WorkspaceMemberName(name) in workspaceMemberNames) name else null
  }

  fun setSdk(sdk: Sdk?) {
    if (currentSdk === sdk) return
    currentSdk = sdk
    if (sdk == null) {
      currentManager = null
      applyUiState(PackageTreeUiState.noSdk())
      return
    }
    val manager = PythonPackageManager.forSdk(project, sdk)
    currentManager = manager

    // Sync fast path: paint the cached snapshot immediately so the tab is not blank while the tree loader
    // runs. The tree loader below then replaces this with the hierarchical view if the manager provides one.
    applyUiState(snapshotUiState(manager))

    PyPackageCoroutine.launch(project, Dispatchers.IO) {
      val uiState = safeLoadPackageTree(manager)
      withContext(Dispatchers.EDT) {
        if (currentSdk !== sdk) return@withContext
        applyUiState(uiState)
      }
    }
  }

  private fun applyUiState(state: PackageTreeUiState) {
    tree.emptyText.text = state.emptyText
    loadedNodes = state.loadedNodes
    workspaceMemberNames = state.workspaceMemberNames
    moduleDepNames = state.moduleDepNames
    rebuildTreeModel()
  }

  private fun snapshotUiState(manager: PythonPackageManager): PackageTreeUiState {
    val snapshot = manager.listInstalledPackagesSnapshot()
    return if (snapshot.isNotEmpty()) {
      PackageTreeUiState.loaded(snapshot.map { it.toFlatNode() })
    }
    else {
      PackageTreeUiState.loading()
    }
  }



  /**
   * Runs PPTW's shared [buildDisplayablePackages] pipeline with the per-module member filter, then converts
   * the flat [DisplayablePackage] output into the [PackageTreeNode] shape this pane's renderer / mouse
   * handlers already read. `DisplayablePackage`s have no shared nodes and cannot cycle (the pipeline's own
   * project-package + path guards enforce this), so the Swing tree never balloons — the previous per-graph
   * OOM on large uv workspaces (PY-89840) is structurally impossible here.
   */
  private suspend fun safeLoadPackageTree(manager: PythonPackageManager): PackageTreeUiState = try {
    val result = buildDisplayablePackages(
      moduleOrProject = moduleOrProject,
      manager = manager,
      memberFilter = moduleContext.name,
    )
    if (result.unavailable != null) {
      PackageTreeUiState.loaded(emptyList())
    }
    else {
      val (nodes, memberNames) = convertDisplayableToTreeNodes(result.packages)
      val allMemberNames = memberNames + result.moduleAliasNames
      val topLevelNames = nodes.mapTo(HashSet()) { it.name.name }
      val depNames = result.jpsModuleDependencyNames
        .asSequence()
        .filter { it.value !in topLevelNames }
        .distinct()
        .toSet()
      val finalNodes = if (memberNames.isEmpty() && depNames.isNotEmpty()) {
        nodes + depNames.map { PackageTreeNode(name = PyPackageName.from(it.value), version = null) }
      }
      else nodes
      PackageTreeUiState.loaded(finalNodes, allMemberNames, depNames)
    }
  }
  catch (_: ExecutionException) {
    PackageTreeUiState.loadError()
  }
  catch (_: IOException) {
    PackageTreeUiState.loadError()
  }

  /**
   * Flattens the [DisplayablePackage] pipeline output to [PackageTreeNode]s the settings-side renderer
   * consumes. Member headers are dropped when a per-module filter is active — the module list on the left
   * already surfaces the member's identity, so nesting a redundant top-level row would only add an indent.
   * Cross-member references cut off at a single row per [buildDisplayablePackages]' `projectPackageNames`
   * guard, so a member depending on another member shows just the target member, without its packages.
   *
   * Every convertible variant of [DisplayablePackage] resolves to a non-null builder here, so the top-level
   * loop no longer squelches a `null`. The recursive [convertChildOrNull] helper stays nullable because
   * `getRequirements()` may hand back a non-convertible variant per the sealed contract.
   */
  private fun convertDisplayableToTreeNodes(packages: List<DisplayablePackage>): Pair<List<PackageTreeNode>, Set<WorkspaceMemberName>> {
    val memberNames = HashSet<WorkspaceMemberName>()
    val nodes = mutableListOf<PackageTreeNode>()
    for (pkg in packages) {
      when (pkg) {
        is WorkspaceMember -> {
          memberNames.add(WorkspaceMemberName(pkg.name))
          if (pkg.name == moduleContext.name) {
            // Selected member — surface its own packages as top-level rows (no member header row).
            appendConvertedChildren(pkg, memberNames, nodes)
          }
          else {
            // Workspace-wide roll-up: keep the member header + its packages nested under it.
            val header = PackageTreeNode(name = PyPackageName.from(pkg.name), version = null)
            appendConvertedChildren(pkg, memberNames, header.children)
            nodes.add(header)
          }
        }
        is InstalledPackage -> nodes.add(installedNode(pkg, memberNames))
        is RequirementPackage -> nodes.add(requirementNode(pkg, memberNames))
        is UndeclaredPackagesGroup,
        is DependencyGroupNode, -> nodes.add(groupingNode(pkg, memberNames))
        is ModuleDependencyDisplayablePackage -> nodes.add(moduleDepNode(pkg))
        is InstallablePackage, is LoadingNode, -> Unit
      }
    }
    return nodes to memberNames
  }

  private fun installedNode(pkg: InstalledPackage, memberNames: MutableSet<WorkspaceMemberName>): PackageTreeNode =
    PackageTreeNode(
      name = PyPackageName.from(pkg.name),
      version = pkg.instance.version.ifEmpty { null },
      group = pkg.dependencyGroup?.name,
    ).also { appendConvertedChildren(pkg, memberNames, it.children) }

  private fun requirementNode(pkg: RequirementPackage, memberNames: MutableSet<WorkspaceMemberName>): PackageTreeNode {
    if (pkg.isProjectPackage) memberNames.add(WorkspaceMemberName(pkg.name))
    return PackageTreeNode(
      name = PyPackageName.from(pkg.name),
      version = pkg.instance.version.ifEmpty { null },
      group = pkg.group,
    ).also { appendConvertedChildren(pkg, memberNames, it.children) }
  }

  /**
   * Same shape for both [UndeclaredPackagesGroup] and [DependencyGroupNode] — a headerless folder
   * that carries its converted children. Sharing this builder keeps the two variants from drifting.
   */
  private fun groupingNode(pkg: DisplayablePackage, memberNames: MutableSet<WorkspaceMemberName>): PackageTreeNode =
    PackageTreeNode(name = PyPackageName.from(pkg.name), version = null)
      .also { appendConvertedChildren(pkg, memberNames, it.children) }

  private fun moduleDepNode(pkg: ModuleDependencyDisplayablePackage): PackageTreeNode =
    PackageTreeNode(name = PyPackageName.from(pkg.name), version = null)

  private fun appendConvertedChildren(
    pkg: DisplayablePackage,
    memberNames: MutableSet<WorkspaceMemberName>,
    into: MutableList<PackageTreeNode>,
  ) {
    for (child in pkg.getRequirements()) {
      val converted = convertChildOrNull(child, memberNames) ?: continue
      into.add(converted)
    }
  }

  /**
   * Recursive helper for [appendConvertedChildren]. Returns `null` for sealed variants that never
   * belong under a real package row ([WorkspaceMember], [InstallablePackage], [LoadingNode]) so
   * callers can drop them without re-checking the type.
   */
  private fun convertChildOrNull(pkg: DisplayablePackage, memberNames: MutableSet<WorkspaceMemberName>): PackageTreeNode? = when (pkg) {
    is InstalledPackage -> installedNode(pkg, memberNames)
    is RequirementPackage -> requirementNode(pkg, memberNames)
    is UndeclaredPackagesGroup,
    is DependencyGroupNode,
      -> groupingNode(pkg, memberNames)
    is ModuleDependencyDisplayablePackage -> moduleDepNode(pkg)
    is WorkspaceMember,
    is InstallablePackage,
    is LoadingNode,
      -> null
  }

  /**
   * Single-shape snapshot the pane reads to paint itself. Folded from both the success and the
   * failure branches of [safeLoadPackageTree] so the pane never applies a half-updated state — a
   * new field added here must be filled by every factory below or the compiler rejects the call.
   *
   * Factories hand the resolved translated [emptyText] rather than a bundle key so callers /
   * tests can assert on the string directly, without touching [PyBundle].
   */
  private data class PackageTreeUiState(
    val emptyText: @NlsContexts.StatusText String,
    val loadedNodes: List<PackageTreeNode>,
    val workspaceMemberNames: Set<WorkspaceMemberName>,
    val moduleDepNames: Set<ModuleDepName>,
  ) {
    companion object {
      fun noSdk(): PackageTreeUiState = PackageTreeUiState(
        emptyText = PyBundle.message("configurable.PyWorkspaceStructureConfigurable.packages.no.sdk"),
        loadedNodes = emptyList(),
        workspaceMemberNames = emptySet(),
        moduleDepNames = emptySet(),
      )

      fun loading(): PackageTreeUiState = PackageTreeUiState(
        emptyText = PyBundle.message("configurable.PyWorkspaceStructureConfigurable.packages.loading"),
        loadedNodes = emptyList(),
        workspaceMemberNames = emptySet(),
        moduleDepNames = emptySet(),
      )

      fun loadError(): PackageTreeUiState = PackageTreeUiState(
        emptyText = PyBundle.message("configurable.PyWorkspaceStructureConfigurable.packages.load.error"),
        loadedNodes = emptyList(),
        workspaceMemberNames = emptySet(),
        moduleDepNames = emptySet(),
      )

      fun loaded(
        nodes: List<PackageTreeNode>,
        workspaceMembers: Set<WorkspaceMemberName> = emptySet(),
        moduleDeps: Set<ModuleDepName> = emptySet(),
      ): PackageTreeUiState = PackageTreeUiState(
        emptyText = PyBundle.message("configurable.PyWorkspaceStructureConfigurable.packages.empty"),
        loadedNodes = nodes,
        workspaceMemberNames = workspaceMembers,
        moduleDepNames = moduleDeps,
      )
    }
  }

  /**
   * Search + sort are pure list transforms on the immutable `PackageTreeNode` snapshot the pane
   * already holds, so they run on [Dispatchers.Default]; only the Swing mutation flips back to EDT.
   * Stale bounce: if the snapshot the coroutine started from was replaced by a newer load or the
   * user typed further into the search box, the result is dropped — the next scheduled coroutine
   * paints the fresh state.
   */
  private fun rebuildTreeModel() {
    val snapshot = loadedNodes
    val query =  searchQuery.trim().lowercase()
    PyPackageCoroutine.launch(project, Dispatchers.Default) {
      val prepared = filterNodes(snapshot, query).sortedBy { it.name.name.lowercase() }
      withContext(Dispatchers.EDT) {
        if (loadedNodes !== snapshot || searchQuery.trim().lowercase() != query) return@withContext
        val members = workspaceMemberNames
        val deps = moduleDepNames
        rootNode.removeAllChildren()
        prepared.forEach { rootNode.add(it.toSwingNode(members, deps)) }
        treeModel.reload()
        val expandAll = query.isNotEmpty()
        var row = 0
        while (row < tree.rowCount) {
          val path = tree.getPathForRow(row) ?: break
          if (expandAll || path.pathCount == 2) tree.expandPath(path)
          row++
        }
      }
    }
  }

  /**
   * Depth-first filter. A node survives when its name contains [query], or any descendant does. Mirrors
   * the tool-window search — the user finds a transitive dep and still sees the declaring root above it.
   */
  private fun filterNodes(nodes: List<PackageTreeNode>, query: String): List<PackageTreeNode> {
    if (query.isEmpty()) return nodes
    fun match(node: PackageTreeNode): PackageTreeNode? {
      val selfMatches = node.name.name.contains(query)
      val filteredChildren = node.children.mapNotNull { match(it) }
      if (!selfMatches && filteredChildren.isEmpty()) return null
      return PackageTreeNode(node.name, filteredChildren.toMutableList(), node.group, node.version)
    }
    return nodes.mapNotNull { match(it) }
  }

  /**
   * Opens [com.jetbrains.python.packaging.toolwindow.ui.PyInstallPackageDialog] via the public
   * [PyInstallPackageDialogLauncher]. Settings stays open — the install popup is non-modal and stacks
   * as a third layer above the Settings dialog so the user can dismiss it and land back on the same
   * Packages tab, matching PPTW's popup-over-tool-window flow.
   */
  private fun triggerInstallPackageDialog() {
    val sdkToOpenOn = currentSdk
    val moduleForPreselect = preselectModuleName
    PyInstallPackageDialogLauncher.open(
      project = project,
      sdk = sdkToOpenOn,
      preselectModuleName = moduleForPreselect,
    )
  }

  /**
   * A row is "actionable" (hover-icons paint + mouse handler fires) when it represents a **declared** package
   * — not a workspace-member header and not a transitive dependency. Matches PPTW's own rule: only wire
   * uninstall / change-version for the declared level, skip transitive sub-trees. Swing-agnostic
   * decision lives in [isActionableRow]; this method only projects the tree state via [navigator].
   */
  private fun isActionable(row: Int): Boolean {
    val pkg = navigator.packageAt(row) ?: return false
    val parentIsRoot = navigator.parentIsRoot(row)
    val parentPkg = if (parentIsRoot) null else (navigator.parentPackageOf(row) ?: return false)
    return isActionableRow(
      RowActionabilityInputs(
        nodeName = pkg.name.name,
        parentIsRoot = parentIsRoot,
        parentNodeName = parentPkg?.name?.name,
        workspaceMembers = workspaceMemberNames,
        moduleDeps = moduleDepNames,
      )
    )
  }

  /**
   * "Change version" opens the same version-chooser popup PPTW uses, anchored under the row's refresh icon.
   * The popup is a JBPopup — it layers over the Settings dialog, so we do NOT close Settings first (unlike
   * the install dialog path, which needs the workspace as its parent).
   */
  private fun triggerChangeVersionDialog(pkg: PackageTreeNode, workspaceMemberName: String?, anchor: RelativePoint?) {
    val sdk = currentSdk ?: return
    PyChangeVersionPopupLauncher.open(
      project = project,
      sdk = sdk,
      packageName = pkg.name.name,
      currentVersion = pkg.version,
      anchor = anchor,
      workspaceMember = workspaceMemberName?.let { PyWorkspaceMember(it) },
    )
  }

  /**
   * Uninstalls the package in a background coroutine via [PythonPackageManagerUI]. The
   * `workspaceMember` argument routes uv / poetry through `uv remove --package <member> <pkg>` (or
   * the equivalent) so a package declared inside a workspace member is removed from that member's
   * pyproject.toml rather than from the workspace root. The tree is refreshed via
   * [PACKAGE_MANAGEMENT_TOPIC] once the manager publishes the change; the explicit bounce is a
   * safety net for the case where the SDK is deactivated between commit and event.
   */
  private fun triggerDeletePackage(pkg: PackageTreeNode, workspaceMemberName: String?) {
    val sdk = currentSdk ?: return
    val pkgName = pkg.name.name
    val member = workspaceMemberName?.let { PyWorkspaceMember(it) }
    PyPackageCoroutine.launch(project) {
      PythonPackageManagerUI.forSdk(project, sdk).uninstallPackagesBackground(listOf(pkgName), workspaceMember = member)
      withContext(Dispatchers.EDT) {
        val current = currentSdk ?: return@withContext
        if (current === sdk) reloadCurrentSdk()
      }
    }
  }

  private fun PythonPackage.toFlatNode(): PackageTreeNode =
    PackageTreeNode(name = PyPackageName.from(name), version = version.ifEmpty { null })

  /**
   * Attaches a typed [PkgTreeRow] to the Swing node so the renderer never inspects the raw
   * `PackageTreeNode` again — icon selection is a plain exhaustive `when` on the sealed row type.
   * Workspace-member / module-dep classification is done here once per rebuild instead of per paint.
   */
  private fun PackageTreeNode.toSwingNode(
    workspaceMembers: Set<WorkspaceMemberName>,
    moduleDeps: Set<ModuleDepName>,
  ): DefaultMutableTreeNode {
    val node = DefaultMutableTreeNode(classifyPkgRow(this, workspaceMembers, moduleDeps))
    for (child in children) node.add(child.toSwingNode(workspaceMembers, moduleDeps))
    return node
  }

  companion object {
    private val PACKAGE_ICON: Icon = PyPackageIcons.Package
    private val ADD_PACKAGE_ICON: Icon = PyPackageIcons.AddPackage

    /** Python module glyph — same icon PPTW's WorkspaceMember row paints. */
    private val WORKSPACE_MEMBER_ICON: Icon = PythonIcons.Python.PythonClosed

    private val VERSION_ATTRIBUTES: SimpleTextAttributes =
      SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getInactiveTextColor())
  }

  private class PackageCellRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
      tree: JTree,
      value: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean,
    ) {
      val row = rowFromTreeComponent(value) ?: return
      icon = when (row) {
        is PkgTreeRow.WorkspaceMember -> WORKSPACE_MEMBER_ICON
        is PkgTreeRow.ModuleDep -> AllIcons.Nodes.Module
        is PkgTreeRow.Package -> PACKAGE_ICON
      }
      append(row.pkg.name.name)
      val version = row.pkg.version
      if (!version.isNullOrEmpty()) {
        append(" $version", VERSION_ATTRIBUTES)
      }
    }
  }
}

/** Which trailing package-row action icon is under the pointer / currently pressed, if any. */
private enum class PkgActionSlot { CHANGE_VERSION, DELETE }

/**
 * Cross-instance hover/press state shared between the mouse handler (writes) and the tree's overlay
 * painter (reads).
 */
private class PkgRowActionsState {
  var hoveredRow: Int = -1
    private set
  var hoveredSlot: PkgActionSlot? = null
    private set
  var pressedRow: Int = -1
    private set
  var pressedSlot: PkgActionSlot? = null
    private set

  fun setHover(row: Int, slot: PkgActionSlot?): Boolean {
    if (row == hoveredRow && slot == hoveredSlot) return false
    hoveredRow = if (slot == null) -1 else row
    hoveredSlot = slot
    return true
  }

  fun setPressed(row: Int, slot: PkgActionSlot?): Boolean {
    if (row == pressedRow && slot == pressedSlot) return false
    pressedRow = if (slot == null) -1 else row
    pressedSlot = slot
    return true
  }
}

/**
 * Translates mouse gestures on the tree into row+slot lookups against the icon geometry, updates
 * [PkgRowActionsState], repaints the affected row, adjusts the cursor, and finally routes clicks to the two
 * provided handlers. Press → release on the same slot to fire, matching standard button UX.
 */
private class PkgRowActionsMouseHandler(
  private val tree: Tree,
  private val state: PkgRowActionsState,
  private val navigator: PkgRowNavigator,
  private val isActionableRow: (Int) -> Boolean,
  private val workspaceMemberOf: (Int) -> String?,
  private val onChangeVersion: (PackageTreeNode, String?, RelativePoint) -> Unit,
  private val onDelete: (PackageTreeNode, String?) -> Unit,
) : MouseAdapter() {

  override fun mouseMoved(e: MouseEvent) = updateHover(e)
  override fun mouseDragged(e: MouseEvent) = updateHover(e)
  override fun mouseEntered(e: MouseEvent) = updateHover(e)

  override fun mouseExited(e: MouseEvent) {
    if (state.setHover(-1, null)) tree.repaint()
    tree.cursor = Cursor.getDefaultCursor()
  }

  override fun mousePressed(e: MouseEvent) {
    if (!SwingUtilities.isLeftMouseButton(e)) return
    val hit = hitTest(e) ?: return
    if (state.setPressed(hit.row, hit.slot)) tree.repaint()
    e.consume()
  }

  override fun mouseReleased(e: MouseEvent) {
    if (!SwingUtilities.isLeftMouseButton(e)) return
    val pressedRow = state.pressedRow
    val pressedSlot = state.pressedSlot
    if (state.setPressed(-1, null) && pressedRow >= 0) tree.repaint()
    val hit = hitTest(e) ?: return
    if (pressedSlot != null && hit.row == pressedRow && hit.slot == pressedSlot) {
      val pkg = navigator.packageAt(hit.row) ?: return
      val member = workspaceMemberOf(hit.row)
      when (hit.slot) {
        PkgActionSlot.CHANGE_VERSION -> {
          val bounds = tree.getRowBounds(hit.row) ?: return
          val rect = pkgRowActionRects(tree, bounds).firstOrNull { it.first == PkgActionSlot.CHANGE_VERSION }?.second
          val anchor = if (rect != null) RelativePoint(tree, Point(rect.x, rect.y + rect.height))
                       else RelativePoint(tree, e.point)
          onChangeVersion(pkg, member, anchor)
        }
        PkgActionSlot.DELETE -> onDelete(pkg, member)
      }
      e.consume()
    }
  }

  private fun updateHover(e: MouseEvent) {
    val hit = hitTest(e)
    val changed = state.setHover(hit?.row ?: -1, hit?.slot)
    if (changed) tree.repaint()
    tree.cursor = if (hit != null) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
    tree.toolTipText = hit?.slot?.tooltip
  }

  private fun hitTest(e: MouseEvent): PkgActionHit? {
    val row = tree.getClosestRowForLocation(e.x, e.y).takeIf { it >= 0 } ?: return null
    if (!isActionableRow(row)) return null
    val bounds = tree.getRowBounds(row) ?: return null
    if (e.y < bounds.y || e.y >= bounds.y + bounds.height) return null
    navigator.packageAt(row) ?: return null
    for ((slot, rect) in pkgRowActionRects(tree, bounds)) {
      if (rect.contains(e.x, e.y)) return PkgActionHit(row, slot)
    }
    return null
  }
}

private data class PkgActionHit(val row: Int, val slot: PkgActionSlot)

/** Resolved translated tooltip so the mouse handler drops the bundle-key indirection. */
private val PkgActionSlot.tooltip: @NlsContexts.Tooltip String
  get() = when (this) {
    PkgActionSlot.CHANGE_VERSION -> PyBundle.message("configurable.PyWorkspaceStructureConfigurable.packages.row.action.change.version")
    PkgActionSlot.DELETE -> PyBundle.message("configurable.PyWorkspaceStructureConfigurable.packages.row.action.delete")
  }

private object PkgRowActionMetrics {
  val ICON_SIZE: Int get() = AllIcons.General.Delete.iconWidth
  /** Gap between adjacent icons; wider than [JBUI.CurrentTheme.ActionsList.elementIconGap] so hover backgrounds don't touch. */
  val GAP: Int get() = LEFT_RIGHT_INSET.get()
  /**
   * Padding between the last icon and the viewport's right edge. Two popup insets: one keeps the icon
   * off the panel's outer border, the second gives the slot hover pill room to grow without bleeding
   * past the row band.
   */
  val TRAILING_INSET: Int get() = LEFT_RIGHT_INSET.get() * 2
  val HOVER_PAD: Int get() = BW.get()
  val HOVER_ARC: Int get() = UIUtil.DEFAULT_VGAP
}

/**
 * Icon geometry shared by the tree's overlay painter and the mouse handler so both sides read the same
 * trailing offsets — no drift between where icons paint and where clicks land.
 */
private fun pkgRowActionRects(tree: Tree, rowBounds: Rectangle): List<Pair<PkgActionSlot, Rectangle>> {
  val iconSize = PkgRowActionMetrics.ICON_SIZE
  val gap = PkgRowActionMetrics.GAP
  val trailing = PkgRowActionMetrics.TRAILING_INSET
  val viewport = tree.parent as? JViewport
  val viewportWidth = viewport?.width ?: tree.width
  val scrollX = viewport?.viewPosition?.x ?: 0
  val rightEdge = scrollX + viewportWidth - trailing
  val y = rowBounds.y + (rowBounds.height - iconSize) / 2
  val deleteX = rightEdge - iconSize
  val changeX = deleteX - gap - iconSize
  return listOf(
    PkgActionSlot.CHANGE_VERSION to Rectangle(changeX, y, iconSize, iconSize),
    PkgActionSlot.DELETE to Rectangle(deleteX, y, iconSize, iconSize),
  )
}

/**
 * Row action icons. Selected-row variants recolor with `keepBrightness=false` so the icon's mid-grey
 * strokes are pushed all the way to the target foreground; otherwise [IconUtil.colorize] preserves the
 * source brightness and the icon still reads as grey against the accent-blue selection band.
 */
private val CHANGE_VERSION_ICON: Icon = AllIcons.General.InlineRefresh
private val CHANGE_VERSION_ICON_SELECTED: Icon =
  IconUtil.colorize(AllIcons.General.InlineRefresh, UIUtil.getListSelectionForeground(true), keepGray = false, keepBrightness = false)
private val DELETE_ICON_SELECTED: Icon =
  IconUtil.colorize(AllIcons.General.Delete, UIUtil.getListSelectionForeground(true), keepGray = false, keepBrightness = false)

private fun iconForPkgSlot(slot: PkgActionSlot, selected: Boolean): Icon = when (slot) {
  PkgActionSlot.CHANGE_VERSION -> if (selected) CHANGE_VERSION_ICON_SELECTED else CHANGE_VERSION_ICON
  PkgActionSlot.DELETE -> if (selected) DELETE_ICON_SELECTED else AllIcons.General.Delete
}

private fun paintIconHoverBg(g: Graphics, x: Int, y: Int, w: Int, h: Int, pressed: Boolean) {
  val pad = JBUI.scale(PkgRowActionMetrics.HOVER_PAD)
  val arc = PkgRowActionMetrics.HOVER_ARC
  val g2 = g.create() as Graphics2D
  try {
    g2.color = if (pressed) JBUI.CurrentTheme.ActionButton.pressedBackground()
    else JBUI.CurrentTheme.ActionButton.hoverBackground()
    RectanglePainter.FILL.paint(g2, x - pad, y - pad, w + pad * 2, h + pad * 2, arc)
  }
  finally {
    g2.dispose()
  }
}
