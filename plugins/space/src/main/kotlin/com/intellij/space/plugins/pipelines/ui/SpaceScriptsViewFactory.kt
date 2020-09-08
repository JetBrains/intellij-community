package com.intellij.space.plugins.pipelines.ui

import com.intellij.space.messages.SpaceBundle
import circlet.pipelines.DefaultDslFileName
import circlet.pipelines.config.api.ScriptConfig
import circlet.pipelines.config.api.ScriptStep
import com.intellij.space.plugins.pipelines.services.SpaceKtsModelBuilder
import com.intellij.space.plugins.pipelines.viewmodel.SpaceModelTreeNode
import com.intellij.space.plugins.pipelines.viewmodel.ScriptState
import com.intellij.space.utils.LifetimedDisposable
import com.intellij.space.utils.LifetimedDisposableImpl
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.DumbAwareActionButton
import com.intellij.ui.ToggleActionButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.Panel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.tree.TreeUtil
import libraries.coroutines.extra.Lifetime
import libraries.klogging.KLogging
import runtime.reactive.bind
import runtime.reactive.mutableProperty
import runtime.reactive.view
import java.awt.CardLayout
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

class SpaceToolWindowViewModel(val lifetime: Lifetime) {
  val taskIsRunning = mutableProperty(false)
  val selectedNode = mutableProperty<SpaceModelTreeNode?>(null)
  val extendedViewModeEnabled = mutableProperty(true)
}

class SpaceToolWindowService(val project: Project) : LifetimedDisposable by LifetimedDisposableImpl(), KLogging() {

  val modelBuilder = project.service<SpaceKtsModelBuilder>()

  val viewModel = SpaceToolWindowViewModel(lifetime)

  fun createToolWindowContent(toolWindow: ToolWindow) {

    // ping script builder first so that it starts building the model for the first time.
    modelBuilder.requestModel()

    val panel = Panel(title = null).apply {
      add(createView())
    }

    val content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false).apply {
      isCloseable = false
    }

    toolWindow.contentManager.addContent(content)
  }

  fun createView(): JComponent {
    logger.debug("createView. begin")
    val layout = CardLayout()
    val panel = JPanel(layout)
    val treeCompName = "tree"
    val missedDslCompName = "empty"
    val treeView = createModelTreeView(lifetime, project)

    panel.add(treeView, treeCompName)
    panel.add(createViewForMissedDsl(project), missedDslCompName)

    modelBuilder.script.view(lifetime) { lt, script ->
      if (script == null) {
        layout.show(panel, missedDslCompName)
      }
      else {
        layout.show(panel, treeCompName)
      }
    }

    logger.debug("createView. end")
    return panel
  }

  private fun createModelTreeView(lifetime: Lifetime, project: Project): JComponent {
    val tree = Tree()

    tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

    val root = tree.model.root as DefaultMutableTreeNode

    tree.selectionModel.addTreeSelectionListener {
      val selectedNode = it.path.lastPathComponent
      viewModel.selectedNode.value = if (selectedNode is SpaceModelTreeNode) selectedNode else null
    }

    TreeUtil.expandAll(tree)
    tree.isRootVisible = false

    lifetime.bind(modelBuilder.script) { script ->
      bind(viewModel.extendedViewModeEnabled) { extendedViewModeEnabled ->
        if (script != null) {
          bind(script.config) { config ->
            bind(script.error) { error ->
              bind(script.state) { state ->
                resetNodes(root, config, error, state, viewModel.extendedViewModeEnabled.value)
                (tree.model as DefaultTreeModel).reload()
              }
            }
          }
        }
        else {
          // seems unreachable state.
          root.removeAllChildren()
        }
      }
    }


    val refreshAction = object : DumbAwareActionButton(IdeBundle.message("action.refresh"), AllIcons.Actions.Refresh) {
      override fun actionPerformed(e: AnActionEvent) {
        modelBuilder.rebuildModel()
      }
    }

    //        val runAction = object : DumbAwareActionButton(ExecutionBundle.message("run.configurable.display.name"), AllIcons.RunConfigurations.TestState.Run) {
    //            override fun actionPerformed(e: AnActionEvent) {
    //                if (modelBuilder.script.value?.state?.value == ScriptState.Building) {
    //                    return
    //                }
    //                val selectedNode = viewModel.selectedNode.value ?: return
    //                if (!selectedNode.isRunnable) {
    //                    return
    //                }
    //                val taskName = selectedNode.userObject
    //                CircletRunConfigurationUtils.run(taskName.toString(), project)
    //            }
    //        }

    val expandAllAction = object : DumbAwareActionButton(IdeBundle.message("action.expand.all"), AllIcons.Actions.Expandall) {
      override fun actionPerformed(e: AnActionEvent) {
        if (modelBuilder.script.value?.state?.value == ScriptState.Building) {
          return
        }
        TreeUtil.expandAll(tree)
      }
    }

    val collapseAllAction = object : DumbAwareActionButton(IdeBundle.message("action.collapse.all"), AllIcons.Actions.Collapseall) {
      override fun actionPerformed(e: AnActionEvent) {
        if (modelBuilder.script.value?.state?.value == ScriptState.Building) {
          return
        }
        TreeUtil.collapseAll(tree, 0)
      }
    }

    val showExtendedInfoAction = object : ToggleActionButton(SpaceBundle.message("show.details"), AllIcons.Actions.Show) {
      override fun isSelected(e: AnActionEvent?): Boolean {
        return viewModel.extendedViewModeEnabled.value
      }

      override fun setSelected(e: AnActionEvent?, state: Boolean) {
        viewModel.extendedViewModeEnabled.value = state
      }
    }

    fun updateActionsIsEnabledStates() {
      val smthIsRunning = modelBuilder.script.value?.state?.value == ScriptState.Building || viewModel.taskIsRunning.value
      val isSelectedNodeRunnable = viewModel.selectedNode.value?.isRunnable ?: false
      refreshAction.isEnabled = !smthIsRunning
      //            runAction.isEnabled = !smthIsRunning && isSelectedNodeRunnable
    }

    viewModel.selectedNode.forEach(lifetime) {
      updateActionsIsEnabledStates()
    }

    modelBuilder.script.view(lifetime) { lt, script ->
      script?.state?.forEach(lt) {
        updateActionsIsEnabledStates()
      }
    }

    viewModel.taskIsRunning.forEach(lifetime) {
      updateActionsIsEnabledStates()
    }

    val panel = ToolbarDecorator
      .createDecorator(tree)
      .addExtraAction(refreshAction)
      //            .addExtraAction(runAction)
      .addExtraAction(expandAllAction)
      .addExtraAction(collapseAllAction)
      .addExtraAction(showExtendedInfoAction)
      .setToolbarPosition(ActionToolbarPosition.TOP)
      .createPanel()
    panel.border = JBEmptyBorder(0)
    return panel
  }

  private fun resetNodes(root: DefaultMutableTreeNode,
                         config: ScriptConfig?,
                         error: String?,
                         state: ScriptState,
                         extendedViewModeEnabled: Boolean) {
    root.removeAllChildren()
    if (config == null) {
      if (error != null) {
        root.add(SpaceModelTreeNode(error))
        return
      }
      if (state == ScriptState.Building) {
        root.add(SpaceModelTreeNode("Compiling script..."))
        return
      }
      return
    }

    val tasks = config.jobs
    val targets = config.targets
    val pipelines = config.pipelines

    var jobsTypesCount = 0
    if (tasks.any()) {
      jobsTypesCount++
    }
    if (targets.any()) {
      jobsTypesCount++
    }
    if (pipelines.any()) {
      jobsTypesCount++
    }
    val shouldAddGroupingNodes = jobsTypesCount > 1
    if (tasks.any()) {
      val tasksCollectionNode = getGroupingNode(root, "tasks", shouldAddGroupingNodes)
      config.jobs.forEach {
        val taskNode = SpaceModelTreeNode(it.name, true)
        if (extendedViewModeEnabled) {
          val triggers = it.triggers
          if (triggers.any()) {
            val triggersNode = SpaceModelTreeNode("triggers")
            triggers.forEach { trigger ->
              triggersNode.add(SpaceModelTreeNode(trigger::class.java.simpleName))
            }

            taskNode.add(triggersNode)
          }

          val jobsNode = SpaceModelTreeNode("jobs")
          it.steps.forEach {
            jobsNode.add(it.traverseJobs())
          }
          taskNode.add(jobsNode)
        }

        tasksCollectionNode.add(taskNode)
      }
    }

    targets.forEach {
      val child = SpaceModelTreeNode("target + ${it.name}. not implemented in UI yet")
      root.add(child)
    }
    pipelines.forEach {
      val child = SpaceModelTreeNode("pipelines + ${it.name}. not implemented in UI yet")
      root.add(child)
    }
  }

  private fun createViewForMissedDsl(project: Project): JComponent {
    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

    val infoText = JLabel(SpaceBundle.message("kts.toolwindow.automation.is.not.configured.label"))
    infoText.isEnabled = false
    infoText.alignmentX = Component.CENTER_ALIGNMENT
    panel.add(infoText)

    val createDslLink = LinkLabel.create(SpaceBundle.message("kts.toolwindow.add.automation.script.link")) {
      val basePath = project.basePath
      if (basePath != null) {
        val baseDirFile = LocalFileSystem.getInstance().findFileByPath(basePath)
        if (baseDirFile != null) {
          val application = ApplicationManager.getApplication()
          application.runWriteAction {
            val file = baseDirFile.createChildData(this, DefaultDslFileName)
            val newLine = System.getProperty("line.separator", "\n")
            val newFileContent =
              "// Write your automation script $newLine" +
              "// Once the script is ready, commit the changes to the repository $newLine" +
              "// Use full DSL reference when needed https://jetbrains.team/help/space/automation-dsl.html $newLine" +
              "job(\"My First Job\") {$newLine  " +
              "container(\"hello-world\")$newLine" +
              "}"
            VfsUtil.saveText(file, newFileContent)
            PsiNavigationSupport.getInstance().createNavigatable(project, file, -1).navigate(true)
          }
        }
      }
    }

    createDslLink.alignmentX = Component.CENTER_ALIGNMENT
    panel.add(createDslLink)

    panel.add(JLabel(" ")) // just separator

    val showHelpLink = LinkLabel.create(SpaceBundle.message("kts.toolwindow.help.link")) {
      BrowserUtil.browse("https://jetbrains.team")
    }
    showHelpLink.icon = AllIcons.General.ContextHelp
    showHelpLink.alignmentX = Component.CENTER_ALIGNMENT
    panel.add(showHelpLink)

    val rootPanel = JPanel()
    rootPanel.layout = GridBagLayout()
    rootPanel.add(panel, GridBagConstraints())
    return rootPanel
  }

  private fun getGroupingNode(root: DefaultMutableTreeNode, name: String, shouldAddGroupingNodes: Boolean): DefaultMutableTreeNode {
    if (shouldAddGroupingNodes) {
      val res = SpaceModelTreeNode(name, true)
      root.add(res)
      return res
    }

    return root
  }
}

fun ScriptStep.traverseJobs(): SpaceModelTreeNode {
  when (val job = this) {
    is ScriptStep.CompositeStep -> {
      val res = SpaceModelTreeNode(job::class.java.simpleName)
      job.children.forEach {
        val child = it.traverseJobs()
        res.add(child)
      }
      return res
    }

    is ScriptStep.Process.Container -> {
      val res = SpaceModelTreeNode("container: ${job.image}")
      val execPrefix = "exec: "
      val execNode = DefaultMutableTreeNode("exec:")
      val exec = job.data.exec
      when (exec) {
        is ScriptStep.ProcessExecutable.ContainerExecutable.DefaultCommand -> {
          execNode.userObject = execPrefix + "defaultCommand${exec.args.presentArgs()}"
        }
        is ScriptStep.ProcessExecutable.ContainerExecutable.OverrideEntryPoint -> {
          execNode.userObject = execPrefix + "overrideEntryPoint: ${exec.entryPoint}${exec.args.presentArgs()}"
        }
        is ScriptStep.ProcessExecutable.KotlinScript -> {
          execNode.userObject = execPrefix + "kts script"
        }
      }
      res.add(execNode)
      return res
    }
    is ScriptStep.Process.VM -> {
      return SpaceModelTreeNode("vm: ${job.image}. VM is not implemented in UI yet")
    }
  }
}

private fun List<String>.presentArgs(): String {
  return if (this.any()) ". args: ${this.joinToString()}" else ""
}
