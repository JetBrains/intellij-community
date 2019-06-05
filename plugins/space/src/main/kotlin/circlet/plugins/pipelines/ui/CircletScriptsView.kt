package circlet.plugins.pipelines.ui

import circlet.pipelines.config.api.*
import circlet.plugins.pipelines.services.*
import circlet.plugins.pipelines.viewmodel.*
import circlet.runtime.*
import com.intellij.execution.*
import com.intellij.execution.filters.*
import com.intellij.execution.impl.*
import com.intellij.execution.ui.*
import com.intellij.icons.*
import com.intellij.ide.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.*
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.*
import com.intellij.ui.*
import com.intellij.ui.components.*
import com.intellij.ui.treeStructure.Tree
import kotlinx.coroutines.*
import runtime.async.*
import runtime.reactive.*
import java.awt.*
import javax.swing.*
import javax.swing.tree.*

class CircletScriptsViewFactory() {
    companion object{
        private const val consolePanelName = "console"
        private const val taskNotSelectedPanelName = "taskNotSelected"
    }

    fun createView(lifetime: Lifetime, project: Project, viewModel: ScriptWindowViewModel) : JComponent {
        val splitPane = Splitter(false)
        val modelTreeView = createModelTreeView(lifetime, project, viewModel)
        splitPane.firstComponent = modelTreeView
        val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console as ConsoleViewImpl
        Disposer.register(project, console)
        val layout = CardLayout()

        val logPanel = JPanel(layout).apply {
            add(JBLabel("select task to run"), taskNotSelectedPanelName)
            add(console.component, consolePanelName)
        }
        splitPane.secondComponent = logPanel

        viewModel.logData.forEach(lifetime) {
            console.clear()
            if (it != null) {
                layout.show(logPanel, consolePanelName)
                console.print(it.dummy, ConsoleViewContentType.NORMAL_OUTPUT)
            }
            else {
                layout.show(logPanel, taskNotSelectedPanelName)
            }
        }
        return splitPane
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
        expandTree(tree)
        tree.isRootVisible = false

        val refreshLifetimes = SequentialLifetimes(lifetime)
        val scriptModelBuilder = ScriptModelBuilder()
        val refreshAction = object : DumbAwareActionButton(IdeBundle.message("action.refresh"), AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) {
                if (viewModel.modelBuildIsRunning.value) {
                    return
                }
                viewModel.modelBuildIsRunning.value = true
                val lt = refreshLifetimes.next()
                GlobalScope.launch {
                    val model = scriptModelBuilder.build(lt, project)
                    viewModel.script.value = model

                }.invokeOnCompletion {
                    launch(lt, ApplicationUiDispatch.coroutineContext) {
                        val model = viewModel.script.value
                        resetNodes(root, model)
                        tree.updateUI()
                        viewModel.modelBuildIsRunning.value = false
                    }
                }
            }
        }

        val runAction = object : DumbAwareActionButton(ExecutionBundle.message("run.configurable.display.name"), AllIcons.RunConfigurations.TestState.Run) {
            override fun actionPerformed(e: AnActionEvent) {
                if (viewModel.modelBuildIsRunning.value) {
                    return
                }
                Messages.showInfoMessage("run build", "circlet")
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
            .createPanel()
    }

    private fun expandTree(tree: JTree) {
        var oldRowCount = 0
        do {
            val rowCount = tree.rowCount
            if (rowCount == oldRowCount) break
            oldRowCount = rowCount
            for (i in 0 until rowCount) {
                tree.expandRow(i)
            }
        } while (true)
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
                    triggers.forEach {
                        triggersNode.add(CircletModelTreeNode(it::class.java.simpleName))
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
