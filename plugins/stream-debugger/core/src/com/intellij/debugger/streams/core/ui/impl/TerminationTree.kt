package com.intellij.debugger.streams.core.ui.impl

import com.intellij.debugger.streams.core.trace.CollectionTreeBuilder
import com.intellij.debugger.streams.core.trace.DebuggerCommandLauncher
import com.intellij.debugger.streams.core.trace.GenericEvaluationContext
import com.intellij.debugger.streams.core.trace.TraceElement
import com.intellij.debugger.streams.core.trace.Value
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.util.ObjectUtils
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener
import com.intellij.xdebugger.impl.ui.tree.nodes.RestorableStateNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

class TerminationTree(
  streamResult : Value,
  traceElements: List<TraceElement>,
  launcher: DebuggerCommandLauncher,
  context: GenericEvaluationContext,
  private val myBuilder: CollectionTreeBuilder,
  @Suppress("CanBeParameter") private val debugName: String,
) : CollectionTree(traceElements, context, myBuilder, debugName) {

  private val NULL_MARKER: Any = ObjectUtils.sentinel("CollectionTree.NULL_MARKER")

  init {
    val root = XValueNodeImpl(this, null, "root", MyValueRoot(streamResult, context))
    setRoot(root, false)
    root.isLeaf = false

    val key2TraceElements = traceElements.groupBy { myBuilder.getKey(it, NULL_MARKER) }
    val key2Index: MutableMap<Any, Int> = HashMap(key2TraceElements.size + 1)

    addTreeListener(object : XDebuggerTreeListener {
      override fun nodeLoaded(node: RestorableStateNode, name: String) {
        val listener: XDebuggerTreeListener = this
        if (node is XValueContainerNode<*>) {
          val container = (node as XValueContainerNode<*>).valueContainer
          if (myBuilder.isSupported(container)) {
            launcher.launchDebuggerCommand {
              val key = myBuilder.getKey(container, NULL_MARKER)
              withContext(Dispatchers.EDT) {
                val elements = key2TraceElements[key]
                val nextIndex = key2Index.getOrDefault(key, -1) + 1
                if (elements != null && nextIndex < elements.size) {
                  val element = elements[nextIndex]
                  myValue2Path[element] = node.path
                  myPath2Value[node.path] = element
                  key2Index[key] = nextIndex
                }
                if (myPath2Value.size == traceElements.size) {
                  //NOTE(Korovin): This will not be called if we have a big list of items and it's loaded partially
                  //If missing repaints, we need to replace this logic to some flow/debounce coroutine and repaint after a batch of nodes
                  removeTreeListener(listener)
                  yield()
                  repaint()
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
  }

  private inner class MyValueRoot(private val myValue: Value, private val myContext: GenericEvaluationContext) : XValue() {
    override fun computeChildren(node: XCompositeNode) {
      val children = XValueChildrenList()
      children.add(myBuilder.createXNamedValue(myValue, myContext))
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
