// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Condition
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.util.IconUtil
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.ModuleOrProject.ModuleAndProject
import com.jetbrains.python.sdk.ModuleOrProject.ProjectOnly
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

/**
 * The list of Python interpreters with actions ("Add", "Remove", "Show Paths") and the details of the selected interpreter to the right of
 * the list.
 */
internal class PythonInterpreterMasterDetails(private val moduleOrProject: ModuleOrProject, private val parentConfigurable: Configurable) : MasterDetailsComponent() {
  private val pythonConfigurableInterpreterList: PyConfigurableInterpreterList = PyConfigurableInterpreterList.getInstance(moduleOrProject.project)

  private val project = moduleOrProject.project

  // Temporary hack as lots of legacy code accept nullable module. Remove after legacy code migrated to ModuleOrProject.
  private val module = when (moduleOrProject) {
    is ModuleAndProject -> moduleOrProject.module
    is ProjectOnly -> null
  }

  internal val projectSdksModel = pythonConfigurableInterpreterList.model

  /**
   * Indicates whether Python paths of one or more interpreters have been changed by user via Python Paths dialog.
   *
   * @see [ShowPathsAction]
   */
  private var pythonPathsModified = false

  /**
   * The field remembers the latest selected SDK in [myTree].
   *
   * It is used after closing "Python Interpreters" dialog with "OK" button to update Python interpreter associated with the project.
   * [myTree] cannot be addressed directly in this case as it is disposed on closing the dialog.
   *
   * @see [PythonInterpreterConfigurable.openInDialog]
   */
  internal var storedSelectedSdk: Sdk? = null

  private var hideOtherProjectVirtualenvs: Boolean = true

  private val treeModel: DefaultTreeModel
    get() = myTree.model as DefaultTreeModel

  init {
    // note that `MasterDetailsComponent` does not work without `initTree()`
    initTree()
    myTree.cellRenderer = PySdkListTreeRenderer()
    myTree.addTreeSelectionListener {
      val selectionPaths = myTree.selectionPaths
      // do not store multi-selection
      storedSelectedSdk = if (selectionPaths?.size == 1) selectionPaths[0].sdk else null
    }
  }

  private class PySdkListTreeRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
      tree: JTree,
      value: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean,
    ) {
      val configurable = (value as? DefaultMutableTreeNode)?.userObject as? PythonInterpreterDetailsConfigurable
      val sdk = configurable?.sdk
      // The name might have been changed with "Rename" action and stored in `displayName`, while the change not being reflected in `sdk`
      // instance yet
      val currentSdkName = configurable?.displayName
      customizeWithSdkValue(sdk, noInterpreterMarker, nullSdkValue = null, actualSdkName = currentSdkName)
    }
  }

  override fun getDisplayName(): String = PyBundle.message("sdk.details.dialog.title")

  override fun isModified(): Boolean {
    return pythonPathsModified || projectSdksModel.isModified || super.isModified()
  }

  private val allPythonSdksInEdit: List<Sdk>
    get() = pythonConfigurableInterpreterList.getAllPythonSdks(project, module)

  override fun reset() {
    pythonPathsModified = false

    myRoot.removeAllChildren()

    val visiblePythonSdks = when {
      hideOtherProjectVirtualenvs -> allPythonSdksInEdit.filter { !it.isAssociatedWithAnotherModule(module) }
      else -> allPythonSdksInEdit
    }
    visiblePythonSdks.forEach(::addSdkNode)

    super.reset()
  }

  /**
   * Suits for incremental addition [sdk] to the tree: it preserves a selection and inserts the provided [sdk] to the proper place.
   */
  private fun addSdkNode(sdk: Sdk) {
    addNode(MyNode(PythonInterpreterDetailsConfigurable(project, module, sdk, parentConfigurable)), myRoot)
  }

  private fun addSdkNodeAndSelect(sdk: Sdk) {
    addSdkNode(sdk)
    selectNodeInTree(sdk)
  }

  override fun apply() {
    super.apply()

    // Do not use `projectSdksModel.isModified` flag solely to optimize the method, because `isModified` remains `false` in case of
    // non-structural changes (f.e. if a JDK has been changed)
    projectSdksModel.apply(this)
  }

  override fun createActions(fromPopup: Boolean): List<AnAction> =
    if (fromPopup) {
      listOf(RemoveAction(), RenameAction(), ShowPathsAction())
    }
    else {
      // it would be nicer to insert the sdk at the proper place instead of adding it to the bottom
      val addInterpreterActionGroup = PopupActionGroup(collectAddInterpreterActions(moduleOrProject, ::addSdkNodeAndSelect))
      addInterpreterActionGroup.templatePresentation.icon = AllIcons.General.Add
      addInterpreterActionGroup.templatePresentation.text = PyBundle.message("python.interpreters.add.interpreter.action.text")
      addInterpreterActionGroup.isPopup = true
      addInterpreterActionGroup.registerCustomShortcutSet(CommonShortcuts.getInsert(), myTree)
      listOf(addInterpreterActionGroup, RemoveAction(), RenameAction(), ToggleVirtualEnvFilterButton(), ShowPathsAction())
    }

  private fun getSelectedSdk(): Sdk? = selectedObject as? Sdk

  private fun isEmptySdkSelection() = myTree.selectionPaths.isNullOrEmpty()

  /**
   * Note that implementing [MasterDetailsComponent.ActionGroupWithPreselection] guarantees that the group action will be handled as popup.
   */
  private class PopupActionGroup(actions: List<AnAction>) : DefaultActionGroup(actions), ActionGroupWithPreselection {
    override fun getActionGroup(): ActionGroup = this
  }

  private inner class RemoveAction : DumbAwareAction(PyBundle.messagePointer("python.interpreters.remove.interpreter.action.text"),
                                                     IconUtil.removeIcon) {
    init {
      registerCustomShortcutSet(CommonShortcuts.getDelete(), myTree)
    }

    override fun actionPerformed(e: AnActionEvent) {
      myTree.selectionPaths?.forEach { selectedPath ->
        projectSdksModel.removeSdk(selectedPath.sdk)
        TreeUtil.removeLastPathComponent(tree, selectedPath)
      }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = !isEmptySdkSelection()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }
  }

  private inner class RenameAction : DumbAwareAction(PyBundle.messagePointer("python.interpreters.rename.interpreter.action.text"),
                                                     IconUtil.editIcon) {
    init {
      registerCustomShortcutSet(CommonShortcuts.getRename(), myTree)
    }

    override fun actionPerformed(e: AnActionEvent) {
      val selectedSdk = getSelectedSdk() ?: return
      val initialName = selectedSdk.name
      val allNames: List<String> = myRoot.children().asSequence().mapNotNull { (it as? MyNode)?.displayName }.toList()
      val name = Messages.showInputDialog(
        myTree,
        PyBundle.message("python.interpreters.rename.interpreter.dialog.message"),
        PyBundle.message("python.interpreters.rename.interpreter.dialog.title"),
        null,
        initialName,
        object : InputValidatorEx {
          override fun getErrorText(inputString: String): String? =
            when {
              inputString.isBlank() -> PyBundle.message("rename.python.interpreter.dialog.provide.name.error.text")
              conflictsWithOtherInterpreter(inputString) -> PyBundle.message("rename.python.interpreter.name.already.exists.error.text")
              else -> null
            }

          override fun checkInput(inputString: String): Boolean = canClose(inputString)

          override fun canClose(inputString: String): Boolean = nameCanBeApplied(inputString)

          private fun nameCanBeApplied(inputString: String) = inputString.isNotBlank() && !allNames.contains(inputString)

          private fun conflictsWithOtherInterpreter(inputString: String): Boolean = inputString != initialName &&
                                                                                    allNames.contains(inputString)
        }
      )
      // Skip changing the name if either the dialog is cancelled or the name is not changed
      if (name == null || name == initialName) return
      // Delegate changing the name to the configurable
      selectedConfigurable?.displayName = name
      (myTree.model as? DefaultTreeModel)?.nodeChanged(selectedNode)
      myTree.revalidate()
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = !isEmptySdkSelection()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }
  }

  private inner class ToggleVirtualEnvFilterButton
    : DumbAwareToggleAction(PyBundle.messagePointer("sdk.details.dialog.hide.all.virtual.envs"),
                            Presentation.NULL_STRING, AllIcons.General.Filter) {

    override fun isSelected(e: AnActionEvent): Boolean = hideOtherProjectVirtualenvs

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (hideOtherProjectVirtualenvs && !state) {
        // reveal other virtualenvs
        allPythonSdksInEdit.filter { it.isAssociatedWithAnotherModule(module) }.forEach(::addSdkNode)
      }
      else if (!hideOtherProjectVirtualenvs && state) {
        // hide other virtualenvs
        val allPythonSdks = allPythonSdksInEdit
        allPythonSdks.filter { it.isAssociatedWithAnotherModule(module) }.forEach(::incrementalRemoveSdk)
      }
      hideOtherProjectVirtualenvs = state
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  /**
   * Removes provided sdk from the tree model and the tree itself.
   */
  private fun incrementalRemoveSdk(sdk: Sdk) {
    val treeNode = TreeUtil.findNode(myRoot, Condition { sdk == it.sdk }) ?: return
    treeModel.removeNodeFromParent(treeNode)
  }

  private inner class ShowPathsAction : DumbAwareAction(PyBundle.messagePointer("python.interpreters.show.interpreter.paths.text"),
                                                        AllIcons.Actions.ShowAsTree) {
    override fun actionPerformed(e: AnActionEvent) {
      val sdk: Sdk = getSelectedSdk() ?: return
      val pathEditor = createPathEditor(sdk)
      val sdkModificator = sdk.sdkModificator
      val dialog = PythonPathDialog(project, pathEditor)
      pathEditor.reset(sdkModificator)
      if (dialog.showAndGet() && pathEditor.isModified) {
        pathEditor.apply(sdkModificator)
        ApplicationManager.getApplication().runWriteAction {
          sdkModificator.commitChanges()
        }
        // now added and excluded paths are updated in `sdk` instance
        pythonPathsModified = true
        reloadSdk(sdk)
      }
    }

    private fun createPathEditor(sdk: Sdk): PythonPathEditor {
      return if (PythonSdkUtil.isRemote(sdk)) {
        PyRemotePathEditor(project, sdk)
      }
      else {
        PythonPathEditor(PyBundle.message("python.sdk.configuration.tab.title"), OrderRootType.CLASSES,
                         FileChooserDescriptorFactory.createAllButJarContentsDescriptor())
      }.apply { addReloadPathsActionCallback(::reloadSdk) }
    }
  }

  private fun reloadSdk() {
    val selectedSdk = getSelectedSdk()
    if (selectedSdk != null) {
      reloadSdk(selectedSdk)
    }
  }

  private fun reloadSdk(sdk: Sdk) {
    PythonSdkUpdater.updateVersionAndPathsSynchronouslyAndScheduleRemaining(sdk, project)
  }

  companion object {
    private val TreePath.sdk: Sdk
      get() = (lastPathComponent as MyNode).configurable.editableObject as Sdk

    private val TreeNode.sdk: Sdk?
      get() {
        val configurable = (this as? DefaultMutableTreeNode)?.userObject as? PythonInterpreterDetailsConfigurable
        return configurable?.sdk
      }
  }
}