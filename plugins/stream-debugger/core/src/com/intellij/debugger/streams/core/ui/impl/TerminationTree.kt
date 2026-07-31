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
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TerminationTree(
  streamResult : Value,
  traceElements: List<TraceElement>,
  launcher: DebuggerCommandLauncher,
  context: GenericEvaluationContext,
  builder: CollectionTreeBuilder,
  @Suppress("CanBeParameter") private val debugName: String,
) : CollectionTree(traceElements, context, builder, debugName) {

  private val NULL_MARKER: Any = ObjectUtils.sentinel("CollectionTree.NULL_MARKER")

  // Trace elements grouped by the value key, in trace order (groupBy preserves ordering).
  // Computed once and reused on every bind.
  // getKey(TraceElement) does not touch the debugger thread, so it is safe to build here.
  private val myKey2TraceElements: Map<Any, List<TraceElement>> =
    traceElements.groupBy { collectionTreeBuilder.getKey(it, NULL_MARKER) }

  // Running per-key counter, persisted across childrenLoaded batches so incremental binding continues in order.
  private val key2Index: MutableMap<Any, Int> = HashMap()

  init {
    val root = XValueNodeImpl(this, null, "root", MyValueRoot(streamResult, context))
    setRoot(root, false)
    root.isLeaf = false

    // Unlike IntermediateTree, the values here are the real result collection's children.
    // We map each tree node to a trace element by the value's JVM key.
    // And since several elements may share one JVM object (e.g. cached Integers),
    // duplicates within a key are disambiguated by their position in the collection order provided by childrenLoaded.
    collectValueNodesOnLoad({ it.parent === root }) { newNodes, onBound ->
      launcher.executeDebuggerCommand {
        // getKey(container) must run on the debugger thread
        val nodesWithKeys = newNodes.map { it to collectionTreeBuilder.getKey(it.valueContainer, NULL_MARKER) }
        withContext(Dispatchers.EDT) {
          for ((node, key) in nodesWithKeys) {
            val elements = myKey2TraceElements[key]
            val nextIndex = key2Index.getOrDefault(key, -1) + 1
            if (elements != null && nextIndex < elements.size) {
              val element = elements[nextIndex]
              value2Path[element] = node.path
              path2Value[node.path] = element
              key2Index[key] = nextIndex
            }
          }
          onBound()
        }
      }
    }

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
      children.add(collectionTreeBuilder.createXNamedValue(myValue, myContext))
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
