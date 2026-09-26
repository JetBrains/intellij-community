package com.intellij.debugger.streams.core.ui.impl

import com.intellij.debugger.streams.core.trace.CollectionTreeBuilder
import com.intellij.debugger.streams.core.trace.GenericEvaluationContext
import com.intellij.debugger.streams.core.trace.TraceElement
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueContainer
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl

class IntermediateTree(
  traceElements: List<TraceElement>,
  context: GenericEvaluationContext,
  builder: CollectionTreeBuilder,
  debugName: String,
) : CollectionTree(traceElements, context, builder, debugName) {
  private val myXValue2TraceElement: MutableMap<XValueContainer, TraceElement> = HashMap()

  private val itemsCount : Int = traceElements.size

  init {
    val root = XValueNodeImpl(this, null, "root", MyTraceElementsRoot(traceElements, context))
    setRoot(root, false)
    root.isLeaf = false

    collectValueNodesOnLoad({ it === root }) { newNodes, onBound ->
      for (node in newNodes) {
        val element = myXValue2TraceElement[node.valueContainer] ?: continue
        value2Path[element] = node.path
        path2Value[node.path] = element
      }
      onBound()
    }
  }

  private inner class MyTraceElementsRoot(
    private val myTraceElements: List<TraceElement>,
    private val myEvaluationContext: GenericEvaluationContext,
  ) : XValue() {
    override fun computeChildren(node: XCompositeNode) {
      val children = XValueChildrenList()
      for (value in myTraceElements) {
        val namedValue = collectionTreeBuilder.createXNamedValue(value.value, myEvaluationContext)
        myXValue2TraceElement[namedValue] = value
        children.add(namedValue)
      }

      node.addChildren(children, true)
    }

    override fun computePresentation(node: XValueNode, place: XValuePlace) {
      node.setPresentation(null, "", "", true)
    }
  }

  override fun getItemsCount(): Int {
    return itemsCount
  }
}
