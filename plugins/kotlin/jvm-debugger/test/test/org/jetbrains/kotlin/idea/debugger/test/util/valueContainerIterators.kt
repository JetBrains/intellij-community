// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.test.util

import com.intellij.debugger.ui.impl.watch.NodeDescriptorProvider
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.xdebugger.XDebuggerTestUtil
import com.intellij.xdebugger.XTestValueNode
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueContainer
import com.intellij.xdebugger.frame.XValuePlace
import org.jetbrains.org.objectweb.asm.Type
import java.util.*

abstract class ValueContainerIterator(container: XValueContainer) : Iterator<XValueContainer> {
    private companion object {
        private val OPAQUE_TYPE_DESCRIPTORS = listOf(String::class.java).map { Type.getType(it).descriptor }
    }

    protected val queue = LinkedList<XValueContainer>(collectChildren(container))

    override fun hasNext() = queue.isNotEmpty()

    protected fun collectChildren(container: XValueContainer): List<XValue> {
        if (container.childrenCanBeCollected()) {
            return XDebuggerTestUtil.collectChildren(container)
        }
        return emptyList()
    }

    private fun XValueContainer.childrenCanBeCollected() =
        when (this) {
            is XStackFrame -> true
            is XValue -> mightHaveChildren()
            is NodeDescriptorProvider -> descriptor.isExpandable
            else -> false
        }

    private fun XValue.mightHaveChildren(): Boolean {
        val node = XTestValueNode()
        computePresentation(node, XValuePlace.TREE)
        node.waitFor(XDebuggerTestUtil.TIMEOUT_MS.toLong())
        val descriptor = if (this is NodeDescriptorProvider) descriptor else null
        return node.myHasChildren
                && (descriptor == null || descriptor.isExpandable)
                && !(descriptor is ValueDescriptorImpl && isOpaqueValue(descriptor))
    }

    private fun isOpaqueValue(descriptor: ValueDescriptorImpl): Boolean {
        val type = descriptor.type ?: return false
        return type.signature() in OPAQUE_TYPE_DESCRIPTORS
    }
}

class ValueContainerIteratorImpl(container: XValueContainer) : ValueContainerIterator(container) {
    override fun next(): XValueContainer = queue.pop()
}

class RecursiveValueContainerIterator(container: XValueContainer) : ValueContainerIterator(container) {
    override fun next(): XValueContainer {
        val nextContainer = queue.pop()
        queue.addAll(collectChildren(nextContainer))
        return nextContainer
    }
}

operator fun XValueContainer.iterator() = ValueContainerIteratorImpl(this)

fun XValueContainer.recursiveIterator() = RecursiveValueContainerIterator(this)
