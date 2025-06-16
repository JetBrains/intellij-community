// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.memory.utils.InstanceJavaValue
import com.intellij.debugger.streams.core.trace.CollectionTreeBuilder
import com.intellij.debugger.streams.core.trace.GenericEvaluationContext
import com.intellij.debugger.streams.core.trace.TraceElement
import com.intellij.debugger.streams.core.trace.Value
import com.intellij.debugger.streams.trace.impl.JavaEvaluationContext
import com.intellij.debugger.streams.trace.impl.JvmValue
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl
import com.intellij.debugger.ui.impl.watch.MessageDescriptor
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl
import com.intellij.debugger.ui.tree.NodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XValueContainer
import org.jetbrains.java.debugger.JavaDebuggerEditorsProvider

class JavaCollectionTreeBuilder(val project: Project) : CollectionTreeBuilder {
  private val nodeManager = MyNodeManager(project)

  private class MyNodeManager(project: Project?) : NodeManagerImpl(project, null) {
    override fun createNode(descriptor: NodeDescriptor, evaluationContext: EvaluationContext): DebuggerTreeNodeImpl {
      return DebuggerTreeNodeImpl(null, descriptor)
    }

    override fun createMessageNode(descriptor: MessageDescriptor): DebuggerTreeNodeImpl {
      return DebuggerTreeNodeImpl(null, descriptor)
    }

    override fun createMessageNode(message: String): DebuggerTreeNodeImpl {
      return DebuggerTreeNodeImpl(null, MessageDescriptor(message))
    }
  }

  override fun createXNamedValue(value: Value?, evaluationContext: GenericEvaluationContext): XNamedValue {
    val jvmValue : com.sun.jdi.Value? = (value as? JvmValue)?.value
    val valueDescriptor = PrimitiveValueDescriptor(project, jvmValue)
    return InstanceJavaValue(valueDescriptor, (evaluationContext as JavaEvaluationContext).context, nodeManager)
  }

  override fun getKey(container: XValueContainer, nullMarker: Any): Any {
    val jvmValue: com.sun.jdi.Value? = (container as JavaValue).descriptor.getValue()
    return jvmValue ?: nullMarker
  }

  override fun getKey(traceElement: TraceElement, nullMarker: Any): Any {
    val value: Value? = traceElement.getValue()
    val jvmValue : com.sun.jdi.Value? = (value as? JvmValue)?.value
    return jvmValue ?: nullMarker
  }

  override fun getEditorsProvider(): XDebuggerEditorsProvider {
    return JavaDebuggerEditorsProvider()
  }

  override fun isSupported(container: XValueContainer): Boolean = container is JavaValue
}