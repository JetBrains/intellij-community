// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine.view

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.executeOnDMT
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.SingleAlarm
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.impl.actions.XDebuggerActions
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreePanel
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeRestorer
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import com.sun.jdi.request.EventRequest
import org.jetbrains.kotlin.idea.debugger.coroutine.CoroutineDebuggerContentInfo
import org.jetbrains.kotlin.idea.debugger.coroutine.CoroutineDebuggerContentInfo.Companion.XCOROUTINE_POPUP_ACTION_GROUP
import org.jetbrains.kotlin.idea.debugger.coroutine.KotlinDebuggerCoroutinesBundle
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineStackFrameItem
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CreationCoroutineStackFrameItem
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.CoroutineDebugProbesProxy
import org.jetbrains.kotlin.idea.debugger.coroutine.util.*
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.tree.TreePath

internal class CoroutineView(project: Project, javaDebugProcess: JavaDebugProcess) :
    Disposable, XDebugSessionListenerProvider, CreateContentParamsProvider {
    companion object {
        private const val VIEW_CLEAR_DELAY_MS = 100
        private val EMPTY_DISPATCHER_NAME = KotlinDebuggerCoroutinesBundle.message("coroutine.view.dispatcher.empty")
        val log by logger
    }
    
    val alarm = SingleAlarm({ resetRoot() }, VIEW_CLEAR_DELAY_MS, this)
    val isLiveUpdateEnabled = Registry.`is`("coroutine.panel.live.update")
    private val debugProcess = javaDebugProcess.debuggerSession.process
    private val renderer = SimpleColoredTextIconPresentationRenderer()
    private val mainPanel = JPanel(BorderLayout())
    private var treeState: XDebuggerTreeState? = null
    private var restorer: XDebuggerTreeRestorer? = null
    private val panel = XDebuggerTreePanel(
        project,
        javaDebugProcess.editorsProvider,
        this,
        null,
        XCOROUTINE_POPUP_ACTION_GROUP,
        null
    )

    init {
        val combobox = ComboBox<String>()
        combobox.renderer = createRenderer()
        combobox.addItem(null)
        val toolbar = createToolbar()
        val threadsPanel = Wrapper()
        threadsPanel.border = CustomLineBorder(0, 0, 1, 0)
        threadsPanel.add(toolbar.component, BorderLayout.EAST)
        threadsPanel.add(combobox, BorderLayout.CENTER)
        mainPanel.add(panel.mainPanel, BorderLayout.CENTER)
        installClickAndKeyListeners(panel.tree)
    }

    fun saveState() {
        if (!isLiveUpdateEnabled) return
        DebuggerUIUtil.invokeLater {
            if (panel.tree.root !is EmptyNode) {
                treeState = XDebuggerTreeState.saveState(panel.tree)
            }
        }
    }

    fun resetRoot() {
        DebuggerUIUtil.invokeLater {
            panel.tree.setRoot(EmptyNode(), false)
        }
    }

    fun collapseCoroutineHierarchyNode() {
        if (isLiveUpdateEnabled) return
        DebuggerUIUtil.invokeLater {
            panel.tree.root?.let { treeRoot ->
                val rootNode = treeRoot.children.firstOrNull() ?: return@invokeLater
                val pathToRootNode = TreePath(panel.tree.treeModel.getPathToRoot(rootNode))
                if ((rootNode as? XValueNodeImpl)?.name == KotlinDebuggerCoroutinesBundle.message("coroutine.view.node.jobs") && !panel.tree.isCollapsed(pathToRootNode)) {
                    panel.tree.collapsePath(pathToRootNode)
                }
            }
        }
    }

    fun isShowing() = mainPanel.isShowing

    fun renewRoot(suspendContext: SuspendContextImpl) {
        panel.tree.setRoot(XCoroutinesRootNode(suspendContext), false)
        if (treeState != null) {
            restorer?.dispose()
            restorer = treeState?.restoreState(panel.tree)
        }
    }

    override fun dispose() {
        if (restorer != null) {
            restorer?.dispose()
            restorer = null
        }
    }

    override fun debugSessionListener(session: XDebugSession): XDebugSessionListener =
        CoroutineViewDebugSessionListener(session, this)

    override fun createContentParams(): CreateContentParams =
        CreateContentParams(
            CoroutineDebuggerContentInfo.XCOROUTINE_THREADS_CONTENT,
            mainPanel,
            KotlinDebuggerCoroutinesBundle.message("coroutine.view.title"),
            null,
            panel.tree
        )

    inner class EmptyNode : XValueContainerNode<XValueContainer>(panel.tree, null, true, object : XValueContainer() {})

    inner class XCoroutinesRootNode(suspendContext: SuspendContextImpl) :
        XValueContainerNode<CoroutineTopGroupContainer>(
            panel.tree, null, false,
            CoroutineTopGroupContainer(suspendContext)
        )

    inner class CoroutineTopGroupContainer(val suspendContext: SuspendContextImpl) : XValueContainer() {
        override fun computeChildren(node: XCompositeNode) {
            node.setAlreadySorted(true)
            if (suspendContext.suspendPolicy != EventRequest.SUSPEND_ALL) {
                node.addChildren(
                    XValueChildrenList.singleton(
                        ErrorNode("to.enable.information.breakpoint.suspend.policy.should.be.set.to.all.threads")
                    ),
                    true,
                )
                return
            }
            val children = XValueChildrenList()
            if (Registry.`is`("coroutine.panel.show.jobs.hierarchy")) {
                children.add(RootCoroutineContainer(suspendContext))
            } else {
                children.add(DispatchersContainer(suspendContext))
            }
            node.addChildren(children, true)
        }
    }

    inner class RootCoroutineContainer(val suspendContext: SuspendContextImpl) :
        RendererContainer(renderer.renderNoIconNode(KotlinDebuggerCoroutinesBundle.message("coroutine.view.node.jobs"))) {
        override fun computeChildren(node: XCompositeNode) {
            invokeInSuspendContext(suspendContext) { suspendContext ->
                val coroutineDebugProxy = CoroutineDebugProbesProxy(suspendContext)
                val coroutineCache = coroutineDebugProxy.dumpCoroutines()
                if (!coroutineCache.isOk()) {
                    node.addChildren(XValueChildrenList.singleton(ErrorNode("coroutine.view.fetching.error")), true)
                    return@invokeInSuspendContext
                }
                val coroutines = XValueChildrenList()
                val cache = coroutineCache.cache
                val isHierarchyBuilt = coroutineDebugProxy.fetchAndSetJobsAndParentsForCoroutines(cache)
                if (isHierarchyBuilt) {
                    val parentJobToChildCoroutineInfos = cache.groupBy { it.parentJob }
                    val jobToCoroutineInfo = cache.associateBy { it.job }
                    val rootJobs = cache.filter { it.parentJob == null }.mapNotNull { it.job }
                    for (rootJob in rootJobs) {
                        val rootCoroutine = jobToCoroutineInfo[rootJob]
                        coroutines.add(
                            CoroutineContainer(
                                suspendContext = suspendContext,
                                rootJob = rootJob,
                                rootCoroutineInfo = rootCoroutine,
                                isCurrent = rootCoroutine?.isRunningOnCurrentThread(suspendContext) ?: false,
                                childCoroutines = parentJobToChildCoroutineInfos[rootJob] ?: emptyList(),
                                parentJobToChildCoroutines = parentJobToChildCoroutineInfos
                            )
                        )
                    }
                } else {
                    // If the job hierarchy was not fetched, add all the dumped coroutines in the plain view.
                    for (coroutine in cache) {
                        coroutines.add(
                            CoroutineContainer(
                                suspendContext = suspendContext,
                                rootJob = coroutine.job,
                                rootCoroutineInfo = coroutine,
                                isCurrent = coroutine.isRunningOnCurrentThread(suspendContext),
                                childCoroutines = emptyList(),
                                parentJobToChildCoroutines = emptyMap()
                            )
                        )
                    }
                }
                if (coroutines.size() > 0) {
                    node.addChildren(coroutines, true)
                } else {
                    node.addChildren(XValueChildrenList.singleton(InfoNode("coroutine.view.fetching.not_found")), true)
                }
            }
        }
    }

    inner class DispatchersContainer(val suspendContext: SuspendContextImpl) :
        RendererContainer(renderer.renderNoIconNode(KotlinDebuggerCoroutinesBundle.message("coroutine.view.node.dispatchers"))) {

        override fun computeChildren(node: XCompositeNode) {
            invokeInSuspendContext(suspendContext) { suspendContext ->
                val coroutineCache = CoroutineDebugProbesProxy(suspendContext).dumpCoroutines()
                if (!coroutineCache.isOk()) {
                    node.addChildren(XValueChildrenList.singleton(ErrorNode("coroutine.view.fetching.error")), true)
                    return@invokeInSuspendContext
                }

                val children = XValueChildrenList()
                val groups = coroutineCache.cache.groupBy { it.dispatcher }
                for (dispatcher in groups.keys) {
                    // Mark the group that contains a running coroutine with a tick
                    val coroutines = groups[dispatcher]
                    val isCurrent = coroutines?.any { it.isRunningOnCurrentThread(suspendContext) } ?: false
                    children.add(DispatcherContainer(suspendContext, dispatcher ?: EMPTY_DISPATCHER_NAME, isCurrent, coroutines))
                }

                if (children.size() > 0) {
                    node.addChildren(children, true)
                } else {
                    node.addChildren(XValueChildrenList.singleton(InfoNode("coroutine.view.fetching.not_found")), true)
                }
            }
        }
    }

    inner class DispatcherContainer(
        private val suspendContext: SuspendContextImpl,
        dispatcherName: String,
        isCurrent: Boolean,
        private val coroutines: List<CoroutineInfoData>?
    ) : RendererContainer(renderer.renderThreadGroup(dispatcherName, isCurrent)) {
        override fun computeChildren(node: XCompositeNode) {
            invokeInSuspendContext(suspendContext) { suspendContext ->
                val children = XValueChildrenList()
                coroutines?.forEach {
                    children.add(CoroutineContainer(suspendContext, it.job, it, it.isRunningOnCurrentThread(suspendContext), emptyList(), emptyMap()))
                }
                if (children.size() > 0) {
                    node.addChildren(children, true)
                } else {
                    node.addChildren(XValueChildrenList.singleton(InfoNode("coroutine.view.fetching.not_found")), true)
                }
            }
        }
    }

    inner class CoroutineContainer(
        private val suspendContext: SuspendContextImpl,
        rootJob: String?,
        private val rootCoroutineInfo: CoroutineInfoData?,
        isCurrent: Boolean,
        private val childCoroutines: List<CoroutineInfoData>,
        private val parentJobToChildCoroutines: Map<String?, List<CoroutineInfoData>>
    ) : RendererContainer(renderer.renderThreadGroup(rootCoroutineInfo?.coroutineDescriptor ?: rootJob ?: CoroutineInfoData.UNKNOWN_JOB, isCurrent)) {
        override fun computeChildren(node: XCompositeNode) {
            invokeInSuspendContext(suspendContext) { suspendContext ->
                val children = XValueChildrenList()
                if (rootCoroutineInfo != null) {
                    children.add(FramesContainer(rootCoroutineInfo, suspendContext))
                }

                childCoroutines.forEach { childCoroutine ->
                    val childCoroutines = parentJobToChildCoroutines[childCoroutine.job]
                    children.add(
                        CoroutineContainer(
                            suspendContext,
                            childCoroutine.job,
                            childCoroutine,
                            childCoroutine.isRunningOnCurrentThread(suspendContext),
                            childCoroutines ?: emptyList(),
                            parentJobToChildCoroutines
                        )
                    )
                }
                if (children.size() > 0) {
                    node.addChildren(children, true)
                } else {
                    node.addChildren(XValueChildrenList.singleton(InfoNode("coroutine.view.fetching.not_found")), true)
                }
            }
        }
    }

    inner class InfoNode(val error: String) : RendererContainer(renderer.renderInfoNode(error))

    inner class ErrorNode(val error: String) : RendererContainer(renderer.renderErrorNode(error))

    inner class FramesContainer(
        private val infoData: CoroutineInfoData,
        private val suspendContext: SuspendContextImpl
    ) : RendererContainer(renderer.renderNoIconNode("Stacktrace")) {
        override fun computeChildren(node: XCompositeNode) {
            node.setAlreadySorted(true)

            invokeInSuspendContext(suspendContext) { suspendContext ->
                val children = XValueChildrenList()
                val doubleFrameList = CoroutineFrameBuilder.build(infoData, suspendContext)
                doubleFrameList?.frames?.forEach {
                    children.add(CoroutineFrameValue(it))
                }
                val creationFrames = doubleFrameList?.creationFrames
                if (!creationFrames.isNullOrEmpty()) {
                    children.add(CreationFramesContainer(creationFrames))
                }
                node.addChildren(children, true)
            }
        }
    }

    inner class CreationFramesContainer(
        private val creationFrames: List<CreationCoroutineStackFrameItem>
    ) : RendererContainer(renderer.renderCreationNode()) {
        override fun computeChildren(node: XCompositeNode) {
            node.setAlreadySorted(true)

            val children = XValueChildrenList()
            creationFrames.forEach {
                children.add(CoroutineFrameValue(it))
            }
            node.addChildren(children, true)
        }
    }

    inner class CoroutineFrameValue(val frameItem: CoroutineStackFrameItem) : XNamedValue(frameItem.uniqueId()) {
        override fun computePresentation(node: XValueNode, place: XValuePlace): Unit =
            applyRenderer(node, renderer.render(frameItem.location))
    }

    open inner class RendererContainer(val presentation: SimpleColoredTextIcon) : XNamedValue(presentation.simpleString()) {
        override fun computePresentation(node: XValueNode, place: XValuePlace): Unit =
            applyRenderer(node, presentation)
    }

    private fun createRenderer(): SimpleListCellRenderer<in String> =
        SimpleListCellRenderer.create { label: JBLabel, value: String?, index: Int ->
            if (value != null) {
                label.text = value
            } else if (index >= 0) {
                label.text = KotlinDebuggerCoroutinesBundle.message("coroutine.dump.threads.loading")
            }
        }

    private fun createToolbar(): ActionToolbarImpl {
        val framesGroup = DefaultActionGroup()
        framesGroup
            .addAll(ActionManager.getInstance().getAction(XDebuggerActions.FRAMES_TOP_TOOLBAR_GROUP))
        val toolbar = ActionManager.getInstance().createActionToolbar(
            ActionPlaces.DEBUGGER_TOOLBAR, framesGroup, true
        ) as ActionToolbarImpl
        toolbar.setReservePlaceAutoPopupIcon(false)
        return toolbar
    }

    private fun applyRenderer(node: XValueNode, presentation: SimpleColoredTextIcon): Unit =
        node.setPresentation(presentation.icon, presentation.valuePresentation(), presentation.hasChildren)

    private fun installClickAndKeyListeners(tree: XDebuggerTree): Unit =
        CoroutineSelectedNodeListener(debugProcess, tree).install()
}

private fun invokeInSuspendContext(
    suspendContext: SuspendContextImpl,
    command: (SuspendContextImpl) -> Unit
) {
    executeOnDMT(suspendContext, PrioritizedTask.Priority.NORMAL) {
        command(suspendContext)
    }
}
