package circlet.plugins.pipelines.ui

import circlet.pipelines.config.api.*
import circlet.plugins.pipelines.services.*
import circlet.plugins.pipelines.viewmodel.*
import circlet.runtime.*
import com.intellij.icons.*
import com.intellij.ide.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.*
import com.intellij.openapi.ui.*
import com.intellij.ui.*
import com.intellij.ui.components.*
import com.intellij.ui.treeStructure.Tree
import kotlinx.coroutines.*
import runtime.async.*
import runtime.reactive.*
import javax.swing.*
import javax.swing.tree.*

class CircletScriptsView(private val lifetime: Lifetime, private val project: Project) {

    private val viewModel = ScriptWindowViewModel(lifetime, project)

    fun createView() : JComponent {
        val splitPane = Splitter(false)
        val modelTreeView = createModelTreeView()
        splitPane.firstComponent = modelTreeView
        val logPanel = JPanel()
        logPanel.add(JBLabel("your ad could be here"))
        splitPane.secondComponent = logPanel
        viewModel.logData.forEach(lifetime) {
            logPanel.removeAll()
            logPanel.add(JBLabel(it?.dummy ?: "select task to run"))
            logPanel.revalidate()
            logPanel.repaint()
        }
        return splitPane
    }

    private fun createModelTreeView() : JComponent {
        val tree = Tree()
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        val root = tree.model.root as DefaultMutableTreeNode
        tree.selectionModel.addTreeSelectionListener {
            val selectedNode = it.path.lastPathComponent
            if (selectedNode is CircletModelTreeNode) {
                viewModel.logData.value = if (selectedNode.isRunnable) LogData(selectedNode.userObject.toString()) else null
            }
            else {
                viewModel.logData.value = null
            }

        }
        resetNodes(root, viewModel.script.value)
        expandTree(tree)
        tree.isRootVisible = false

        val refreshLifetimes = SequentialLifetimes(lifetime)
        val scriptModelBuilder = ScriptModelBuilder()
        val refreshAction = object : DumbAwareActionButton(IdeBundle.message("action.refresh"), AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) {
                val lt = refreshLifetimes.next()
                GlobalScope.launch {
                    val model = scriptModelBuilder.build(lt, project)
                    viewModel.script.value = model

                }.invokeOnCompletion {
                    launch(lt, ApplicationUiDispatch.coroutineContext) {
                        val model = viewModel.script.value
                        resetNodes(root, model)
                        tree.updateUI()
                    }
                }
            }
        }

        return ToolbarDecorator
            .createDecorator(tree)
            .addExtraAction(refreshAction)
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

class CircletModelTreeNode(text: String? = null, val isRunnable: Boolean = false) : DefaultMutableTreeNode(text) {

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
