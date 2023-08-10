// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine.view

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
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
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreePanel
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeRestorer
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode
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

class CoroutineView(project: Project, javaDebugProcess: JavaDebugProcess) :
    Disposable, XDebugSessionListenerProvider, CreateContentParamsProvider {
    companion object {
        private const val VIEW_CLEAR_DELAY_MS = 100
        private val EMPTY_DISPATCHER_NAME = KotlinDebuggerCoroutinesBundle.message("coroutine.view.dispatcher.empty")
        val log by logger
    }

    val alarm = SingleAlarm({ resetRoot() }, VIEW_CLEAR_DELAY_MS, this)
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
            val children = XValueChildrenList()
            children.add(CoroutineGroupContainer(suspendContext))
            node.addChildren(children, true)
        }
    }

    inner class CoroutineGroupContainer(val suspendContext: SuspendContextImpl) : RendererContainer(renderer.renderGroup(KotlinDebuggerCoroutinesBundle.message("coroutine.view.node.root"))) {
        override fun computeChildren(node: XCompositeNode) {
            if (suspendContext.suspendPolicy != EventRequest.SUSPEND_ALL) {
                node.addChildren(
                    XValueChildrenList.singleton(
                        ErrorNode("to.enable.information.breakpoint.suspend.policy.should.be.set.to.all.threads")
                    ),
                    true,
                )
                return
            }

            debugProcess.invokeInSuspendContext(suspendContext) { suspendContext ->
                val debugProbesProxy = CoroutineDebugProbesProxy(suspendContext)
                val coroutineCache = debugProbesProxy.dumpCoroutines()
                if (!coroutineCache.isOk()) {
                    val errorNode = ErrorNode("coroutine.view.fetching.error")
                    node.addChildren(XValueChildrenList.singleton(errorNode), true)
                    return@invokeInSuspendContext
                }

                val children = XValueChildrenList()
                val groups = coroutineCache.cache.groupBy { it.descriptor.dispatcher }
                for (dispatcher in groups.keys) {
                    children.add(CoroutineContainer(suspendContext, dispatcher ?: EMPTY_DISPATCHER_NAME, groups[dispatcher]))
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
      val suspendContext: SuspendContextImpl,
      private val groupName: String,
      val coroutines: List<CoroutineInfoData>?
    ) : RendererContainer(renderer.renderGroup(groupName)) {
        override fun computeChildren(node: XCompositeNode) {
            val children = XValueChildrenList()
            coroutines?.forEach {
                children.add(FramesContainer(it, suspendContext))
            }

            if (children.size() > 0) {
                node.addChildren(children, true)
            } else {
                node.addChildren(XValueChildrenList.singleton(InfoNode("coroutine.view.fetching.not_found")), true)
            }

        }
    }

    inner class InfoNode(val error: String) : RendererContainer(renderer.renderInfoNode(error))

    inner class ErrorNode(val error: String) : RendererContainer(renderer.renderErrorNode(error))

    inner class FramesContainer(
        private val infoData: CoroutineInfoData,
        private val suspendContext: SuspendContextImpl
    ) : RendererContainer(renderer.render(infoData)) {
        override fun computeChildren(node: XCompositeNode) {
            debugProcess.invokeInSuspendContext(suspendContext) { suspendContext ->
                val children = XValueChildrenList()
                val doubleFrameList = CoroutineFrameBuilder.build(infoData, suspendContext)
                doubleFrameList?.frames?.forEach {
                    children.add(CoroutineFrameValue(it))
                }
                doubleFrameList?.creationFrames?.let {
                    children.add(CreationFramesContainer(it))
                }
                node.addChildren(children, true)
            }
        }
    }

    inner class CreationFramesContainer(
        private val creationFrames: List<CreationCoroutineStackFrameItem>
    ) : RendererContainer(renderer.renderCreationNode()) {
        override fun computeChildren(node: XCompositeNode) {
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

private fun DebugProcessImpl.invokeInSuspendContext(
    suspendContext: SuspendContextImpl,
    command: (SuspendContextImpl) -> Unit
): Unit =
    managerThread.invoke(object : SuspendContextCommandImpl(suspendContext) {
        override fun getPriority() =
            PrioritizedTask.Priority.NORMAL

        override fun contextAction(suspendContext: SuspendContextImpl): Unit =
            command(suspendContext)
    })
