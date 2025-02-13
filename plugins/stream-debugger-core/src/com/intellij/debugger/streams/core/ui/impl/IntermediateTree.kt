package com.intellij.debugger.streams.core.ui.impl

import com.intellij.debugger.streams.core.trace.CollectionTreeBuilder
import com.intellij.debugger.streams.core.trace.DebuggerCommandLauncher
import com.intellij.debugger.streams.core.trace.TraceElement
import com.intellij.openapi.application.ApplicationManager
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener
import com.intellij.xdebugger.impl.ui.tree.nodes.RestorableStateNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl

class IntermediateTree(
  traceElements: List<TraceElement>,
  launcher: DebuggerCommandLauncher,
  private val myBuilder: CollectionTreeBuilder,
  debugName: String,
) : CollectionTree(traceElements, launcher, myBuilder, debugName) {
  private val myXValue2TraceElement: MutableMap<XValueContainer, TraceElement> = HashMap()

  private val itemsCount : Int = traceElements.size

  init {
    val root = XValueNodeImpl(this, null, "root", MyTraceElementsRoot(traceElements, launcher))
    setRoot(root, false)
    root.isLeaf = false

    addTreeListener(object : XDebuggerTreeListener {
      override fun nodeLoaded(node: RestorableStateNode, name: String) {
        val listener: XDebuggerTreeListener = this
        if (node is XValueContainerNode<*>) {
          val container = (node as XValueContainerNode<*>).valueContainer
          if (myBuilder.isSupported(container)) {
            ApplicationManager.getApplication().invokeLater {
              val element = myXValue2TraceElement[container]
              if (element != null) {
                myValue2Path[element] = node.path
                myPath2Value[node.path] = element
              }
              if (myPath2Value.size == traceElements.size) {
                myXValue2TraceElement.clear()
                removeTreeListener(listener)
                ApplicationManager.getApplication().invokeLater { repaint() }
              }
            }
          }
        }
      }
    })
  }

  private inner class MyTraceElementsRoot(
    private val myTraceElements: List<TraceElement>,
    private val myDebuggerCommandLauncher: DebuggerCommandLauncher,
  ) : XValue() {
    override fun computeChildren(node: XCompositeNode) {
      val children = XValueChildrenList()
      for (value in myTraceElements) {
        val namedValue = myBuilder.createXNamedValue(value.value, myDebuggerCommandLauncher)
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
