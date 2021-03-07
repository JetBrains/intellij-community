// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.plugins.pipelines.ui

import circlet.pipelines.DefaultDslFileName
import circlet.pipelines.config.idea.api.*
import circlet.pipelines.config.idea.api.ProcessExecutable.*
import circlet.pipelines.config.idea.api.ScriptStep.CompositeStep
import circlet.pipelines.config.idea.api.ScriptStep.SimpleStep
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.plugins.pipelines.services.SpaceKtsModelBuilder
import com.intellij.space.plugins.pipelines.viewmodel.ScriptState
import com.intellij.space.plugins.pipelines.viewmodel.SpaceModelTreeNode
import com.intellij.space.utils.LifetimedDisposable
import com.intellij.space.utils.LifetimedDisposableImpl
import com.intellij.ui.DumbAwareActionButton
import com.intellij.ui.ToggleActionButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.Panel
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
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.jvm.javaType
import kotlin.reflect.typeOf

class SpaceToolWindowViewModel(val lifetime: Lifetime) {
  val taskIsRunning = mutableProperty(false)
  val selectedNode = mutableProperty<SpaceModelTreeNode?>(null)
  val extendedViewModeEnabled = mutableProperty(true)
}

@Service
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
      refreshAction.isEnabled = !smthIsRunning
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
                         config: IdeaScriptConfig?,
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

    val jobs = config.jobs
    val targets = config.targets
    val pipelines = config.pipelines

    var jobsTypesCount = 0
    if (jobs.any()) {
      jobsTypesCount++
    }
    if (targets.any()) {
      jobsTypesCount++
    }
    if (pipelines.any()) {
      jobsTypesCount++
    }
    val shouldAddGroupingNodes = jobsTypesCount > 1
    if (jobs.any()) {
      val jobsCollectionNode = getGroupingNode(root, "jobs", shouldAddGroupingNodes)
      config.jobs.forEach { job ->
        val jobNode = job.toTreeNode(showChildren = extendedViewModeEnabled)
        jobsCollectionNode.add(jobNode)
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

    val createDslLink = ActionLink(SpaceBundle.message("kts.toolwindow.add.automation.script.link")) {
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

private fun ScriptJob.toTreeNode(showChildren: Boolean) = SpaceModelTreeNode(name, true).apply {
  if (showChildren) {
    if (triggers.any()) {
      add(triggers.toTreeNode())
    }
    add(steps.toTreeNode())
  }
}

private fun List<Trigger>.toTreeNode() = SpaceModelTreeNode("triggers").apply {
  forEach { trigger ->
    add(trigger.toTreeNode())
  }
}

private fun Trigger.toTreeNode() = SpaceModelTreeNode(usefulSubTypeName() ?: "other trigger")

private fun StepSequence.toTreeNode() = SpaceModelTreeNode("steps").apply {
  forEach { step ->
    add(step.toTreeNode())
  }
}

private fun ScriptStep.toTreeNode(): SpaceModelTreeNode = when (val step = this) {
  is CompositeStep -> SpaceModelTreeNode(step.treeNodeText).apply {
    step.children.forEach { subStep ->
      add(subStep.toTreeNode())
    }
  }
  is SimpleStep.Process.Container -> SpaceModelTreeNode("container: ${step.image}").apply {
    add(step.data.exec.toTreeNode())
  }
  is SimpleStep.DockerComposeStep -> SpaceModelTreeNode("compose: ${step.mainService}")
  else -> SpaceModelTreeNode(step.usefulSubTypeName())
}

private val CompositeStep.treeNodeText: String get() = when(this) {
  is CompositeStep.Fork -> "parallel"
  is CompositeStep.Sequence -> "sequence"
  else -> usefulSubTypeName() ?: "composite step"
}

private fun ProcessExecutable.toTreeNode() = DefaultMutableTreeNode(treeNodeText)

private val ProcessExecutable.treeNodeText: String
  get() = when (this) {
    is ContainerExecutable.DefaultCommand -> "exec: defaultCommand${args.presentArgs()}"
    is ContainerExecutable.OverrideEntryPoint -> "exec: overrideEntryPoint: $entryPoint${args.presentArgs()}"
    is KotlinScript -> "exec: kts script"
    is ShellScript -> "exec: shell script"
    else -> "exec: ${usefulSubTypeName()}"
  }

private fun List<String>.presentArgs(): String {
  return if (this.any()) ". args: ${this.joinToString()}" else ""
}

/**
 * Returns the name of this object's dynamic type (which is a subtype of its static type [T]).
 * If this object is of an anonymous type, this method falls back to the name of the closest supertype of the runtime type of this object
 * that implements/extends its static type [T].
 * If this object is a direct anonymous implementation of the static type [T], this method returns `null` to allow other fallbacks.
 *
 * Most instances of the interfaces are anonymous classes at the moment in the space project.
 * For instance, the [Trigger] interface may have subinterfaces that we don't know of at compile time here, but could be added in the
 * future.
 * Since the object is of an anonymous class, we should use the name of the closest supertype that implements [Trigger], e.g. GitPush.
 */
@OptIn(ExperimentalStdlibApi::class)
private inline fun <reified T : Any> T.usefulSubTypeName(): String? {
  val simpleClassName = this::class.simpleName
  if (simpleClassName != null) { // false for anonymous classes
    return simpleClassName
  }
  // There is always at least one supertype (T), because we know this is an anonymous class that is also an instance of T
  val subTypeOfT = this::class.supertypes.first { it.isSubtypeOf(typeOf<T>()) }
  val simpleSubtypeName = subTypeOfT.javaType.typeName.substringAfterLast('.')
  return simpleSubtypeName.takeIf { it != T::class.simpleName }
}
