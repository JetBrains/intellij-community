// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.FileSelectInContext
import com.intellij.ide.SelectInContext
import com.intellij.ide.plugins.UIComponentFileEditor
import com.intellij.ide.plugins.UIComponentVirtualFile
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.icons.PythonPyprojectIcons
import com.intellij.python.pyproject.model.PyProjectModelSettings
import com.intellij.python.pyproject.statistics.PyProjectTomlCollector
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.Badge
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.classIconProvider
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import org.apache.tuweni.toml.Toml
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.toml.lang.psi.TomlFileType
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.GridLayout
import java.io.IOException
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.event.HyperlinkEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.DefaultTreeSelectionModel
import javax.swing.tree.TreePath

internal fun openPyProjectPreview(project: Project, settings: PyProjectModelSettings) {
  val file = PyProjectPreviewVirtualFile(project, settings)
  FileEditorManager.getInstance(project).openFile(file, true)
}

private fun nextUniqueName(baseName: String, nameCount: MutableMap<String, Int>): String {
  val count = nameCount[baseName] ?: 0
  nameCount[baseName] = count + 1
  return if (count == 0) baseName else "$baseName@$count"
}

private class PyProjectPreviewVirtualFile(
  @JvmField val project: Project,
  @JvmField val settings: PyProjectModelSettings,
) : UIComponentVirtualFile(
  PyProjectTomlBundle.message("pyproject.preview.tab.title"),
  PythonPyprojectIcons.Model.PyProjectModule,
) {
  override fun createContent(editor: UIComponentFileEditor): Content =
    PyProjectPreviewContent(project, settings, this, editor)
}

private class PyProjectPreviewContent(
  private val project: Project,
  private val settings: PyProjectModelSettings,
  private val virtualFile: PyProjectPreviewVirtualFile,
  private val fileEditor: UIComponentFileEditor,
) : UIComponentVirtualFile.Content {

  override fun createComponent(): JComponent {
    val mainPanel = panel {
      createHeaderPanel()
      createHowItWorksPanel()
      createWorkspacePanel()
    }.apply {
      border = JBUI.Borders.empty(20, 24)
      isOpaque = true
      background = JBColor.lazy { EditorColorsManager.getInstance().globalScheme.defaultBackground }
    }
    return JBScrollPane(mainPanel)
  }

  private fun Panel.createHeaderPanel() {
    row {
      label(PyProjectTomlBundle.message("pyproject.preview.header"))
        .applyToComponent { font = JBFont.h1() }
        .gap(RightGap.SMALL)
      icon(Badge.beta)
    }
    row {
      text(PyProjectTomlBundle.message("pyproject.preview.description"))
    }
    row {
      button(PyProjectTomlBundle.message("pyproject.preview.enable")) {
        settings.usePyprojectToml = true
        settings.showConfigurationNotification = false
        PyProjectTomlCollector.previewEnableClicked()
        FileEditorManager.getInstance(project).closeFile(virtualFile)
      }.applyToComponent { putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true) }
        .customize(UnscaledGaps(right = 16))
      text(PyProjectTomlBundle.message("pyproject.preview.settings.note")).applyToComponent {
        addHyperlinkListener { e ->
          if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
            @Suppress("HardCodedStringLiteral")
            ShowSettingsUtil.getInstance().showSettingsDialog(project, "Project Structure")
          }
        }
      }
    }.topGap(TopGap.SMALL)
  }

  private fun Panel.createHowItWorksPanel() {
    row {
      label(PyProjectTomlBundle.message("pyproject.preview.how.it.works"))
        .applyToComponent { font = JBFont.h2().asBold() }
    }.customize(UnscaledGapsY(top = 32))

    // Project structure
    row {
      icon(AllIcons.Nodes.Folder).gap(RightGap.SMALL)
      label(PyProjectTomlBundle.message("pyproject.preview.project.structure"))
        .applyToComponent { font = JBFont.h3().asBold() }
    }.topGap(TopGap.SMALL)
    row {
      text("").applyToComponent {
        putClientProperty(DslComponentProperty.ICONS_PROVIDER, classIconProvider(PythonPyprojectIcons::class.java))
        text = PyProjectTomlBundle.message("pyproject.preview.how.it.works.content")
      }
    }.bottomGap(BottomGap.SMALL)
    row {
      cell(buildTreeComparisonPanel()).align(AlignX.FILL)
    }

    // Sub-project naming
    row {
      icon(PythonPyprojectIcons.Model.PyProjectModule).gap(RightGap.SMALL)
      label(PyProjectTomlBundle.message("pyproject.preview.sub.project.naming"))
        .applyToComponent { font = JBFont.h3().asBold() }
    }.topGap(TopGap.MEDIUM)
    row {
      text(PyProjectTomlBundle.message("pyproject.preview.sub.project.naming.content"))
    }

    // Dependencies
    row {
      icon(AllIcons.Nodes.Related).gap(RightGap.SMALL)
      label(PyProjectTomlBundle.message("pyproject.preview.dependencies"))
        .applyToComponent { font = JBFont.h3().asBold() }
    }.topGap(TopGap.MEDIUM)
    row {
      text(PyProjectTomlBundle.message("pyproject.preview.dependencies.content"))
    }
  }

  private fun Panel.createWorkspacePanel() {
    row {
      label(PyProjectTomlBundle.message("pyproject.preview.how.to.create"))
        .applyToComponent { font = JBFont.h2().asBold() }
    }.customize(UnscaledGapsY(top = 32))
    row {
      text(PyProjectTomlBundle.message("pyproject.preview.how.to.create.content"))
    }
    createCodeSection(
      PythonPyprojectIcons.Model.Preview.UV,
      PyProjectTomlBundle.message("pyproject.preview.uv.workspaces"),
      "https://docs.astral.sh/uv/concepts/workspaces/",
      PyProjectTomlBundle.message("pyproject.preview.uv.workspaces.content"),
      PyProjectTomlBundle.message("pyproject.preview.uv.workspaces.description"),
    )
    createCodeSection(
      PythonPyprojectIcons.Model.Preview.UV,
      PyProjectTomlBundle.message("pyproject.preview.uv.independent"),
      "https://docs.astral.sh/uv/concepts/dependencies/#path-dependencies",
      PyProjectTomlBundle.message("pyproject.preview.uv.independent.content"),
      PyProjectTomlBundle.message("pyproject.preview.uv.independent.description"),
    )
    createCodeSection(
      PythonPyprojectIcons.Model.Preview.Poetry,
      PyProjectTomlBundle.message("pyproject.preview.poetry"),
      "https://python-poetry.org/docs/dependency-specification/#path-dependencies",
      PyProjectTomlBundle.message("pyproject.preview.poetry.content"),
    )
  }

  private fun Panel.createCodeSection(icon: Icon, title: String, learnMoreUrl: String, code: String, description: String? = null) {
    row {
      icon(icon).gap(RightGap.SMALL)
      @Suppress("HardCodedStringLiteral")
      label(title).applyToComponent { font = JBFont.h3().asBold() }
      link(PyProjectTomlBundle.message("pyproject.preview.learn.more")) {
        BrowserUtil.browse(learnMoreUrl)
      }
    }.topGap(TopGap.SMALL)
    if (description != null) {
      row {
        @Suppress("HardCodedStringLiteral")
        text(description)
      }
    }
    row {
      cell(buildCodeBlock(code)).align(AlignX.FILL)
    }
  }

  private fun buildCodeBlock(code: String): JComponent {
    val document = EditorFactory.getInstance().createDocument(StringUtil.convertLineSeparators(code))
    return EditorTextField(document, project, TomlFileType, true, false).apply {
      setDisposedWith(fileEditor)
      ensureWillComputePreferredSize()
      border = roundedPanelBorder()
      addSettingsProvider { editor ->
        editor.setBorder(null)
        editor.settings.isCaretRowShown = false
        editor.settings.additionalLinesCount = 0
        editor.settings.isAdditionalPageAtBottom = false
      }
    }
  }

  private fun buildTreeComparisonPanel(): JComponent {
    val before = buildTreePanel(PyProjectTomlBundle.message("pyproject.preview.current"), includePyprojectToml = false)
    val after = buildTreePanel(PyProjectTomlBundle.message("pyproject.preview.after"), includePyprojectToml = true)
    val arrow = JLabel("\u2192").apply { foreground = UIUtil.getLabelForeground() }
    val gap = arrow.preferredSize.width + JBUI.scale(16)
    return object : JPanel(GridLayout(1, 2, gap, 0)) {
      init {
        isOpaque = false
      }

      override fun paintChildren(g: Graphics) {
        super.paintChildren(g)
        val arrowSize = arrow.preferredSize
        val x = (width - arrowSize.width) / 2
        val y = JBUI.scale(8)
        arrow.setBounds(0, 0, arrowSize.width, arrowSize.height)
        val g2 = g.create(x, y, arrowSize.width, arrowSize.height)
        arrow.paint(g2)
        g2.dispose()
      }
    }.apply {
      add(before)
      add(after)
    }
  }

  private fun buildTreePanel(title: @NlsContexts.Label String, includePyprojectToml: Boolean): JComponent = JPanel(BorderLayout()).apply {
    isOpaque = false
    border = roundedPanelBorder()
    add(JLabel(title).apply { font = JBFont.label().asBold(); border = JBUI.Borders.emptyBottom(4) }, BorderLayout.NORTH)
    add(buildProjectTree(includePyprojectToml), BorderLayout.CENTER)
  }

  private fun buildProjectTree(includePyprojectToml: Boolean): Tree {
    val projectName = project.name
    val basePath = project.basePath
                   ?: return styledTree(node(projectName, AllIcons.Nodes.Module, NodeType.MODULE))

    val markedPaths = ReadAction.computeBlocking<Map<String, MarkedEntry>, RuntimeException> {
      collectMarkedPaths(basePath, includePyprojectToml)
    }

    val root = node(projectName, AllIcons.Nodes.Module, NodeType.MODULE)
    for ((path, entry) in markedPaths.toSortedMap()) {
      if (path.isEmpty()) {
        root.userObject = TreeNodeInfo(projectName, entry.icon, entry.type, entry.moduleName)
      }
      else {
        insertPath(root, path, entry)
      }
    }

    return styledTree(root, project, basePath)
  }

  private fun collectMarkedPaths(basePath: String, includePyprojectToml: Boolean): Map<String, MarkedEntry> {
    val collector = MarkedPathCollector(basePath)
    val moduleState = collectExistingModulePaths(collector, includePyprojectToml)

    if (DumbService.isDumb(project)) return collector.build()

    val scope = GlobalSearchScope.projectScope(project)
    if (!includePyprojectToml) {
      for (file in FilenameIndex.getVirtualFilesByName(PY_PROJECT_TOML, scope)) {
        collector.add(file.path, MarkedEntry(AllIcons.FileTypes.Text, NodeType.FOLDER))
      }
    }
    else {
      collectPyprojectModulePaths(collector, scope, moduleState)
    }

    return collector.build()
  }

  private fun collectExistingModulePaths(
    collector: MarkedPathCollector,
    includePyprojectToml: Boolean,
  ): ExistingModuleState {
    val allModuleNames = mutableListOf<String>()
    val existingModuleNames = mutableMapOf<String, String>()
    var existingRootImlNames: Set<String> = emptySet()

    for (module in ModuleManager.getInstance(project).modules) {
      allModuleNames.add(module.name)
      for (contentEntry in ModuleRootManager.getInstance(module).contentEntries) {
        val contentPath = contentEntry.file?.path ?: VfsUtilCore.urlToPath(contentEntry.url)
        existingModuleNames[collector.relativize(contentPath)] = module.name
        collector.add(contentPath, MarkedEntry(AllIcons.Nodes.Module, NodeType.MODULE, module.name))

        val ideaDir = contentEntry.file?.findChild(".idea")?.takeIf { it.isDirectory }
        if (ideaDir != null) {
          existingRootImlNames = collectIdeaDirPaths(collector, contentPath, ideaDir, includePyprojectToml, existingRootImlNames)
        }

        collectSourceFolderPaths(collector, contentPath, contentEntry)
      }
    }
    return ExistingModuleState(allModuleNames, existingModuleNames, existingRootImlNames)
  }

  private fun collectIdeaDirPaths(
    collector: MarkedPathCollector,
    contentPath: String,
    ideaDir: VirtualFile,
    includePyprojectToml: Boolean,
    currentImlNames: Set<String>,
  ): Set<String> {
    if (includePyprojectToml && contentPath != collector.basePath) {
      collector.add(ideaDir.path, MarkedEntry(AllIcons.Modules.GeneratedFolder, NodeType.REMOVED_FOLDER))
      return currentImlNames
    }

    collector.add(ideaDir.path, MarkedEntry(AllIcons.Modules.GeneratedFolder, NodeType.EXCLUDED_FOLDER))
    val imlFiles = ideaDir.children.filter { !it.isDirectory && it.extension == "iml" }
    if (!includePyprojectToml) {
      imlFiles.forEach { collector.add(it.path, MarkedEntry(AllIcons.FileTypes.Xml, NodeType.EXCLUDED_FOLDER)) }
      return currentImlNames
    }
    return imlFiles.mapTo(mutableSetOf()) { it.name }
  }

  private fun collectSourceFolderPaths(
    collector: MarkedPathCollector,
    contentPath: String,
    contentEntry: ContentEntry,
  ) {
    for (sourceFolder in contentEntry.sourceFolders) {
      val file = sourceFolder.file ?: continue
      if (file.path == collector.basePath || file.path == contentPath) continue
      val icon = when (sourceFolder.rootType) {
        JavaSourceRootType.SOURCE -> AllIcons.Modules.SourceRoot
        JavaSourceRootType.TEST_SOURCE -> AllIcons.Modules.TestRoot
        JavaResourceRootType.RESOURCE -> AllIcons.Modules.ResourcesRoot
        JavaResourceRootType.TEST_RESOURCE -> AllIcons.Modules.TestResourcesRoot
        else -> AllIcons.Modules.SourceRoot
      }
      collector.add(file.path, MarkedEntry(icon, NodeType.MODULE))
    }
  }

  private fun collectPyprojectModulePaths(
    collector: MarkedPathCollector,
    scope: GlobalSearchScope,
    moduleState: ExistingModuleState,
  ) {
    val nameCount = mutableMapOf<String, Int>()
    val replacedModuleNames = mutableSetOf<String>()

    for (file in FilenameIndex.getVirtualFilesByName(PY_PROJECT_TOML, scope)) {
      val parentDir = file.parent ?: continue
      val baseName = readProjectName(file) ?: parentDir.name
      val moduleName = nextUniqueName(baseName, nameCount)
      moduleState.allModuleNames.add(moduleName)

      val existingName = moduleState.existingModuleNames[collector.relativize(parentDir.path)]
      if (existingName != null && existingName != moduleName) {
        replacedModuleNames.add(existingName)
      }

      val nodeType = if (existingName == null) NodeType.PYPROJECT_NEW_MODULE else NodeType.PYPROJECT_MODULE
      collector.add(parentDir.path, MarkedEntry(AllIcons.Nodes.Module, nodeType, moduleName))
      collector.add(file.path, MarkedEntry(AllIcons.FileTypes.Text, NodeType.MODULE))

      parentDir.findChild(".idea")?.takeIf { it.isDirectory && parentDir.path != collector.basePath }?.let {
        collector.add(it.path, MarkedEntry(AllIcons.Modules.GeneratedFolder, NodeType.REMOVED_FOLDER))
      }
    }

    for (name in moduleState.allModuleNames.sorted()) {
      val type = when {
        name in replacedModuleNames -> NodeType.REMOVED_FOLDER
        "$name.iml" !in moduleState.existingRootImlNames -> NodeType.ADDED_FILE
        else -> NodeType.EXCLUDED_FOLDER
      }
      collector.add("${collector.basePath}/.idea/$name.iml", MarkedEntry(AllIcons.FileTypes.Xml, type))
    }
  }

  private fun readProjectName(file: VirtualFile): String? = try {
    Toml.parse(String(file.contentsToByteArray(), Charsets.UTF_8))
      .getTable("project")?.getString("name")?.takeIf { it.isNotBlank() }
  }
  catch (e: IOException) {
    thisLogger().debug("Failed to parse pyproject.toml: ${file.path}", e)
    null
  }
}

private enum class NodeType {
  FOLDER, MODULE, PYPROJECT_MODULE, PYPROJECT_NEW_MODULE, EXCLUDED_FOLDER, REMOVED_FOLDER, ADDED_FILE;

  val isPyproject get() = this == PYPROJECT_MODULE || this == PYPROJECT_NEW_MODULE
}

private val pyprojectBg = JBColor(0xD6E7F8, 0x3C476B)

private data class MarkedEntry(val icon: Icon, val type: NodeType, val moduleName: String? = null)

private data class TreeNodeInfo(val name: String, val icon: Icon, val type: NodeType = NodeType.FOLDER, val moduleName: String? = null)

private fun node(name: String, icon: Icon, type: NodeType = NodeType.FOLDER): DefaultMutableTreeNode =
  DefaultMutableTreeNode(TreeNodeInfo(name, icon, type))

private fun styledTree(root: DefaultMutableTreeNode, project: Project? = null, basePath: String? = null): Tree {
  val model = DefaultTreeModel(root)
  val tree = if (project != null && basePath != null) NavigatableTree(model, project, basePath)
  else HighlightedTree(model)
  return tree.apply {
    isRootVisible = true
    showsRootHandles = true
    cellRenderer = ProjectTreeCellRenderer()
    selectionModel = object : DefaultTreeSelectionModel() {
      override fun setSelectionPaths(pPaths: Array<out TreePath?>?) {}
      override fun addSelectionPaths(paths: Array<out TreePath?>?) {}
    }
    expandRow(0)
    var i = 1
    while (i < rowCount) {
      val path = getPathForRow(i)
      val treeNode = path?.lastPathComponent as? DefaultMutableTreeNode
      val info = treeNode?.userObject as? TreeNodeInfo
      if (info != null && info.type == NodeType.FOLDER) {
        expandRow(i)
      }
      i++
    }
  }
}

private open class HighlightedTree(model: DefaultTreeModel) : Tree(model) {
  override fun getPathBackground(path: TreePath, row: Int): Color? {
    val node = path.lastPathComponent as? DefaultMutableTreeNode
    val info = node?.userObject as? TreeNodeInfo
    return if (info?.type?.isPyproject == true) pyprojectBg
    else super.getPathBackground(path, row)
  }
}

private class NavigatableTree(
  model: DefaultTreeModel,
  private val project: Project,
  private val basePath: String,
) : HighlightedTree(model), UiDataProvider {
  private var contextNode: DefaultMutableTreeNode? = null

  init {
    addMouseListener(object : PopupHandler() {
      override fun invokePopup(comp: Component, x: Int, y: Int) {
        val path = getClosestPathForLocation(x, y) ?: return
        contextNode = path.lastPathComponent as? DefaultMutableTreeNode
        val group = DefaultActionGroup(SelectInProjectViewAction())
        ActionManager.getInstance().createActionPopupMenu("PyProjectPreviewTree", group).component.show(comp, x, y)
      }
    })
  }

  override fun uiDataSnapshot(sink: DataSink) {
    val node = contextNode ?: return
    sink[CommonDataKeys.PROJECT] = project
    val virtualFile = resolveVirtualFile(node, basePath) ?: return
    sink[CommonDataKeys.VIRTUAL_FILE] = virtualFile
    sink[SelectInContext.DATA_KEY] = FileSelectInContext(project, virtualFile, null)
  }
}

private fun resolveVirtualFile(node: DefaultMutableTreeNode, basePath: String): VirtualFile? {
  val pathParts = generateSequence(node) { it.parent as? DefaultMutableTreeNode }
    .takeWhile { it.parent != null }
    .map { (it.userObject as? TreeNodeInfo)?.name }
    .toList()
  if (pathParts.any { it == null }) return null
  val fullPath = if (pathParts.isEmpty()) basePath
  else "$basePath/${pathParts.asReversed().joinToString("/")}"
  return LocalFileSystem.getInstance().findFileByPath(fullPath)
}

private class SelectInProjectViewAction : AnAction(
  PyProjectTomlBundle.messagePointer("pyproject.preview.select.in.project.view"),
  AllIcons.General.Locate,
) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    ProjectView.getInstance(project).select(null, virtualFile, true)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.getData(CommonDataKeys.VIRTUAL_FILE) != null
  }
}

private class MarkedPathCollector(val basePath: String) {
  private val paths = linkedMapOf<String, MarkedEntry>()

  fun add(absolutePath: String, entry: MarkedEntry) {
    if (!absolutePath.startsWith(basePath)) return
    paths[relativize(absolutePath)] = entry
  }

  fun relativize(absolutePath: String): String =
    absolutePath.removePrefix(basePath).removePrefix("/")

  fun build(): Map<String, MarkedEntry> = paths
}

private fun insertPath(root: DefaultMutableTreeNode, path: String, entry: MarkedEntry) {
  val parts = path.split("/")
  var current = root
  for ((i, part) in parts.withIndex()) {
    val isLast = (i == parts.lastIndex)
    val existing = findChild(current, part)
    val child = if (existing == null) {
      (if (isLast) {
        DefaultMutableTreeNode(TreeNodeInfo(part, entry.icon, entry.type, entry.moduleName))
      }
      else node(part, AllIcons.Nodes.Folder)).also { current.add(it) }
    }
    else {
      if (isLast) existing.userObject = TreeNodeInfo(part, entry.icon, entry.type, entry.moduleName)
      existing
    }
    current = child
  }
}

private fun findChild(parent: DefaultMutableTreeNode, name: String): DefaultMutableTreeNode? =
  parent.children().asSequence()
    .filterIsInstance<DefaultMutableTreeNode>()
    .firstOrNull { (it.userObject as? TreeNodeInfo)?.name == name }

private class ProjectTreeCellRenderer : ColoredTreeCellRenderer() {
  private val addedFileAttributes get() = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getLabelSuccessForeground())
  private val removedFolderAttributes get() = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, NamedColorUtil.getErrorForeground())

  override fun customizeCellRenderer(
    tree: JTree,
    value: Any?,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean,
  ) {
    val node = value as? DefaultMutableTreeNode ?: return
    val info = node.userObject as? TreeNodeInfo ?: return

    val isPyproject = info.type.isPyproject
    icon = if (isPyproject) PythonPyprojectIcons.Model.PyProjectModule else info.icon

    @Suppress("HardCodedStringLiteral")
    when (info.type) {
      NodeType.REMOVED_FOLDER -> append(info.name, removedFolderAttributes)
      NodeType.ADDED_FILE -> append(info.name, addedFileAttributes)
      NodeType.EXCLUDED_FOLDER -> append(info.name, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      else -> {
        val nameAttr = if (info.name == info.moduleName) SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
        else SimpleTextAttributes.REGULAR_ATTRIBUTES
        append(info.name, nameAttr)
        if (info.moduleName != null && info.name != info.moduleName) {
          append(" [${info.moduleName}]", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        }
      }
    }
  }
}

private class ExistingModuleState(
  val allModuleNames: MutableList<String>,
  val existingModuleNames: Map<String, String>,
  val existingRootImlNames: Set<String>,
)

private fun roundedPanelBorder() = BorderFactory.createCompoundBorder(
  RoundedLineBorder(JBColor.border(), JBUI.scale(8)),
  JBUI.Borders.empty(8),
)
