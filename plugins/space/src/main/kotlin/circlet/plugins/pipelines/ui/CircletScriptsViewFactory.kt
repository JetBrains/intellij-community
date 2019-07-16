package circlet.plugins.pipelines.ui

import circlet.pipelines.config.api.*
import circlet.plugins.pipelines.services.*
import circlet.plugins.pipelines.services.run.*
import circlet.plugins.pipelines.viewmodel.*
import circlet.runtime.*
import com.intellij.execution.*
import com.intellij.icons.*
import com.intellij.ide.*
import com.intellij.ide.util.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.*
import com.intellij.openapi.project.*
import com.intellij.openapi.ui.*
import com.intellij.openapi.vfs.*
import com.intellij.ui.*
import com.intellij.ui.components.labels.*
import com.intellij.ui.treeStructure.*
import com.intellij.util.ui.tree.*
import libraries.klogging.*
import runtime.async.*
import runtime.reactive.*
import java.awt.*
import javax.swing.*
import javax.swing.BoxLayout
import javax.swing.tree.*


class CircletScriptsViewFactory : KLogging() {
    fun createView(lifetime: Lifetime, project: Project, viewModel: ScriptWindowViewModel) : JComponent {

        logger.debug("createView. begin")
        val layout = CardLayout()
        val panel = JPanel(layout)
        val treeCompName = "tree"
        val missedDslCompName = "empty"
        val treeView = createModelTreeView(lifetime, project, viewModel)
        panel.add(treeView, treeCompName)
        panel.add(createViewForMissedDsl(project), missedDslCompName)
        var shouldBuild = true
        viewModel.script.forEach(lifetime) { script ->
            logger.debug("createView. view viewModel. $script")
            if (script == null) {
                layout.show(panel, missedDslCompName)
                shouldBuild = true
            }
            else {
                layout.show(panel, treeCompName)
                if (shouldBuild) {
                    ScriptModelBuilder.updateModel(project, viewModel)
                }
                shouldBuild = false
            }
        }
        logger.debug("createView. end")
        return panel
    }

    private fun createModelTreeView(lifetime: Lifetime, project: Project, viewModel: ScriptWindowViewModel) : JComponent {
        val tree = Tree()
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        val root = tree.model.root as DefaultMutableTreeNode
        tree.selectionModel.addTreeSelectionListener {
            val selectedNode = it.path.lastPathComponent
            viewModel.selectedNode.value = if (selectedNode is CircletModelTreeNode) selectedNode else null

        }
        resetNodes(root, viewModel.script.value)
        TreeUtil.expandAll(tree)
        tree.isRootVisible = false

        viewModel.script.forEach(lifetime) {
            launch(lifetime, ApplicationUiDispatch.coroutineContext) {
                val model = viewModel.script.value
                resetNodes(root, model)
                (tree.model as DefaultTreeModel).reload()
                viewModel.modelBuildIsRunning.value = false
            }
        }

        val refreshAction = object : DumbAwareActionButton(IdeBundle.message("action.refresh"), AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) {
                ScriptModelBuilder.updateModel(project, viewModel)
            }
        }

        val runAction = object : DumbAwareActionButton(ExecutionBundle.message("run.configurable.display.name"), AllIcons.RunConfigurations.TestState.Run) {
            override fun actionPerformed(e: AnActionEvent) {
                if (viewModel.modelBuildIsRunning.value) {
                    return
                }
                val selectedNode = viewModel.selectedNode.value ?: return
                if (!selectedNode.isRunnable){
                    return
                }
                val taskName = selectedNode.userObject
                CircletRunConfigurationUtils.run(taskName.toString(), project)
            }
        }

        val expandAllAction = object : DumbAwareActionButton(IdeBundle.message("action.expand.all"), AllIcons.Actions.Expandall) {
            override fun actionPerformed(e: AnActionEvent) {
                if (viewModel.modelBuildIsRunning.value) {
                    return
                }

                TreeUtil.expandAll(tree)
            }
        }

        val collapseAllAction = object : DumbAwareActionButton(IdeBundle.message("action.collapse.all"), AllIcons.Actions.Collapseall) {
            override fun actionPerformed(e: AnActionEvent) {
                if (viewModel.modelBuildIsRunning.value) {
                    return
                }

                TreeUtil.collapseAll(tree, 0)
            }
        }

        fun updateActionsIsEnabledStates() {
            val smthIsRunning = viewModel.modelBuildIsRunning.value || viewModel.taskIsRunning.value
            val isSelectedNodeRunnable = viewModel.selectedNode.value?.isRunnable ?: false
            refreshAction.isEnabled = !smthIsRunning
            runAction.isEnabled = !smthIsRunning && isSelectedNodeRunnable
        }

        viewModel.apply {
            selectedNode.forEach(lifetime) {
                updateActionsIsEnabledStates()
            }
            modelBuildIsRunning.forEach(lifetime) {
                updateActionsIsEnabledStates()
            }
            taskIsRunning.forEach(lifetime) {
                updateActionsIsEnabledStates()
            }
        }

        return ToolbarDecorator
            .createDecorator(tree)
            .addExtraAction(refreshAction)
            .addExtraAction(runAction)
            .addExtraAction(expandAllAction)
            .addExtraAction(collapseAllAction)
            .createPanel()
    }

    private fun resetNodes(root: DefaultMutableTreeNode, model: ScriptViewModel?) {
        root.removeAllChildren()
        if (model == null) {
            root.add(CircletModelTreeNode("model is empty"))
            return
        }

        val config = model.config
        val tasks = config.tasks
        if (tasks.any()) {
            val tasksCollectionNode = CircletModelTreeNode("tasks")
            config.tasks.forEach {
                val taskNode = CircletModelTreeNode(it.name, true)
                val triggers = it.triggers
                if (triggers.any()) {
                    val triggersNode = CircletModelTreeNode("triggers")
                    triggers.forEach { trigger ->
                        triggersNode.add(CircletModelTreeNode(trigger::class.java.simpleName))
                    }

                    taskNode.add(triggersNode)
                }

                val jobsNode = CircletModelTreeNode("jobs")
                it.jobs.forEach {
                    jobsNode.add(it.traverseJobs())
                }
                taskNode.add(jobsNode)

                tasksCollectionNode.add(taskNode)
            }
            root.add(tasksCollectionNode)
        }

        config.targets.forEach {
            val child = CircletModelTreeNode("target + ${it.name}. not implemented in UI yet")
            root.add(child)
        }
        config.pipelines.forEach {
            val child = CircletModelTreeNode("pipelines + ${it.name}. not implemented in UI yet")
            root.add(child)
        }
    }

    private fun createViewForMissedDsl(project: Project): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        val infoText = JLabel("Automation is not configured")
        infoText.isEnabled = false
        infoText.alignmentX = Component.CENTER_ALIGNMENT
        panel.add(infoText)

        val createDslLink = LinkLabel.create("Add automation DSL script") {
            val basePath = project.basePath
            if (basePath != null) {
                val baseDirFile = LocalFileSystem.getInstance().findFileByPath(basePath)
                if (baseDirFile != null) {
                    val application = ApplicationManager.getApplication()
                    application.runWriteAction {
                        val file = baseDirFile.createChildData(this, "circlet.kts")
                        val newLine = System.getProperty("line.separator", "\n")
                        val newFileContent = "//todo add link to help/tutorial${newLine}task(\"My First Task\") {$newLine  run(\"hello-world\")$newLine}"
                        VfsUtil.saveText(file, newFileContent)
                        PsiNavigationSupport.getInstance().createNavigatable(project, file, -1).navigate(true)
                    }
                }
            }
        }

        createDslLink.alignmentX = Component.CENTER_ALIGNMENT
        panel.add(createDslLink)

        panel.add(JLabel(" ")) // just separator

        val showHelpLink = LinkLabel.create("Getting started with automation") {
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
}

fun ProjectJob.traverseJobs() : CircletModelTreeNode {
    when (val job = this) {
        is ProjectJob.CompositeJob -> {
            val res = CircletModelTreeNode(job::class.java.simpleName)
            job.children.forEach {
                val child = it.traverseJobs()
                res.add(child)
            }
            return res
        }

        is ProjectJob.Process.Container -> {
            val res = CircletModelTreeNode("container: ${job.image}")
            val execPrefix = "exec: "
            val execNode = DefaultMutableTreeNode("exec:")
            val exec = job.data.exec
            when (exec) {
                is ProjectJob.ProcessExecutable.ContainerExecutable.DefaultCommand -> {
                    execNode.userObject = execPrefix + "defaultCommand${exec.args.presentArgs()}"
                }
                is ProjectJob.ProcessExecutable.ContainerExecutable.OverrideEntryPoint -> {
                    execNode.userObject = execPrefix + "overrideEntryPoint: ${exec.entryPoint}${exec.args.presentArgs()}"
                }
                is ProjectJob.ProcessExecutable.KotlinScript -> {
                    execNode.userObject = execPrefix + "kts script"
                }
            }
            res.add(execNode)
            return res
        }
        is ProjectJob.Process.VM -> {
            return CircletModelTreeNode("vm: ${job.image}. VM is not implemented in UI yet")
        }
    }
}

private fun List<String>.presentArgs() : String {
    return if (this.any()) ". args: ${this.joinToString()}" else ""
}
