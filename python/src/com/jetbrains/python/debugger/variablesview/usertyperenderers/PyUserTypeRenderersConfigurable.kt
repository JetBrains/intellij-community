// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.variablesview.usertyperenderers

import com.intellij.ide.util.ElementsChooser
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.psi.util.QualifiedName
import com.intellij.ui.*
import com.intellij.ui.layout.*
import com.intellij.ui.table.JBTable
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionEditor
import com.jetbrains.python.PyBundle
import com.jetbrains.python.debugger.PyDebugProcess
import com.jetbrains.python.debugger.PyDebuggerEditorsProvider
import com.jetbrains.python.debugger.variablesview.usertyperenderers.codeinsight.PyTypeNameResolver
import com.jetbrains.python.debugger.variablesview.usertyperenderers.codeinsight.TypeNameCompletionProvider
import com.jetbrains.python.debugger.variablesview.usertyperenderers.codeinsight.getClassesNumberInModuleRootWithName
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import java.awt.BorderLayout
import java.awt.event.ActionListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.util.function.Supplier
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.table.AbstractTableModel


class PyUserTypeRenderersConfigurable : SearchableConfigurable {

  private val CONFIGURABLE_ID = "debugger.dataViews.python.type.renderers"
  private val CONFIGURABLE_NAME = PyBundle.message("configurable.PyUserTypeRenderersConfigurable.display.name")

  private val PANEL_SPLIT_PROPORTION = 0.3f

  private val myMainPanel: JPanel = JPanel(BorderLayout())
  private var myCurrentRenderer: PyUserNodeRenderer? = null

  private val myProject: Project
  private val myRendererChooser: ElementsChooser<PyUserNodeRenderer>
  private var myRendererSettings: RendererSettings? = null

  private var myRendererIndexToSelect: Int? = null
  private var myNewRendererToAdd: PyUserNodeRenderer? = null

  init {
    val projectManager = ProjectManager.getInstance()
    val openProjects = projectManager.openProjects
    myProject = if (openProjects.isNotEmpty()) openProjects.first() else projectManager.defaultProject
    myRendererChooser = ElementsChooser(true)
  }

  fun setRendererIndexToSelect(index: Int?) {
    myRendererIndexToSelect = index
  }

  fun setNewRendererToAdd(renderer: PyUserNodeRenderer?) {
    myNewRendererToAdd = renderer
  }

  override fun createComponent(): JPanel {
    ApplicationManager.getApplication().invokeLater {
      if (myProject.isDisposed) return@invokeLater
      myRendererSettings = RendererSettings()
      setupRendererSettings()
      setupRendererChooser()
      val chooserDecorator = RendererChooserToolbarDecorator().decorator
      val splitter = Splitter(false).apply {
        proportion = PANEL_SPLIT_PROPORTION
        firstComponent = chooserDecorator.createPanel()
        secondComponent = myRendererSettings
      }
      if (myRendererChooser.elementCount > 0) {
        val index = myRendererIndexToSelect ?: 0
        val first = myRendererChooser.getElementAt(index)
        myRendererChooser.selectElements(listOf(first))
      }
      myMainPanel.removeAll()
      myMainPanel.add(splitter, BorderLayout.CENTER)
    }
    return myMainPanel
  }

  override fun reset() {
    myRendererChooser.removeAllElements()
    myCurrentRenderer = null

    val settings = PyUserTypeRenderersSettings.getInstance()

    val newRendererToAdd = myNewRendererToAdd
    if (newRendererToAdd != null) {
      val newRender = newRendererToAdd.clone()
      myRendererChooser.addElement(newRender, newRender.isEnabled)
    }

    for (render in settings.renderers) {
      val newRenderer = render.clone()
      myRendererChooser.addElement(newRenderer, newRenderer.isEnabled)
    }

    val selectedRenderers = mutableListOf<PyUserNodeRenderer>()
    val indexToSelect = myRendererIndexToSelect
    if (indexToSelect != null && indexToSelect >= 0 && indexToSelect < myRendererChooser.elementCount) {
      val renderer = myRendererChooser.getElementAt(indexToSelect)
      selectedRenderers.add(renderer)
    }
    else if (myRendererChooser.elementCount != 0) {
      val firstRenderer = myRendererChooser.getElementAt(0)
      selectedRenderers.add(firstRenderer)
    }

    myRendererChooser.selectElements(selectedRenderers)
    myRendererSettings?.reset()
  }

  override fun isModified(): Boolean {
    if (myRendererSettings?.isModified() == true) return true
    val settings = PyUserTypeRenderersSettings.getInstance()
    if (myRendererChooser.elementCount != settings.renderers.size) return true
    settings.renderers.withIndex().forEach {
      val element = myRendererChooser.getElementAt(it.index)
      if (!element.equalTo(it.value)) return true
    }
    return false
  }

  override fun apply() {
    myRendererSettings?.apply()
    val settings = PyUserTypeRenderersSettings.getInstance()
    val renderers = mutableListOf<PyUserNodeRenderer>()
    for (i in 0 until myRendererChooser.elementCount) {
      val element = myRendererChooser.getElementAt(i)
      renderers.add(element.clone())
    }
    settings.setRenderers(renderers)
    applyRenderersToDebugger()
  }

  private fun applyRenderersToDebugger() {
    val debugSession = XDebuggerManager.getInstance(myProject).currentSession
    (debugSession?.debugProcess as? PyDebugProcess)?.let { debugProcess ->
      debugProcess.setUserTypeRenderersSettings()
      debugProcess.dropFrameCaches()
      debugProcess.session?.rebuildViews()
    }
  }

  override fun getDisplayName(): String = CONFIGURABLE_NAME

  override fun getId(): String = CONFIGURABLE_ID

  private fun setupRendererChooser() {
    myRendererChooser.emptyText.text = PyBundle.message("form.debugger.variables.view.user.type.renderers.no.renderers")
    myRendererChooser.addElementsMarkListener(ElementsChooser.ElementsMarkListener { element, isMarked ->
      element.isEnabled = isMarked
    })
    myRendererChooser.addListSelectionListener { e ->
      if (!e.valueIsAdjusting) {
        updateCurrentRenderer(myRendererChooser.selectedElements)
      }
    }
  }

  private fun setupRendererSettings() {
    myRendererSettings?.isVisible = false
  }

  private fun updateCurrentRendererName(newName: String) {
    myCurrentRenderer?.let {
      it.name = newName
      myRendererChooser.refresh(it)
    }
  }

  private fun updateCurrentRenderer(selectedElements: List<PyUserNodeRenderer>) {
    when (selectedElements.size) {
      1 -> setCurrentRenderer(selectedElements[0])
      else -> setCurrentRenderer(null)
    }
  }

  private fun setCurrentRenderer(renderer: PyUserNodeRenderer?) {
    if (myCurrentRenderer === renderer) return
    if (myRendererSettings == null) return
    if (myRendererSettings?.isModified() == true) {
      myRendererSettings?.apply()
    }
    myCurrentRenderer = renderer
    myRendererSettings?.reset()
  }

  fun getCurrentlyVisibleNames(): List<String> {
    val resultList = mutableListOf<String>()
    for (i in 0 until myRendererChooser.elementCount) {
      myRendererChooser.getElementAt(i)?.name?.let { name ->
        resultList.add(name)
      }
    }
    return resultList
  }

  private inner class RendererChooserToolbarDecorator {

    val decorator = ToolbarDecorator.createDecorator(myRendererChooser.component)

    init {
      decorator.setToolbarPosition(ActionToolbarPosition.TOP)
      decorator.setAddAction(AddAction())
      decorator.setRemoveAction(RemoveAction())
      decorator.setMoveUpAction(MoveAction(true))
      decorator.setMoveDownAction(MoveAction(false))
    }

    private inner class AddAction : AnActionButtonRunnable {
      override fun run(button: AnActionButton?) {
        val renderer = PyUserNodeRenderer(true, getCurrentlyVisibleNames())
        myRendererChooser.addElement(renderer, renderer.isEnabled)
        myRendererChooser.moveElement(renderer, 0)
      }
    }

    private inner class RemoveAction : AnActionButtonRunnable {
      override fun run(button: AnActionButton?) {
        myRendererChooser.selectedElements.forEach {
          myRendererChooser.removeElement(it)
        }
      }
    }

    private inner class MoveAction(val myMoveUp: Boolean) : AnActionButtonRunnable {
      override fun run(button: AnActionButton?) {
        val selectedRow = myRendererChooser.selectedElementRow
        if (selectedRow < 0) return
        var newRow = selectedRow + if (myMoveUp) -1 else 1
        if (newRow < 0) {
          newRow = myRendererChooser.elementCount - 1
        }
        else if (newRow >= myRendererChooser.elementCount) {
          newRow = 0
        }
        myRendererChooser.moveElement(myRendererChooser.getElementAt(selectedRow), newRow)
      }
    }
  }

  override fun disposeUIResources() {
    super.disposeUIResources()
    myRendererSettings?.let {
      ApplicationManager.getApplication().executeOnPooledThread { Disposer.dispose(it) }
    }
  }

  private inner class RendererSettings : JPanel(BorderLayout()), Disposable {

    private val myPanel: JPanel
    private val myRendererNameTextField = JTextField()
    private val myAppendDefaultChildrenCheckBox = JCheckBox(
      PyBundle.message("form.debugger.variables.view.user.type.renderers.append.default.children"))
    private val myRbDefaultValueRenderer = JRadioButton(
      PyBundle.message("form.debugger.variables.view.user.type.renderers.use.default.renderer"))
    private val myRbExpressionValueRenderer = JRadioButton(
      PyBundle.message("form.debugger.variables.view.user.type.renderers.use.following.expression"))
    private val myRbDefaultChildrenRenderer = JRadioButton(
      PyBundle.message("form.debugger.variables.view.user.type.renderers.use.default.renderer"))
    private val myRbListChildrenRenderer = JRadioButton(
      PyBundle.message("form.debugger.variables.view.user.type.renderers.use.list.of.expressions"))
    private val myTypeNameTextField: TextFieldWithCompletion = TextFieldWithCompletion(myProject, TypeNameCompletionProvider(myProject), "",
                                                                                       true, true, true)
    private val myNodeValueExpressionEditor: XDebuggerExpressionEditor
    private val myChildrenRenderersListEditor: JComponent
    private val myChildrenListEditorTableModel: ChildrenListEditorTableModel
    private val myChildrenListEditorTable: JBTable

    init {
      myNodeValueExpressionEditor = XDebuggerExpressionEditor(myProject, PyDebuggerEditorsProvider(), "NodeValueExpression", null,
                                                              XExpressionImpl.EMPTY_EXPRESSION, false, false, true)
      myChildrenListEditorTableModel = ChildrenListEditorTableModel()
      myChildrenListEditorTable = JBTable(myChildrenListEditorTableModel)
      myChildrenRenderersListEditor = createChildrenListEditor()

      setupTypeNameEditor()
      setupPanelComponents()
      myPanel = createSettingsPanel()
      add(myPanel, BorderLayout.NORTH)
    }

    private fun createSettingsPanel(): JPanel {
      return panel {
        row(PyBundle.message("form.debugger.variables.view.user.type.renderers.name")) {
          myRendererNameTextField()
        }
        row {
          label(PyBundle.message("form.debugger.variables.view.user.type.renderers.apply.renderer.to.objects.of.type"))
        }
        row {
          myTypeNameTextField(CCFlags.growX)
        }
        row(PyBundle.message("form.debugger.variables.view.user.type.renderers.when.rendering.node")) {
          buttonGroup {
            row { myRbDefaultValueRenderer() }
            row { myRbExpressionValueRenderer() }
          }
          row {
            row { myNodeValueExpressionEditor.component(CCFlags.growX, comment = PyBundle.message("form.debugger.variables.view.user.type.renderers.variable.name")) }
          }
        }
        row(PyBundle.message("form.debugger.variables.view.user.type.renderers.when.expanding.node")) {
          buttonGroup {
            row { myRbDefaultChildrenRenderer() }
            row { myRbListChildrenRenderer() }
          }
          row {
            row { myChildrenRenderersListEditor(CCFlags.growX, comment = PyBundle.message("form.debugger.variables.view.user.type.renderers.variable.name")) }
            row { myAppendDefaultChildrenCheckBox() }
          }
        }
      }
    }

    private fun setupRendererNameField() {
      myRendererNameTextField.document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          val newName = myRendererNameTextField.text
          updateCurrentRendererName(newName)
        }
      })
    }

    private fun setupTypeNameEditor() {
      val myTypeNameFieldValidator = ComponentValidator(this).withValidator(
        Supplier<ValidationInfo?> {
          val text: String = myTypeNameTextField.text
          return@Supplier if (!isValidTypeName(text)) {
            ValidationInfo(PyBundle.message("form.debugger.variables.view.user.type.renderers.class.not.found"),
                           myTypeNameTextField)
          }
          else {
            null
          }
        }).installOn(myTypeNameTextField)

      myTypeNameTextField.addFocusListener(object : FocusAdapter() {
        override fun focusLost(e: FocusEvent) {
          myTypeNameFieldValidator.revalidate()
        }
      })

      myTypeNameTextField.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
          updateSelfType()
        }
      })
    }

    private fun isValidTypeName(typeName: String): Boolean {
      return PyTypeNameResolver(myProject).resolve(typeName) != null
    }

    private fun updateSelfType() {
      val typeName = myTypeNameTextField.text
      val moduleName = typeName.substringBeforeLast(".")
      val className = typeName.substringAfterLast(".")
      val import = if (moduleName == className) "" else "from $moduleName import $className"
      val contextText =
        """
        $import
        def foo(self: $className):
          pass
        """.trimIndent()
      val contextWithSelf = PyExpressionCodeFragmentImpl(myProject, "fragment.py", contextText, true)
      val offset = contextText.indexOf("def")
      val srcPosition = XSourcePositionImpl.createByOffset(contextWithSelf.virtualFile, offset)
      myNodeValueExpressionEditor.setSourcePosition(srcPosition)
    }

    private fun setupRadioButtons() {
      myRbDefaultValueRenderer.isSelected = true
      myRbDefaultChildrenRenderer.isSelected = true
      val listener = ActionListener { e ->
        when (e.source) {
          myRbDefaultChildrenRenderer -> {
            myAppendDefaultChildrenCheckBox.isEnabled = myRbListChildrenRenderer.isSelected
            myChildrenListEditorTable.isEnabled = myRbListChildrenRenderer.isSelected
          }
          myRbListChildrenRenderer -> {
            myAppendDefaultChildrenCheckBox.isEnabled = myRbListChildrenRenderer.isSelected
            myChildrenListEditorTable.isEnabled = myRbListChildrenRenderer.isSelected
            val renderer = myCurrentRenderer
            if (myChildrenListEditorTableModel.rowCount == 0 && renderer != null) {
              val pyClass = PyTypeNameResolver(myProject).resolve(renderer.toType)
              pyClass?.visitClassAttributes({
                                              myChildrenListEditorTableModel.addRow("self.${it.name}")
                                              true
                                            }, false, null)
            }
          }
        }
      }
      myRbDefaultValueRenderer.addActionListener(listener)
      myRbExpressionValueRenderer.addActionListener(listener)
      myRbDefaultChildrenRenderer.addActionListener(listener)
      myRbListChildrenRenderer.addActionListener(listener)
    }

    private fun setupPanelComponents() {
      setupRendererNameField()
      setupRadioButtons()
      myAppendDefaultChildrenCheckBox.isEnabled = false
      myChildrenListEditorTable.isEnabled = false
    }

    private fun createChildrenListEditor(): JComponent {
      myChildrenListEditorTable.setShowGrid(true)
      return ChildrenListEditorToolbarDecorator(myChildrenListEditorTable, myChildrenListEditorTableModel).decorator.createPanel()
    }

    override fun setVisible(aFlag: Boolean) {
      myPanel.isVisible = aFlag
    }

    fun isModified(): Boolean {
      myCurrentRenderer?.let {
        return it.name != myRendererNameTextField.text ||
               it.toType != myTypeNameTextField.text ||
               it.valueRenderer.isDefault != myRbDefaultValueRenderer.isSelected ||
               it.valueRenderer.expression != myNodeValueExpressionEditor.expression.expression ||
               it.childrenRenderer.isDefault != myRbDefaultChildrenRenderer.isSelected ||
               it.childrenRenderer.children != myChildrenListEditorTableModel.clonedChildren ||
               it.childrenRenderer.appendDefaultChildren != myAppendDefaultChildrenCheckBox.isSelected
      }
      return false
    }

    fun apply() {
      myCurrentRenderer?.let {
        it.name = myRendererNameTextField.text
        it.toType = myTypeNameTextField.text
        it.valueRenderer.isDefault = myRbDefaultValueRenderer.isSelected
        it.valueRenderer.expression = myNodeValueExpressionEditor.expression.expression
        it.childrenRenderer.isDefault = myRbDefaultChildrenRenderer.isSelected
        it.childrenRenderer.children = myChildrenListEditorTableModel.clonedChildren
        it.childrenRenderer.appendDefaultChildren = myAppendDefaultChildrenCheckBox.isSelected
        resetTypeInfo(it)
      }
    }

    private fun resetTypeInfo(renderer: PyUserNodeRenderer) {
      renderer.typeSourceFile = ""
      renderer.moduleRootHasOneTypeWithSameName = false

      val type = renderer.toType
      val cls = PyTypeNameResolver(myProject).resolve(type) ?: return
      val clsName = cls.name ?: return
      val importPath = QualifiedNameFinder.findCanonicalImportPath(cls, null)

      renderer.typeCanonicalImportPath = if (importPath != null) "$importPath.${clsName}" else ""
      renderer.typeQualifiedName = cls.qualifiedName ?: ""
      renderer.typeSourceFile = cls.containingFile?.virtualFile?.path ?: ""
      renderer.moduleRootHasOneTypeWithSameName = getClassesNumberInModuleRootWithName(cls, importPath ?: QualifiedName.fromComponents(),
                                                                                       myProject) == 1
    }

    private fun resetChildrenListEditorTableModel(currentRenderer: PyUserNodeRenderer) {
      myChildrenListEditorTableModel.clear()
      for (child in currentRenderer.childrenRenderer.children) {
        myChildrenListEditorTableModel.addRow(child.expression)
      }
    }

    private fun resetRadioButtons(currentRenderer: PyUserNodeRenderer) {
      if (currentRenderer.valueRenderer.isDefault) {
        myRbDefaultValueRenderer.doClick()
      }
      else {
        myRbExpressionValueRenderer.doClick()
      }
      if (currentRenderer.childrenRenderer.isDefault) {
        myRbDefaultChildrenRenderer.doClick()
      }
      else {
        myRbListChildrenRenderer.doClick()
      }
    }

    fun reset() {
      myCurrentRenderer?.let {
        myRendererNameTextField.text = it.name
        myTypeNameTextField.text = it.toType
        myNodeValueExpressionEditor.expression = XExpressionImpl.fromText(it.valueRenderer.expression)
        myAppendDefaultChildrenCheckBox.isSelected = it.childrenRenderer.appendDefaultChildren
        resetRadioButtons(it)
        resetChildrenListEditorTableModel(it)
        myPanel.isVisible = true
      } ?: run {
        myPanel.isVisible = false
      }
      updateSelfType()
    }

    override fun dispose() {
      // do nothing
    }

    private inner class ChildrenListEditorToolbarDecorator(val table: JBTable, val tableModel: ChildrenListEditorTableModel) {

      val decorator = ToolbarDecorator.createDecorator(table)

      init {
        decorator.setToolbarPosition(ActionToolbarPosition.TOP)
        decorator.setAddAction(AddAction())
        decorator.setRemoveAction(RemoveAction())
        decorator.setMoveUpAction(MoveAction(true))
        decorator.setMoveDownAction(MoveAction(false))
      }

      private inner class AddAction : AnActionButtonRunnable {
        override fun run(button: AnActionButton?) {
          tableModel.addRow("")
        }
      }

      private inner class RemoveAction : AnActionButtonRunnable {
        override fun run(button: AnActionButton?) {
          tableModel.removeRows(table.selectedRows)
        }
      }

      private inner class MoveAction(val up: Boolean) : AnActionButtonRunnable {
        override fun run(button: AnActionButton?) {
          if (up) {
            TableUtil.moveSelectedItemsUp(table)
          }
          else {
            TableUtil.moveSelectedItemsDown(table)
          }
        }
      }
    }

    private inner class ChildrenListEditorTableModel : AbstractTableModel() {
      private val COLUMN_COUNT = 1
      private val EXPRESSION_TABLE_COLUMN = 0

      private val myData = mutableListOf<PyUserNodeRenderer.ChildInfo>()

      override fun getColumnCount(): Int {
        return COLUMN_COUNT
      }

      override fun getRowCount(): Int {
        return myData.size
      }

      override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        return true
      }

      override fun getColumnClass(columnIndex: Int): Class<*> {
        return when (columnIndex) {
          EXPRESSION_TABLE_COLUMN -> String::class.java
          else -> super.getColumnClass(columnIndex)
        }
      }

      override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        if (rowIndex >= rowCount) {
          return null
        }
        val row = myData[rowIndex]
        return when (columnIndex) {
          EXPRESSION_TABLE_COLUMN -> row.expression
          else -> null
        }
      }

      override fun setValueAt(aValue: Any, rowIndex: Int, columnIndex: Int) {
        if (rowIndex >= rowCount) {
          return
        }
        val row = myData[rowIndex]
        when (columnIndex) {
          EXPRESSION_TABLE_COLUMN -> row.expression = aValue as String
        }
      }

      override fun getColumnName(columnIndex: Int): String? {
        return when (columnIndex) {
          EXPRESSION_TABLE_COLUMN -> null
          else -> ""
        }
      }

      fun addRow(expression: String) {
        myData.add(PyUserNodeRenderer.ChildInfo(expression))
        val lastRow = myData.size - 1
        fireTableRowsInserted(lastRow, lastRow)
      }

      fun removeRows(rows: IntArray) {
        val nonSelected = myData.filterIndexed({ id, _ -> id !in rows })
        myData.clear()
        myData.addAll(nonSelected)
        fireTableDataChanged()
      }

      fun clear() {
        myData.clear()
        fireTableDataChanged()
      }

      val clonedChildren: List<PyUserNodeRenderer.ChildInfo>
        get() = myData.toList()
    }
  }
}