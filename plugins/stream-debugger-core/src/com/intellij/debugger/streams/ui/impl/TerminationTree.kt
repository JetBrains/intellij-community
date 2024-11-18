package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.streams.trace.CollectionTreeBuilder
import com.intellij.debugger.streams.trace.EvaluationContextWrapper
import com.intellij.debugger.streams.trace.TraceElement
import com.intellij.debugger.streams.trace.Value
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.ObjectUtils
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener
import com.intellij.xdebugger.impl.ui.tree.nodes.RestorableStateNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import one.util.streamex.StreamEx

class TerminationTree(
  streamResult : Value,
  traceElements: List<TraceElement>,
  evaluationContextWrapper: EvaluationContextWrapper,
  private val myBuilder: CollectionTreeBuilder,
  @Suppress("CanBeParameter") private val debugName: String,
) : CollectionTree(traceElements, evaluationContextWrapper, myBuilder, debugName) {

  private val NULL_MARKER: Any = ObjectUtils.sentinel("CollectionTree.NULL_MARKER")

  init {
    val root = XValueNodeImpl(this, null, "root", MyValueRoot(streamResult, evaluationContextWrapper))
    setRoot(root, false)
    root.isLeaf = false

    val key2TraceElements =
      StreamEx.of(traceElements).groupingBy { element: TraceElement? -> myBuilder.getKey(element!!, NULL_MARKER) }
    val key2Index: MutableMap<Any, Int> = java.util.HashMap(key2TraceElements.size + 1)

    addTreeListener(object : XDebuggerTreeListener {
      override fun nodeLoaded(node: RestorableStateNode, name: String) {
        val listener: XDebuggerTreeListener = this
        if (node is XValueContainerNode<*>) {
          val container = (node as XValueContainerNode<*>).valueContainer
          if (myBuilder.isSupported(container)) {
            evaluationContextWrapper.scheduleDebuggerCommand {
              val key = myBuilder.getKey(container, NULL_MARKER)
              ApplicationManager.getApplication().invokeLater {
                val elements = key2TraceElements[key]
                val nextIndex = key2Index.getOrDefault(key, -1) + 1
                if (elements != null && nextIndex < elements.size) {
                  val element = elements[nextIndex]
                  myValue2Path[element] = node.path
                  myPath2Value[node.path] = element
                  key2Index[key] = nextIndex
                }
                if (myPath2Value.size == traceElements.size) {
                  removeTreeListener(listener)
                  ApplicationManager.getApplication().invokeLater { repaint() }
                }
              }
            }
          }
        }
      }
    })

    addTreeListener(object : XDebuggerTreeListener {
      override fun nodeLoaded(node: RestorableStateNode, name: String) {
        val path = node.path
        if (path.pathCount == 2) {
          ApplicationManager.getApplication().invokeLater { expandPath(path) }
          removeTreeListener(this)
        }
      }
    })

    setSelectionRow(0)
    expandNodesOnLoad { node -> node === root }
  }

  private inner class MyValueRoot(private val myValue: Value, private val myEvaluationContext: EvaluationContextWrapper) : XValue() {
    override fun computeChildren(node: XCompositeNode) {
      val children = XValueChildrenList()
      children.add(myBuilder.createXNamedValue(myValue, myEvaluationContext))
      node.addChildren(children, true)
    }

    override fun computePresentation(node: XValueNode, place: XValuePlace) {
      node.setPresentation(null, "", "", true)
    }
  }

  override fun getItemsCount(): Int {
    return 1
  }
}
