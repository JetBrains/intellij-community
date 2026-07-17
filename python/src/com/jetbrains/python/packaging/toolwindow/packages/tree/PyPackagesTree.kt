// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages.tree

import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.ClientProperty
import com.intellij.ui.hover.TreeHoverListener
import com.intellij.ui.render.RenderingHelper
import com.intellij.openapi.application.EDT
import com.intellij.python.processOutput.common.ProcessOutputQuery
import com.intellij.python.processOutput.common.sendProcessOutputQuery
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle
import com.jetbrains.python.TraceContext
import com.jetbrains.python.packaging.management.PyPackageScope
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.packaging.pyRequirementVersionSpec
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowPanel
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstallablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.model.LoadingNode
import com.jetbrains.python.packaging.toolwindow.model.RequirementPackage
import com.jetbrains.python.packaging.toolwindow.model.WorkspaceMember
import com.jetbrains.python.packaging.toolwindow.model.DependencyGroupNode
import com.jetbrains.python.packaging.toolwindow.model.UndeclaredPackagesGroup
import com.jetbrains.python.packaging.toolwindow.packages.tree.renderers.PyPackageTreeCellRenderer
import com.jetbrains.python.packaging.toolwindow.packages.tree.renderers.asInstalledPackageOrNull
import com.jetbrains.python.packaging.toolwindow.ui.PyInstallPackageDialog
import com.jetbrains.python.packaging.toolwindow.ui.showChangeVersionPopup
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.isReadOnly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.AWTEvent
import java.awt.datatransfer.StringSelection
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.MouseEvent
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionListener
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel
import javax.swing.tree.TreeCellRenderer
import com.intellij.ui.treeStructure.Tree as IntelliJTree

@ApiStatus.Internal
internal class PyPackagesTree(
  val project: Project,
  private val controller: PyPackagingToolWindowPanel,
) : IntelliJTree(), UiDataProvider, CopyProvider {

  companion object {
    internal val TREE_KEY: Key<PyPackagesTree> = Key.create("PyPackageToolwindow.Tree")
    private const val LOAD_MORE_PAGE: Int = 50
  }

  private val packagingService = project.service<PyPackagingToolWindowService>()
  private var treeListener: PyPackagesTreeListener? = null

  private val rootNode = DefaultMutableTreeNode()
  private val treeModel = DefaultTreeModel(rootNode)

  @set:RequiresEdt
  var items: List<DisplayablePackage> = emptyList()
    set(value) {
      field = value
      sortedAllMatches = null
      updateTreeModel()
      treeListener?.onTreeStructureChanged()
    }

  /**
   * Service-side seeded sorted match list (cross-repo merge + global priority sort). Tree's
   * [loadMore] paginates this list visually so the on-scroll order matches the install dialog.
   */
  @RequiresEdt
  fun primeSortedMatches(sortedAll: List<DisplayablePackage>) {
    sortedAllMatches = sortedAll
  }

  /** How many more packages the repository can still produce for the current query. */
  @get:RequiresEdt
  @set:RequiresEdt
  var pendingMore: Int = 0

  /**
   * Full pre-sorted match list for the active query, seeded by [primeSortedMatches] from the
   * service (which sorts cross-repo + filters installed once). [loadMore] reveals chunks of
   * this list visually without re-fetching or re-sorting, so the displayed order is identical
   * to what the install dialog shows.
   *
   * Reset whenever [items] is assigned externally (a new search starts).
   */
  private var sortedAllMatches: List<DisplayablePackage>? = null

  internal val isReadOnly
    get() = packagingService.currentSdk?.isReadOnly != false

  /**
   * `true` once [PyPackageTreeCellRenderer] has been installed during construction. Used to
   * pin our renderer for the rest of the tree's lifetime — see [setCellRenderer] / [updateUI].
   */
  private var initialized = false

  /**
   * Intentionally ignores external `setCellRenderer` calls after construction.
   *
   * Several IntelliJ tree decorators (e.g. `LazyRendererTreeUI`, the LaF "TreeCellRenderer"
   * default) call `tree.setCellRenderer(...)` during normal repainting and on LaF reload to
   * replace the renderer with the platform default. The packaging tree relies on its custom
   * [PyPackageTreeCellRenderer] for hover icons, inline change-version buttons and tooltip
   * geometry, so we silently swallow late overrides instead of letting the tree fall back to a
   * plain label renderer (which would visually break the action icons).
   */
  override fun setCellRenderer(renderer: TreeCellRenderer?) {
    if (initialized) return
    super.setCellRenderer(renderer)
  }

  /**
   * `updateUI()` is called by Swing on LaF change to recreate the tree UI. The base implementation
   * resets the cell renderer to the LaF default, which would wipe out [PyPackageTreeCellRenderer].
   *
   * The dance below:
   *   1. Drops the `initialized` flag so the LaF default renderer set inside `super.updateUI()`
   *      is allowed through [setCellRenderer] (otherwise the LaF would render with a stale, now-disposed renderer).
   *   2. Restores the flag.
   *   3. If the tree had already been initialized once, re-installs a fresh
   *      [PyPackageTreeCellRenderer] so the user keeps seeing our custom row layout after the
   *      theme change.
   */
  override fun updateUI() {
    val wasInitialized = initialized
    initialized = false
    super.updateUI()
    initialized = wasInitialized
    if (wasInitialized) {
      super.setCellRenderer(PyPackageTreeCellRenderer(this))
    }
  }

  private var suppressClearOnFocusLoss: Boolean = false

  init {
    putClientProperty(TREE_KEY, this)
    ClientProperty.put(this, RenderingHelper.SHRINK_LONG_RENDERER, false)
    model = treeModel
    alignmentX = LEFT_ALIGNMENT
    alignmentY = TOP_ALIGNMENT
    isRootVisible = false
    showsRootHandles = true
    selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    super.setCellRenderer(PyPackageTreeCellRenderer(this))
    initialized = true
    transferHandler = null
    TreeHoverListener.DEFAULT.addTo(this)
    javax.swing.ToolTipManager.sharedInstance().registerComponent(this)
    enableEvents(AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK)
    addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        (ui as BasicTreeUI).let { it.leftChildIndent = it.leftChildIndent }
        repaint()
      }
    })
    initializeUI()
  }

  override fun getToolTipText(event: MouseEvent): String? {
    val row = getClosestRowForLocation(event.x, event.y).takeIf { it >= 0 } ?: return null
    val rowBounds = getRowBounds(row) ?: return null
    if (event.y < rowBounds.y || event.y >= rowBounds.y + rowBounds.height) return null
    val pkg = packageAtRow(row).asInstalledPackageOrNull() ?: return null
    val node = getPathForRow(row).lastPathComponent as DefaultMutableTreeNode
    val renderer = cellRenderer.getTreeCellRendererComponent(
      this, node, isPathSelected(getPathForRow(row)), isExpanded(row), model.isLeaf(node), row, hasFocus()
    ) as PyPackageTreeCellRenderer
    renderer.setSize(rowBounds.width, rowBounds.height)
    val relativeX = event.x - rowBounds.x

    val changeIconX = renderer.inlineChangeVersionIconX
    val changeIcon = renderer.inlineChangeVersionIcon
    if (changeIconX > 0 && changeIcon != null && relativeX in changeIconX..(changeIconX + changeIcon.iconWidth)) {
      val next = pkg.nextVersion?.presentableText
      return if (next != null && pkg.canBeUpdated) {
        PyBundle.message("python.toolwindow.packages.tooltip.update.to", next)
      }
      else {
        PyBundle.message("python.toolwindow.packages.tooltip.change.version")
      }
    }

    val trailingIconX = renderer.trailingIconX
    val trailingIcon = renderer.trailingIcon
    if (trailingIconX > 0 && trailingIcon != null && relativeX in trailingIconX..(trailingIconX + trailingIcon.iconWidth)) {
      return PyBundle.message("python.toolwindow.packages.tooltip.uninstall")
    }

    return null
  }

  private val hoverHandler = PyPackagesTreeHoverHandler(this)
  internal val linkHoveredRow: Int get() = hoverHandler.linkHoveredRow
  internal val iconHoveredRow: Int get() = hoverHandler.iconHoveredRow
  internal val changeIconHoveredRow: Int get() = hoverHandler.changeIconHoveredRow

  override fun processMouseMotionEvent(e: MouseEvent) {
    super.processMouseMotionEvent(e)
    if (e.id == MouseEvent.MOUSE_MOVED) {
      hoverHandler.handleMouseMoved(e)
    }
  }

  override fun processMouseEvent(e: MouseEvent) {
    if (e.id == MouseEvent.MOUSE_EXITED) {
      hoverHandler.clearLinkHover()
    }
    if (e.id == MouseEvent.MOUSE_PRESSED && e.button == MouseEvent.BUTTON1 && !e.isPopupTrigger) {
      if (handleLinkClick(e)) {
        return
      }
    }
    super.processMouseEvent(e)
  }

  private fun initializeUI() {
    setupTreeInteractions()
  }

  private fun updateTreeModel() {
    rootNode.removeAllChildren()
    items.forEach { pkg -> rootNode.add(createNodeRecursively(pkg)) }
    treeModel.reload()
  }

  private fun createNodeRecursively(pkg: DisplayablePackage): DefaultMutableTreeNode {
    val node = DefaultMutableTreeNode(pkg)
    pkg.getRequirements().forEach { requirement ->
      node.add(createNodeRecursively(requirement))
    }
    return node
  }

  private fun setupTreeInteractions() {
    setupTreeEventListeners()
  }

  private fun setupTreeEventListeners() {
    addTreeSelectionListener(createPackageSelectionListener())
    addTreeExpansionListener(createTreeExpansionListener())
    addFocusListener(createFocusListener())
    val docPreview = PyPackagesTreeDocPreviewSupport(this, project)
    addMouseMotionListener(docPreview)
    addMouseListener(docPreview)
  }

  internal fun packageAtRow(row: Int): DisplayablePackage? {
    val path = getPathForRow(row) ?: return null
    return (path.lastPathComponent as DefaultMutableTreeNode).userObject as DisplayablePackage
  }

  private fun createPackageSelectionListener() = TreeSelectionListener { event ->
    val path = event.path ?: return@TreeSelectionListener
    if (!hasFocus()) return@TreeSelectionListener
    val pkg = (path.lastPathComponent as DefaultMutableTreeNode).userObject as DisplayablePackage
    handlePackageSelection(pkg)
  }

  private fun handlePackageSelection(pkg: DisplayablePackage) {
    when (pkg) {
      is InstalledPackage -> controller.packageSelected(pkg)
      is InstallablePackage -> controller.packageSelected(pkg)
      is RequirementPackage -> controller.packageSelected(pkg)
      is WorkspaceMember -> controller.packageSelected(pkg)
      is LoadingNode, is DependencyGroupNode, is UndeclaredPackagesGroup -> {}
    }
  }

  private fun createTreeExpansionListener() = object : TreeExpansionListener {
    override fun treeExpanded(event: TreeExpansionEvent) {
      treeListener?.onTreeStructureChanged()
    }

    override fun treeCollapsed(event: TreeExpansionEvent) {
      treeListener?.onTreeStructureChanged()
    }
  }

  private fun createFocusListener(): FocusListener = object : FocusAdapter() {
    override fun focusGained(e: FocusEvent) {
      val pkg = selectedItem() ?: return
      handlePackageSelection(pkg)
    }

    override fun focusLost(e: FocusEvent) {
      if (suppressClearOnFocusLoss || e.isTemporary) return
      controller.setEmpty()
    }
  }

  private fun handleLinkClick(e: MouseEvent): Boolean {
    val row = getClosestRowForLocation(e.x, e.y)
    if (row == -1) return false

    val pkg = packageAtRow(row) ?: return false

    if (isReadOnly) return false

    val renderer = cellRenderer.getTreeCellRendererComponent(
      this, (getPathForRow(row).lastPathComponent as DefaultMutableTreeNode), true, false, true, row, false
    ) as PyPackageTreeCellRenderer

    val cellBounds = getRowBounds(row) ?: return false
    val relativeX = e.x - cellBounds.x

    if (pkg is InstalledPackage) {
      val changeIconX = renderer.inlineChangeVersionIconX
      val changeIcon = renderer.inlineChangeVersionIcon
      if (changeIconX > 0 && changeIcon != null && relativeX in changeIconX..(changeIconX + changeIcon.iconWidth)) {
        setSelectionRow(row)
        handlePackageSelection(pkg)
        changeVersionInline(pkg, com.intellij.ui.awt.RelativePoint(this, java.awt.Point(e.x, e.y)))
        return true
      }
    }

    val trailingIconX = renderer.trailingIconX
    val trailingIcon = renderer.trailingIcon
    if (trailingIconX > 0 && trailingIcon != null && relativeX in trailingIconX..(trailingIconX + trailingIcon.iconWidth)) {
      val handled = when (pkg) {
        is InstalledPackage -> { setSelectionRow(row); handlePackageSelection(pkg); deletePackageInline(pkg); true }
        is InstallablePackage -> { setSelectionRow(row); handlePackageSelection(pkg); showInstallDialog(pkg); true }
        is RequirementPackage,
        is UndeclaredPackagesGroup,
        is DependencyGroupNode,
        is WorkspaceMember,
        is LoadingNode -> false
      }
      if (handled) return true
    }

    val linkStartX = renderer.linkStartX
    val linkEndX = renderer.linkEndX
    if (linkStartX !in 1..<linkEndX) return false
    if (relativeX !in linkStartX..linkEndX) return false

    setSelectionRow(row)
    handlePackageSelection(pkg)

    return when (pkg) {
      is InstallablePackage -> { installPackage(pkg); true }
      is InstalledPackage,
      is RequirementPackage,
      is WorkspaceMember,
      is LoadingNode,
      is DependencyGroupNode,
      is UndeclaredPackagesGroup -> false
    }
  }

  private fun showInstallDialog(pkg: InstallablePackage) {
    PyInstallPackageDialog(project).show(pkg.name)
  }

  private fun updatePackageToLatest(pkg: InstalledPackage) {
    val versionString = pkg.nextVersion?.presentableText ?: return
    val requirement = pyRequirement(pkg.name, pyRequirementVersionSpec(versionString))
    val spec = pkg.repository?.findPackageSpecification(requirement) ?: return
    val installRequest = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(listOf(spec))
    PyPackageCoroutine.launch(project, Dispatchers.IO) {
      packagingService.installPackage(installRequest, workspaceMember = pkg.workspaceMember, dependencyGroup = pkg.dependencyGroup)
    }
  }

  private fun installPackage(pkg: InstallablePackage) {
    val spec = pkg.repository.findPackageSpecification(pyRequirement(pkg.name, null)) ?: return
    val installRequest = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(listOf(spec))
    PyPackageCoroutine.launch(project, Dispatchers.IO) {
      packagingService.installPackage(installRequest)
    }
  }

  private fun deletePackageInline(pkg: InstalledPackage) {
    PyPackageCoroutine.launch(project, Dispatchers.IO) {
      packagingService.deletePackage(pkg)
    }
  }

  private fun changeVersionInline(pkg: InstalledPackage, anchor: com.intellij.ui.awt.RelativePoint? = null) {
    PyPackageCoroutine.launch(project, Dispatchers.Default) {
      val trace = TraceContext(PyBundle.message("python.toolwindow.packages.tooltip.change.version"), null)
      val details = withContext(trace) { packagingService.detailsForPackage(pkg) }
      if (details == null) {
        sendProcessOutputQuery(ProcessOutputQuery.OpenToolWindowByTraceUuid(trace.uuid.toString()))
        return@launch
      }
      withContext(Dispatchers.EDT) {
        showChangeVersionPopup(
          project = project,
          details = details,
          scope = PyPackageScope(pkg.workspaceMember, pkg.dependencyGroup),
          anchor = anchor,
          highlightVersion = pkg.nextVersion?.presentableText,
          currentVersion = pkg.instance.version,
        )
      }
    }
  }

  /**
   * Reveals the next [LOAD_MORE_PAGE] entries from the pre-sorted match list seeded by
   * [primeSortedMatches]. No network round-trip, no re-sort — items appear in the exact order
   * the install dialog shows them.
   */
  @RequiresEdt
  fun loadMore() {
    if (pendingMore <= 0) return
    val sorted = sortedAllMatches ?: return
    val from = items.size
    val to = minOf(from + LOAD_MORE_PAGE, sorted.size)
    if (from >= to) return
    setItemsKeepingCache(sorted.subList(0, to))
    pendingMore = (sorted.size - to).coerceAtLeast(0)
  }

  private fun setItemsKeepingCache(value: List<DisplayablePackage>) {
    val preserved = sortedAllMatches
    items = value
    sortedAllMatches = preserved
  }

  fun setTreeListener(listener: PyPackagesTreeListener) {
    treeListener = listener
  }

  fun selectPackage(pkg: DisplayablePackage) {
    val index = items.indexOf(pkg)
    if (index != -1) {
      setSelectionRow(index)
    }
  }

  fun expandAll() {
    for (i in 0 until rowCount) expandRow(i)
  }

  fun selectedItems(): List<DisplayablePackage> =
    selectionRows?.toList()?.mapNotNull { row -> packageAtRow(row) } ?: emptyList()

  private fun selectedItem(): DisplayablePackage? = selectedItems().firstOrNull()

  override fun uiDataSnapshot(sink: DataSink) {
    sink[PlatformDataKeys.COPY_PROVIDER] = this
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun performCopy(dataContext: DataContext) {
    getTextForCopy()?.let { CopyPasteManager.getInstance().setContents(StringSelection(it)) }
  }

  override fun isCopyEnabled(dataContext: DataContext): Boolean = getTextForCopy() != null

  override fun isCopyVisible(dataContext: DataContext): Boolean = true

  private fun getTextForCopy(): String? = when (val pkg = selectedItem()) {
    is InstalledPackage, is InstallablePackage, is RequirementPackage, is WorkspaceMember -> pkg.name
    is LoadingNode, is DependencyGroupNode, is UndeclaredPackagesGroup, null -> null
  }
}

internal fun interface PyPackagesTreeListener {
  /** Called on EDT when the visible tree row layout changes (items set, expanded, collapsed). */
  @RequiresEdt
  fun onTreeStructureChanged()
}
