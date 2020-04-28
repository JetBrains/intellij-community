package circlet.plugins.pipelines.ui

import circlet.messages.*
import circlet.pipelines.*
import circlet.pipelines.config.api.*
import circlet.plugins.pipelines.services.*
import circlet.plugins.pipelines.services.run.*
import circlet.plugins.pipelines.viewmodel.*
import circlet.utils.*
import com.intellij.execution.*
import com.intellij.icons.*
import com.intellij.ide.*
import com.intellij.ide.util.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import com.intellij.openapi.vfs.*
import com.intellij.openapi.wm.*
import com.intellij.ui.*
import com.intellij.ui.components.*
import com.intellij.ui.components.labels.*
import com.intellij.ui.content.*
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.*
import com.intellij.util.ui.tree.*
import libraries.coroutines.extra.*
import libraries.klogging.*
import runtime.*
import runtime.reactive.*
import runtime.reactive.combineLatest
import runtime.reactive.property.*
import java.awt.*
import javax.swing.*
import javax.swing.BoxLayout
import javax.swing.tree.*

class CircletToolWindowViewModel(val lifetime: Lifetime) {
    val taskIsRunning = mutableProperty(false)
    val selectedNode = mutableProperty<CircletModelTreeNode?>(null)
    val extendedViewModeEnabled = mutableProperty(true)
}

class CircletToolWindowService(val project: Project) : LifetimedDisposable by LifetimedDisposableImpl(), KLogging() {

    val modelBuilder = project.service<SpaceKtsModelBuilder>()

    val viewModel = CircletToolWindowViewModel(lifetime)

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
            viewModel.selectedNode.value = if (selectedNode is CircletModelTreeNode) selectedNode else null
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

        val showExtendedInfoAction = object : ToggleActionButton(CircletBundle.message("show.details"), AllIcons.Actions.Show) {
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

    private fun resetNodes(root: DefaultMutableTreeNode, config: ScriptConfig?, error: String?, state: ScriptState, extendedViewModeEnabled: Boolean) {
        root.removeAllChildren()
        if (config == null) {
            if (error != null) {
                root.add(CircletModelTreeNode(error))
                return
            }
            if (state == ScriptState.Building) {
                root.add(CircletModelTreeNode("Compiling script..."))
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
                val taskNode = CircletModelTreeNode(it.name, true)
                if (extendedViewModeEnabled) {
                    val triggers = it.triggers
                    if (triggers.any()) {
                        val triggersNode = CircletModelTreeNode("triggers")
                        triggers.forEach { trigger ->
                            triggersNode.add(CircletModelTreeNode(trigger::class.java.simpleName))
                        }

                        taskNode.add(triggersNode)
                    }

                    val jobsNode = CircletModelTreeNode("jobs")
                    it.steps.forEach {
                        jobsNode.add(it.traverseJobs())
                    }
                    taskNode.add(jobsNode)
                }

                tasksCollectionNode.add(taskNode)
            }
        }

        targets.forEach {
            val child = CircletModelTreeNode("target + ${it.name}. not implemented in UI yet")
            root.add(child)
        }
        pipelines.forEach {
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

        val createDslLink = LinkLabel.create("Add automation script") {
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

    private fun getGroupingNode(root: DefaultMutableTreeNode, name: String, shouldAddGroupingNodes: Boolean): DefaultMutableTreeNode {
        if (shouldAddGroupingNodes) {
            val res = CircletModelTreeNode(name, true)
            root.add(res)
            return res
        }

        return root
    }
}

fun ScriptStep.traverseJobs(): CircletModelTreeNode {
    when (val job = this) {
        is ScriptStep.CompositeStep -> {
            val res = CircletModelTreeNode(job::class.java.simpleName)
            job.children.forEach {
                val child = it.traverseJobs()
                res.add(child)
            }
            return res
        }

        is ScriptStep.Process.Container -> {
            val res = CircletModelTreeNode("container: ${job.image}")
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
            return CircletModelTreeNode("vm: ${job.image}. VM is not implemented in UI yet")
        }
    }
}

private fun List<String>.presentArgs(): String {
    return if (this.any()) ". args: ${this.joinToString()}" else ""
}
