/*
* Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
* Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
*/

package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.debugger.ui.tree.DebuggerTreeNode
import com.intellij.debugger.ui.tree.NodeManager
import com.intellij.debugger.ui.tree.ValueDescriptor
import com.intellij.debugger.ui.tree.render.ChildrenBuilder
import com.intellij.debugger.ui.tree.render.ClassRenderer
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener
import com.intellij.openapi.project.Project
import com.sun.jdi.*
import java.util.*

class KotlinClassRenderer : ClassRenderer() {
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
        val gettersFuture = DebuggerUtilsAsync.allMethods(refType)
            .thenApply { methods -> methods.getters().createNodes(value, parentDescriptor.project, evaluationContext, nodeManager) }
        DebuggerUtilsAsync.allFields(refType).thenCombine(gettersFuture) { fields, getterNodes ->
            if (fields.isEmpty() && getterNodes.isEmpty()) {
                builder.setChildren(listOf(nodeManager.createMessageNode(KotlinDebuggerCoreBundle.message("message.class.has.no.properties"))))
                return@thenCombine
            }

            createNodesToShow(fields, evaluationContext, parentDescriptor, nodeManager, nodeDescriptorFactory, value)
                .thenAccept { nodesToShow ->
                    if (nodesToShow.isEmpty()) {
                        setClassHasNoFieldsToDisplayMessage(builder, nodeManager)
                        builder.setChildren(getterNodes)
                        return@thenAccept
                    }
                    builder.setChildren(mergeNodesLists(nodesToShow, getterNodes))
                }
        }
    }

    override fun calcLabel(
        descriptor: ValueDescriptor,
        evaluationContext: EvaluationContext?,
        labelListener: DescriptorLabelListener
    ): String {
        val renderer = NodeRendererSettings.getInstance().toStringRenderer
        return renderer.calcLabel(descriptor, evaluationContext, labelListener)
    }

    override fun isApplicable(type: Type?): Boolean =
        type is ReferenceType && type !is ArrayType && type.isInKotlinSources()

    private fun mergeNodesLists(fieldNodes: List<DebuggerTreeNode>, getterNodes: List<DebuggerTreeNode>): List<DebuggerTreeNode> {
        val namesToIndex = getterNodes.withIndex().associate { it.value.descriptor.name to it.index }

        val result = ArrayList<DebuggerTreeNode>(fieldNodes.size + getterNodes.size)
        val added = BooleanArray(getterNodes.size)
        for (fieldNode in fieldNodes) {
            result.add(fieldNode)
            val name = fieldNode.descriptor.name.removeSuffix("\$delegate")
            namesToIndex[name]?.let { index ->
                result.add(getterNodes[index])
                added[index] = true
            }
        }

        result.addAll(getterNodes.filterIndexed { i, _ -> !added[i] })
        return result
    }

    private fun List<Method>.getters() =
        filter { method ->
            !method.isAbstract &&
            GetterDescriptor.GETTER_PREFIXES.any { method.name().startsWith(it) } &&
            method.name() != "getClass" &&
            method.argumentTypeNames().isEmpty() &&
            method.declaringType().isInKotlinSources() &&
            !DebuggerUtils.isSimpleGetter(method)
        }
        .distinctBy { it.name() }
        .toList()

    private fun List<Method>.createNodes(
        parentObject: ObjectReference,
        project: Project,
        evaluationContext: EvaluationContext,
        nodeManager: NodeManager
    ) = map { nodeManager.createNode(GetterDescriptor(parentObject, it, project), evaluationContext) }
}
