// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration.interpreter

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil.BW
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.asItem
import com.intellij.python.sdk.common.PyInterpreterItem
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.LcrRow
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.render.CompositeRenderer
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.launchOnShow
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.collectAddInterpreterActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * The interpreter combo of a module, with the button that adds an interpreter. The Workspace page, the run
 * configurations, the Python facet, and the framework support of a Java IDE use it.
 *
 * The rows come from [PythonInterpreterRegistry]: first the interpreters of the workspace root of the module, then, on
 * request, the interpreters of the other Python projects, see [interpreterComboRows]. A module that is not a Python
 * project lists the interpreters of all Python projects.
 *
 * The combo reads the rows from the [EvoPyProjectModel] snapshot in the background while it is shown. The read must not
 * block the EDT, see PY-93821. The button runs the add flow, which sets the new interpreter on the module at once, see
 * [collectAddInterpreterActions].
 *
 * @param module the module whose interpreter the combo picks, or `null` when there is none yet.
 * @param navigateToAllInterpreters opens the "All Interpreters" page from the combo. Without it the combo has no row for
 *   that page.
 * @param offerModuleInterpreter adds the row that stands for the interpreter of the module, whichever it is at run time,
 *   see [isModuleInterpreterSelected].
 * @param offerAdd shows the button that adds an interpreter.
 */
@ApiStatus.Internal
class PyInterpreterComboBox @JvmOverloads constructor(
  private val project: Project,
  private val module: () -> Module?,
  private val navigateToAllInterpreters: ((JComponent) -> Unit)? = null,
  private val offerModuleInterpreter: Boolean = false,
  private val offerAdd: Boolean = true,
) {
  private inner class Model : CollectionComboBoxModel<SdkComboItem<PythonInterpreter>>() {
    // The combo is not editable and holds only its rows, so a selection that is not null is a row.
    override fun setSelectedItem(item: Any?) {
      presenter.onSelectionChanged(item as? SdkComboItem<*>)
    }

    fun setSelectedItemFromPresenter(item: SdkComboItem<*>?) {
      super.setSelectedItem(item)
    }
  }

  private val model = Model()

  private val presenter = SdkComboPresenter(
    hidePopup = { combo.hidePopup() },
    navToAllInterpreters = { ApplicationManager.getApplication().invokeLater { navigateToAllInterpreters?.invoke(combo) } },
    toggleOtherProjects = { toggleOtherProjects() },
    propagate = { model.setSelectedItemFromPresenter(it) },
  )

  /** What the last read gave. Empty until the first read ends. */
  private var loaded: Loaded = Loaded(InterpreterComboSource(null, emptyList(), emptyList()), emptyMap(), null)

  /** Whether the combo also lists the interpreters of the other Python projects. */
  private var showOtherProjects: Boolean = false

  /** Each new value asks for a new read, and says which row to select after it. */
  private val readRequests = MutableStateFlow<ReadRequest>(ReadRequest.Keep())

  private val combo: ComboBox<SdkComboItem<PythonInterpreter>> = ComboBox(model).apply {
    isSwingPopup = false
    // The width of the empty combo, so a long path of an interpreter does not widen the page beyond its dialog.
    preferredSize = preferredSize
    renderer = ComboRenderer(listCellRenderer<SdkComboItem<PythonInterpreter>> {
      // The popup passes `null` for the editor position without a selection, although `value` is not nullable. A module
      // without an interpreter has no selection, so the field says so in grey. No row of the list stands for it.
      val v: SdkComboItem<PythonInterpreter>? = value
      if (v == null) {
        text(PyBundle.message("python.sdk.there.is.no.interpreter")) { foreground = greyForeground }
        return@listCellRenderer
      }
      // The popup asks for the separator of a value with the index -1, so the separator follows the value.
      if (v == SdkComboItem.ShowAll) separator {}
      when (v) {
        // The row says only that, in grey, with the icon of the interpreter. Its own row below names it.
        SdkComboItem.ModuleInterpreter -> {
          loaded.source.moduleInterpreter?.let { loaded.items[it] }?.let { icon(it.icon.icon()) }
          text(PyBundle.message("python.sdk.rendering.current")) { foreground = greyForeground }
        }
        is SdkComboItem.Existing -> renderInterpreter(v.interpreter)
        is SdkComboItem.GroupHeader -> text(v.title)
        SdkComboItem.ToggleOtherProjects -> {
          val key = if (showOtherProjects) "configurable.PyWorkspaceStructureConfigurable.sdk.hide.non.owned"
          else "configurable.PyWorkspaceStructureConfigurable.sdk.show.non.owned"
          text(PyBundle.message(key)) {
            foreground = greyForeground
            font = JBFont.small()
            align = LcrInitParams.Align.RIGHT
          }
        }
        SdkComboItem.ShowAll -> text(PyBundle.message("configurable.PyWorkspaceStructureConfigurable.sdk.show.all"))
      }
    })
  }

  private val addInterpreterAction: DumbAwareAction = object : DumbAwareAction(
    PyBundle.messagePointer("configurable.PyWorkspaceStructureConfigurable.sdk.add"),
    Presentation.NULL_STRING,
    AllIcons.General.Add,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      val target = loaded.addTarget ?: return
      val group = DefaultActionGroup(collectAddInterpreterActions(target) { added -> select(added) })
      val popup = JBPopupFactory.getInstance().createActionGroupPopup(
        null, group, e.dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false,
      )
      val source = e.inputEvent?.component
      if (source != null) popup.showUnderneathOf(source) else popup.showInBestPositionFor(e.dataContext)
    }

    // The new interpreter belongs to the workspace root, which is known only after the first read.
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = loaded.addTarget != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  /** The combo and the add button. */
  val component: JComponent = JPanel(GridBagLayout()).apply {
    isOpaque = false
    combo.minimumSize = Dimension(0, combo.preferredSize.height)
    val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, DefaultActionGroup(addInterpreterAction), true)
    toolbar.targetComponent = combo
    toolbar.component.apply {
      isOpaque = false
      border = JBUI.Borders.empty()
    }
    val c = GridBagConstraints()
    c.fill = GridBagConstraints.HORIZONTAL
    c.gridx = 0; c.weightx = 1.0
    add(combo, c)
    if (offerAdd) {
      c.gridx = 1; c.weightx = 0.0; c.insets = JBUI.insetsLeft(BW.get())
      add(toolbar.component, c)
    }
  }

  /** The component that a label names. */
  val labeledComponent: JComponent get() = combo

  /**
   * The selected interpreter, or `null` for no interpreter. For the "module interpreter" row, it is the interpreter that
   * the module has at the last read.
   */
  val selectedInterpreter: PythonInterpreter?
    get() = when (val item = model.selected) {
      is SdkComboItem.Existing -> item.interpreter
      SdkComboItem.ModuleInterpreter -> loaded.source.moduleInterpreter
      is SdkComboItem.GroupHeader, SdkComboItem.ToggleOtherProjects, SdkComboItem.ShowAll, null -> null
    }

  /** Whether the "module interpreter" row is selected, see `offerModuleInterpreter`. */
  val isModuleInterpreterSelected: Boolean
    get() = model.selected == SdkComboItem.ModuleInterpreter

  /** The interpreter of the selected row. It is `null` for the "module interpreter" row and for the other rows. */
  private val selectedRowInterpreter: PythonInterpreter?
    get() = when (val item = model.selected) {
      is SdkComboItem.Existing -> item.interpreter
      SdkComboItem.ModuleInterpreter, is SdkComboItem.GroupHeader, SdkComboItem.ToggleOtherProjects,
      SdkComboItem.ShowAll, null,
        -> null
    }

  init {
    combo.launchOnShow("PyInterpreterComboBox.read") {
      readRequests.collectLatest { request ->
        val keepModuleRow = isModuleInterpreterSelected
        val current = selectedRowInterpreter
        val include = when (request) {
          is ReadRequest.Pick -> request.include
          is ReadRequest.Keep, is ReadRequest.ModuleRow -> null
        }
        val kept = listOfNotNull(current, include).distinct()
        val module = module()
        val read = withContext(Dispatchers.Default) { read(module, kept) }
        loaded = read
        val target = when (request) {
          is ReadRequest.Pick -> request.select(read.source.moduleInterpreter)
          is ReadRequest.ModuleRow -> null
          is ReadRequest.Keep -> current.takeUnless { keepModuleRow }
        }
        if (opensOtherProjects(read.source, target)) showOtherProjects = true
        publishRows(kept)
        selectRow(target)
      }
    }
  }

  /** Calls [listener] when the selection changes. */
  fun addSelectionListener(listener: () -> Unit) {
    combo.addActionListener { listener() }
  }

  /**
   * Reads the rows again while the combo is shown, and selects the interpreter that [select] picks. [select] gets the
   * interpreter that the module has. Without a row for the pick, the combo selects the module interpreter.
   */
  fun reload(select: (moduleInterpreter: PythonInterpreter?) -> PythonInterpreter?) {
    readRequests.value = ReadRequest.Pick(null, select)
  }

  /** Reads the rows again, and selects [interpreter]. It has a row also when no Python project lists it. */
  fun select(interpreter: PythonInterpreter?) {
    readRequests.value = ReadRequest.Pick(interpreter) { interpreter }
  }

  /** Reads the rows again, and selects the "module interpreter" row, see `offerModuleInterpreter`. */
  fun selectModuleInterpreter() {
    readRequests.value = ReadRequest.ModuleRow()
  }

  /** Reads the rows again, for example after the module changed, and keeps the selected row. */
  fun reloadKeepingSelection() {
    readRequests.value = ReadRequest.Keep()
  }

  private fun LcrRow<SdkComboItem<PythonInterpreter>>.renderInterpreter(interpreter: PythonInterpreter) {
    val item = loaded.items[interpreter] ?: return
    icon(item.icon.icon())
    text(item.name)
    item.suffix?.let { text(it) }
    text(item.description) { foreground = greyForeground }
  }

  /** Reads what the combo can list from one snapshot. It also reads the presentation of [extra]. */
  private suspend fun read(module: Module?, extra: List<PythonInterpreter>): Loaded {
    val snapshot = EvoPyProjectModel.getInstance(project).snapshot()
    val evoPyProject = module?.let { snapshot.forModule(it) }
    val rootPyProject = evoPyProject?.let { snapshot.workspaceOf(it)?.pyProject ?: it.pyProject }
    val registry = PythonInterpreterRegistry.getInstance(project)
    val own = rootPyProject?.let { registry.interpreters(it) }.orEmpty()
    val all = snapshot.evoPyProjects.toList().flatMap { registry.interpreters(it.pyProject) }.distinct()
    val moduleInterpreter = evoPyProject?.interpreter
    val every = (own + all + listOfNotNull(moduleInterpreter) + extra).distinct()
    val items = withContext(Dispatchers.IO) { every.associateWith { it.asItem() } }
    val owners = every.associateWith { snapshot.ownerNameOf(it) }
    // A module without a Python project goes in without one, and the add flow gives it one.
    val addTarget = rootPyProject?.let { ModuleOrProject.ModuleAndProject(it) } ?: module?.let { ModuleOrProject.ModuleAndProject(it) }
    // Another workspace, by its root: the members of one tool workspace, such as a uv workspace, share its interpreters.
    val hasOtherProjects = snapshot.evoPyProjects.any { (snapshot.workspaceOf(it)?.pyProject ?: it.pyProject) != rootPyProject }
    return Loaded(InterpreterComboSource(moduleInterpreter, own, all, owners, hasOtherProjects), items, addTarget)
  }

  private fun publishRows(kept: List<PythonInterpreter>) {
    val rows = interpreterComboRows(loaded.source, showOtherProjects, kept, offerShowAll = navigateToAllInterpreters != null,
                                    offerModuleInterpreter = offerModuleInterpreter)
    model.removeAll()
    model.add(rows)
  }

  /**
   * Selects the row of [interpreter]. Else it selects the "module interpreter" row or the row of the interpreter of the
   * module, the first that the combo holds. Else nothing is selected, and the field says that there is no interpreter.
   */
  private fun selectRow(interpreter: PythonInterpreter?) {
    model.selectedItem = rowOf(interpreter)
                         ?: SdkComboItem.ModuleInterpreter.takeIf { it in model.items }
                         ?: rowOf(loaded.source.moduleInterpreter)
  }

  private fun rowOf(interpreter: PythonInterpreter?): SdkComboItem.Existing<PythonInterpreter>? =
    interpreter?.let { SdkComboItem.Existing(it) }?.takeIf { it in model.items }

  /**
   * Shows or hides the interpreters of the other Python projects, and opens the popup again. The selection stays, also
   * when the owned mode has no row for it: the combo keeps it as its value, so the toggle never changes the interpreter.
   */
  private fun toggleOtherProjects() {
    val selected = model.selected
    showOtherProjects = !showOtherProjects
    publishRows(listOfNotNull(selectedRowInterpreter))
    model.setSelectedItemFromPresenter(selected)
    ApplicationManager.getApplication().invokeLater { combo.showPopup() }
  }
}

/** A request for a read. Each is a new instance, so the state flow never drops a request as equal to the last one. */
private sealed interface ReadRequest {
  /** Select what [select] picks from the interpreter of the module. [include] has a row also when no project lists it. */
  class Pick(val include: PythonInterpreter?, val select: (PythonInterpreter?) -> PythonInterpreter?) : ReadRequest

  /** Select the "module interpreter" row. */
  class ModuleRow : ReadRequest

  /** Keep the selected row. */
  class Keep : ReadRequest
}

private class Loaded(
  val source: InterpreterComboSource<PythonInterpreter>,
  /** The presentation of each interpreter that the combo can hold. */
  val items: Map<PythonInterpreter, PyInterpreterItem>,
  /** Where the add flow adds an interpreter, or `null` before the first read. */
  val addTarget: ModuleOrProject.ModuleAndProject?,
)

/**
 * The combo renderer: [rows] draws every row except an owner caption, which [GroupHeaderRow] draws. The popup does not
 * select a caption, see [ComboBox.SelectableItem].
 */
private class ComboRenderer(private val rows: ListCellRenderer<SdkComboItem<PythonInterpreter>>) : CompositeRenderer<SdkComboItem<PythonInterpreter>>() {
  private val header = GroupHeaderRow()
  private val headers = ListCellRenderer<SdkComboItem<PythonInterpreter>> { _, value, _, _, _ ->
    header.apply {
      caption.caption = when (value) {
        is SdkComboItem.GroupHeader -> value.title
        is SdkComboItem.Existing, SdkComboItem.ModuleInterpreter, SdkComboItem.ToggleOtherProjects,
        SdkComboItem.ShowAll, null,
          -> null
      }
    }
  }

  override fun selectRenderer(value: SdkComboItem<PythonInterpreter>?): ListCellRenderer<*> =
    if (value is SdkComboItem.GroupHeader) headers else rows
}

/**
 * An owner caption of the combo popup: a group caption without a line. The popup cannot select it.
 *
 * The popup already indents each row, so the caption has no left inset of its own: it starts where the icons of the
 * rows start, as on the All Interpreters page.
 */
private class GroupHeaderRow : JPanel(BorderLayout()), ComboBox.SelectableItem {
  val caption: GroupHeaderSeparator = GroupHeaderSeparator(captionInsets()).apply { setHideLine(true) }

  init {
    isOpaque = false
    add(caption, BorderLayout.CENTER)
  }

  override fun isSelectable(): Boolean = false
}

/** The insets of a popup group caption, without the left inset. */
private fun captionInsets(): Insets {
  val insets = JBUI.CurrentTheme.Popup.separatorLabelInsets()
  // The values are scaled already, so they go into plain insets and are not scaled again.
  return Insets(insets.top, 0, insets.bottom, insets.right)
}
