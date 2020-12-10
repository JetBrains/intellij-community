package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.ui.impl.watch.MessageDescriptor
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.debugger.ui.tree.DebuggerTreeNode
import com.intellij.debugger.ui.tree.NodeManager
import com.intellij.debugger.ui.tree.render.ChildrenBuilder
import com.intellij.debugger.ui.tree.render.ClassRenderer
import com.sun.jdi.*
import java.util.*
import java.util.concurrent.CompletableFuture

class KotlinClassRenderer : ClassRenderer() {
    // TODO: Add asynchronous collection of methods and remove code duplication
    override fun buildChildren(value: Value?, builder: ChildrenBuilder, evaluationContext: EvaluationContext) {
        DebuggerManagerThreadImpl.assertIsManagerThread()
        if (value !is ObjectReference) {
            builder.setChildren(emptyList())
            return
        }

        val parentDescriptor = builder.parentDescriptor as ValueDescriptorImpl
        val nodeManager = builder.nodeManager
        val nodeDescriptorFactory = builder.descriptorManager
        val refType = value.referenceType()
        DebuggerUtilsAsync.allFields(refType)
            .thenAccept { fields ->
                if (fields.isEmpty()) {
                    builder.setChildren(listOf(nodeManager.createMessageNode(MessageDescriptor.CLASS_HAS_NO_FIELDS.label)))
                    return@thenAccept
                }

                val fieldsToShow = fields.filter { shouldDisplay(evaluationContext, value, it) }
                if (fieldsToShow.isEmpty()) {
                    builder.setChildren(listOf(nodeManager.createMessageNode(JavaDebuggerBundle.message("message.node.class.no.fields.to.display"))))
                    return@thenAccept
                }

                val futures: Array<CompletableFuture<List<DebuggerTreeNode>>> = createNodesChunked(
                    fieldsToShow, evaluationContext, parentDescriptor, nodeManager, nodeDescriptorFactory, value
                )
                CompletableFuture.allOf(*futures).thenAccept {
                    val fieldNodes = futures.flatMap { it.join() }
                    val getterNodes = refType.allMethods().getters().createNodes(evaluationContext, parentDescriptor, nodeManager)
                    builder.setChildren(mergeNodesLists(fieldNodes, getterNodes))
                }
            }
    }

    override fun isApplicable(type: Type?): Boolean =
        type is ReferenceType && type !is ArrayType && type.isInKotlinSources()

    private fun mergeNodesLists(fieldNodes: List<DebuggerTreeNode>, getterNodes: List<DebuggerTreeNode>): List<DebuggerTreeNode> {
        val namesToIndex = getterNodes.withIndex().associate { it.value.descriptor.name to it.index }

        val result = ArrayList<DebuggerTreeNode>(fieldNodes.size + getterNodes.size)
        val added = BooleanArray(getterNodes.size)
        for (fieldNode in fieldNodes) {
            result.add(fieldNode)
            namesToIndex[fieldNode.descriptor.name]?.let {
                result.add(getterNodes[it])
                added[it] = true
            }
        }

        result.addAll(getterNodes.filterIndexed { i, _ -> !added[i] })
        return result
    }

    private fun List<Method>.getters() =
            filter {
                it.name().startsWith("get") && it.name() != "getClass" &&
                it.argumentTypeNames().isEmpty() && !DebuggerUtils.isSimpleGetter(it)
            }
            .distinctBy { it.name() }
            .toList()

    private fun List<Method>.createNodes(
        evaluationContext: EvaluationContext,
        parentDescriptor: ValueDescriptorImpl,
        nodeManager: NodeManager
    ) = map { nodeManager.createNode(GetterDescriptor(parentDescriptor, it, parentDescriptor.project), evaluationContext) }
}