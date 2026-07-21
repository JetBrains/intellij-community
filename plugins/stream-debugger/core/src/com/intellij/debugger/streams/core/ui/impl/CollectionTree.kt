// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.ui.impl

import com.intellij.debugger.streams.core.trace.CollectionTreeBuilder
import com.intellij.debugger.streams.core.trace.DebuggerCommandLauncher
import com.intellij.debugger.streams.core.trace.GenericEvaluationContext
import com.intellij.debugger.streams.core.trace.TraceElement
import com.intellij.debugger.streams.core.trace.Value
import com.intellij.debugger.streams.core.ui.PaintingListener
import com.intellij.debugger.streams.core.ui.TraceContainer
import com.intellij.debugger.streams.core.ui.ValuesSelectionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.xdebugger.impl.actions.XDebuggerActions
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.tree.TreePath

/**
 * @author Vitaliy.Bibaev
 */
abstract class CollectionTree protected constructor(
  traceElements: List<TraceElement>,
  context: GenericEvaluationContext,
  protected val collectionTreeBuilder: CollectionTreeBuilder,
  @Suppress("unused") private val debugName: String,
) : XDebuggerTree(context.project, collectionTreeBuilder.getEditorsProvider(), null, XDebuggerActions.INSPECT_TREE_POPUP_GROUP, null),
    TraceContainer {

  protected val value2Path: MutableMap<TraceElement, TreePath> = HashMap()
  protected val path2Value: MutableMap<TreePath, TraceElement> = HashMap()

  private val expectedValueCount: Int = traceElements.size

  private var highlighted: Set<TreePath> = emptySet()
  private val selectionDispatcher = EventDispatcher.create(ValuesSelectionListener::class.java)
  private val paintingDispatcher = EventDispatcher.create(PaintingListener::class.java)

  private var ignoreInternalSelectionEvents = false
  private var ignoreExternalSelectionEvents = false

  private val highlightBackground: JBColor = JBColor.lazy {
    ColorUtil.toAlpha(UIUtil.getTreeSelectionBackground(true), if (JBColor.isBright()) 75 else 100)
  }

  init {
    addTreeSelectionListener {
      if (!ignoreInternalSelectionEvents) {
        val selectedItems = TreeUtil.collectSelectedPaths(this)
          .map { getTopPath(it) }
          .mapNotNull { path2Value[it] }
        fireSelectionChanged(selectedItems)
      }
    }

    setSelectionRow(0)
    expandNodesOnLoad { it === root }
  }

  override fun isFileColorsEnabled(): Boolean = true

  override fun getFileColorForPath(path: TreePath): Color? {
    return if (isPathHighlighted(path)) highlightBackground else UIUtil.getTreeBackground()
  }

  override fun clearSelection() {
    ignoreInternalSelectionEvents = true
    super.clearSelection()
    ignoreInternalSelectionEvents = false
  }

  fun getRectByValue(element: TraceElement): Rectangle? {
    val path = value2Path[element] ?: return null
    return getPathBounds(path)
  }

  override fun highlight(elements: List<TraceElement>) {
    clearSelection()

    highlightValues(elements)
    tryScrollTo(elements)

    updatePresentation()
  }

  override fun select(elements: List<TraceElement>) {
    val paths = Array(elements.size) { value2Path[elements[it]] }

    select(paths)
    highlightValues(elements)

    if (paths.isNotEmpty()) {
      scrollPathToVisible(paths[0])
    }

    updatePresentation()
  }

  override fun addSelectionListener(listener: ValuesSelectionListener) {
    // TODO: dispose?
    selectionDispatcher.addListener(listener)
  }

  override fun highlightedExists(): Boolean = !isSelectionEmpty || highlighted.isNotEmpty()

  abstract fun getItemsCount(): Int

  fun addPaintingListener(listener: PaintingListener) {
    paintingDispatcher.addListener(listener)
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    paintingDispatcher.multicaster.componentPainted()
  }

  private fun select(paths: Array<TreePath?>) {
    if (ignoreExternalSelectionEvents) {
      return
    }

    ignoreInternalSelectionEvents = true
    selectionModel.selectionPaths = paths
    ignoreInternalSelectionEvents = false
  }

  private fun fireSelectionChanged(selectedItems: List<TraceElement>) {
    ignoreExternalSelectionEvents = true
    selectionDispatcher.multicaster.selectionChanged(selectedItems)
    ignoreExternalSelectionEvents = false
  }

  private fun tryScrollTo(elements: List<TraceElement>) {
    val rows = elements.mapNotNull { value2Path[it] }.map { getRowForPath(it) }.sorted().toIntArray()
    if (rows.isEmpty()) {
      return
    }

    if (isShowing) {
      val bestVisibleArea = optimizeRowsCountInVisibleRect(rows)
      val visibleRect = this.visibleRect
      val notVisibleHighlightedRowExists = rows.any { !visibleRect.intersects(getRowBounds(it)) }
      if (notVisibleHighlightedRowExists) {
        scrollRectToVisible(bestVisibleArea)
      }
    }
    else {
      // Use slow path if component hidden
      scrollPathToVisible(getPathForRow(rows[0]))
    }
  }

  private fun optimizeRowsCountInVisibleRect(rows: IntArray): Rectangle {
    // a simple scan-line algorithm to find an optimal subset of visible rows (maximum)
    val visibleRect = this.visibleRect
    val height = visibleRect.height

    class Result {
      var top = 0
      var bot = 0

      fun count(): Int = bot - top
    }

    var topIndex = 0
    var bottomIndex = 1
    var rowBounds = getRowBounds(rows[topIndex]) ?: return visibleRect
    var topY = rowBounds.y

    val result = Result()
    while (bottomIndex < rows.size) {
      val nextY = getRowBounds(rows[bottomIndex]).y
      while (nextY - topY > height) {
        topIndex++
        rowBounds = getRowBounds(rows[topIndex]) ?: return visibleRect
        topY = rowBounds.y
      }

      if (bottomIndex - topIndex > result.count()) {
        result.top = topIndex
        result.bot = bottomIndex
      }

      bottomIndex++
    }

    var y = getRowBounds(rows[result.top]).y
    if (y > visibleRect.y) {
      val botBounds = getRowBounds(rows[result.bot])
      y = botBounds.y + botBounds.height - visibleRect.height
    }
    return Rectangle(visibleRect.x, y, visibleRect.width, height)
  }

  private fun highlightValues(elements: List<TraceElement>) {
    highlighted = elements.mapNotNull { value2Path[it] }.toSet()
  }

  private fun updatePresentation() {
    revalidate()
    repaint()
  }

  fun isHighlighted(traceElement: TraceElement): Boolean {
    val path = value2Path[traceElement] ?: return false
    return isPathHighlighted(path)
  }

  private fun isPathHighlighted(path: TreePath): Boolean {
    return highlighted.contains(path) || isPathSelected(path)
  }

  private fun getTopPath(path: TreePath): TreePath {
    var current: TreePath? = path
    while (current != null && !path2Value.containsKey(current)) {
      current = current.parentPath
    }

    return current ?: path
  }

  /**
   * `childrenLoaded` is used because unlike `nodeLoaded` it keeps the collection order.
   * `nodeLoaded` fires in presentation-completion order, which is non-deterministic and breaks the
   * value-to-row mapping when several values share one JVM object (e.g. cached `Integer`s).
   *
   * Children may arrive one by one, so they are accumulated and passed to [bind] only once the batch is complete.
   * [bind] fills [value2Path]/[path2Value] and must call `onBound` when done.
   * `onBound` repaints and, once all expected children are loaded, unsubscribes the listener.
   */
  protected fun collectValueNodesOnLoad(
    isValuesHolder: (XDebuggerTreeNode) -> Boolean,
    bind: (newNodes: List<XValueContainerNode<*>>, onBound: () -> Unit) -> Unit,
  ) {
    addTreeListener(object : XDebuggerTreeListener {
      private var batch = ArrayList<XValueContainerNode<*>>()
      private var loadedCount = 0

      // Invoked on EDT so all the calls are serialized
      override fun childrenLoaded(node: XDebuggerTreeNode, children: List<XValueContainerNode<*>>, last: Boolean) {
        if (!isValuesHolder(node)) {
          return
        }
        children
          .filter { collectionTreeBuilder.isSupported(it.valueContainer) }
          .toCollection(batch)

        if (!last || batch.isEmpty()) {
          return
        }

        val newNodes = batch
        batch = ArrayList()
        loadedCount += newNodes.size
        val allChildrenLoaded = loadedCount >= expectedValueCount

        bind(newNodes) {
          scheduleRepaint()
          if (allChildrenLoaded) {
            removeTreeListener(this)
          }
        }
      }
    })
  }

  /**
   * Repaints on the next EDT tick so the tree can finish laying out newly inserted/updated rows before the
   * mapping lines (which read row bounds via [getRectByValue]) are drawn.
   */
  private fun scheduleRepaint() {
    ApplicationManager.getApplication().invokeLater { repaint() }
  }

  companion object {
    @JvmStatic
    fun create(
      streamResult: Value?,
      traceElements: List<TraceElement>,
      debuggerCommandLauncher: DebuggerCommandLauncher,
      evaluationContext: GenericEvaluationContext,
      collectionTreeBuilder: CollectionTreeBuilder,
      debugName: String,
    ): CollectionTree {
      return if (streamResult == null) {
        IntermediateTree(traceElements, evaluationContext, collectionTreeBuilder, debugName)
      }
      else {
        TerminationTree(streamResult, traceElements, debuggerCommandLauncher, evaluationContext, collectionTreeBuilder, debugName)
      }
    }
  }
}
