// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.JVMNameUtil
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
import java.util.concurrent.CompletableFuture
import java.util.function.Function

class KotlinClassRenderer : ClassRenderer() {
    init {
        setIsApplicableChecker(Function { type: Type? ->
            if (type is ReferenceType && type !is ArrayType) {
                return@Function type.isInKotlinSourcesAsync()
            }
            CompletableFuture.completedFuture(false)
        })
    }

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

    override fun isApplicable(type: Type?) = throw IllegalStateException("Should not be called")

    override fun shouldDisplay(context: EvaluationContext?, objInstance: ObjectReference, field: Field): Boolean {
        val referenceType = objInstance.referenceType()
        if (field.isStatic && referenceType.isKotlinObjectType()) {
            if (field.isInstanceFieldOfType(referenceType)) {
                return false
            }
            return true
        }
        return super.shouldDisplay(context, objInstance, field)
    }

    private fun mergeNodesLists(fieldNodes: List<DebuggerTreeNode>, getterNodes: List<DebuggerTreeNode>): List<DebuggerTreeNode> {
        val namesToIndex = getterNodes.withIndex().associate { it.value.descriptor.name to it.index }

        val result = ArrayList<DebuggerTreeNode>(fieldNodes.size + getterNodes.size)
        val added = BooleanArray(getterNodes.size)
        for (fieldNode in fieldNodes) {
            result.add(fieldNode)
            val name = fieldNode.descriptor.name.removeSuffix("\$delegate")
            val index = namesToIndex[name] ?: continue
            if (!added[index]) {
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
            method.argumentTypeNames().isEmpty() &&
            method.name() != "getClass" &&
            !method.name().endsWith("\$annotations") &&
            method.declaringType().isInKotlinSources() &&
            !method.isSimpleGetter() &&
            !method.isLateinitVariableGetter()
        }
        .distinctBy { it.name() }
        .toList()

    private fun List<Method>.createNodes(
        parentObject: ObjectReference,
        project: Project,
        evaluationContext: EvaluationContext,
        nodeManager: NodeManager
    ) = map { nodeManager.createNode(GetterDescriptor(parentObject, it, project), evaluationContext) }

    private fun ReferenceType.isKotlinObjectType() =
        containsInstanceField() && hasPrivateConstructor()

    private fun ReferenceType.containsInstanceField() =
        safeFields().any { it.isInstanceFieldOfType(this) }

    private fun ReferenceType.hasPrivateConstructor(): Boolean {
        val constructor = methodsByName(JVMNameUtil.CONSTRUCTOR_NAME).singleOrNull() ?: return false
        return constructor.isPrivate && constructor.argumentTypeNames().isEmpty()
    }

    private fun Field.isInstanceFieldOfType(type: Type) =
        isStatic && isFinal && name() == "INSTANCE" && safeType() == type
}
