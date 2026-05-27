// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.ui.impl

import com.intellij.debugger.streams.core.trace.CollectionTreeBuilder
import com.intellij.debugger.streams.core.trace.DebuggerCommandLauncher
import com.intellij.debugger.streams.core.trace.GenericEvaluationContext
import com.intellij.debugger.streams.core.trace.TraceElement
import com.intellij.debugger.streams.core.ui.LinkedValuesMapping
import com.intellij.debugger.streams.core.ui.TraceController
import com.intellij.debugger.streams.core.ui.ValueWithPosition
import com.intellij.debugger.streams.core.ui.ValuesPositionsListener
import java.awt.Component
import java.awt.GridLayout
import javax.swing.JPanel

/**
 * @author Vitaliy.Bibaev
 */
open class FlatView(controllers: List<TraceController>, launcher: DebuggerCommandLauncher, context: GenericEvaluationContext, builder: CollectionTreeBuilder,
                    debugName: String)
  : JPanel(GridLayout(1, 2 * controllers.size - 1)) {
  private val myPool = mutableMapOf<TraceElement, ValueWithPositionImpl>()

  init {
    assert(controllers.isNotEmpty())
    var prevMappingPane: MappingPane? = null
    var lastValues: List<ValueWithPositionImpl>? = null
    for ((index, controller) in controllers.subList(0, controllers.size - 1).withIndex()) {
      val (valuesBefore, valuesAfter, mapping) = controller.resolve(controllers[index + 1])
      val nextCall = controller.nextCall ?: error("intermediate state should know about next call")
      val mappingPane = MappingPane(nextCall.tabTitle, nextCall.tabTooltip, valuesBefore, mapping, controller)

      val tree = CollectionTree.create(controller.getStreamResult(), valuesBefore.map { it.traceElement }, launcher, context, builder, "${debugName}FlatView#controller#${index}")
      val view = PositionsAwareCollectionView(tree, valuesBefore)
      controller.register(view)
      view.addValuesPositionsListener(object : ValuesPositionsListener {
        override fun valuesPositionsChanged() {
          mappingPane.repaint()
        }
      })

      val prevMapping = prevMappingPane
      if (prevMapping != null) {
        view.addValuesPositionsListener(object : ValuesPositionsListener {
          override fun valuesPositionsChanged() {
            prevMapping.repaint()
          }
        })

        prevMapping.addMouseWheelListener { e -> view.instancesTree.dispatchEvent(e) }
      }

      mappingPane.addMouseWheelListener { e -> view.instancesTree.dispatchEvent(e) }

      add(view)
      add(mappingPane)

      prevMappingPane = mappingPane
      lastValues = valuesAfter
    }

    lastValues?.let {
      val lastController = controllers.last()
      val tree = CollectionTree.create(lastController.getStreamResult(), it.map { it.traceElement }, launcher, context, builder, "${debugName}FlatView#lastValues#CollectionTree")
      val view = PositionsAwareCollectionView(tree, it)
      lastController.register(view)
      view.addValuesPositionsListener(object : ValuesPositionsListener {
        override fun valuesPositionsChanged() {
          prevMappingPane?.repaint()
        }
      })

      prevMappingPane?.let { it.addMouseWheelListener { e -> view.instancesTree.dispatchEvent(e) } }

      add(view)
    }

    if (controllers.size == 1) {
      val controller = controllers[0]
      val tree = CollectionTree.create(controller.getStreamResult(), controller.trace, launcher, context, builder, "FlatView#singleController")
      val view = CollectionView(tree)
      add(view)
      controller.register(view)
    }
  }

  final override fun add(component: Component): Component = super.add(component)

  private fun getValue(element: TraceElement): ValueWithPositionImpl = myPool.computeIfAbsent(element, ::ValueWithPositionImpl)

  private fun TraceController.resolve(nextController: TraceController): LinkedValuesWithPositions {
    val prevValues = mutableListOf<ValueWithPositionImpl>()
    val mapping = mutableMapOf<ValueWithPositionImpl, MutableSet<ValueWithPositionImpl>>()

    for (element in trace) {
      val prevValue = getValue(element)
      prevValues += prevValue
      for (nextElement in getNextValues(element)) {
        val nextValue = getValue(nextElement)
        mapping.computeIfAbsent(prevValue, { mutableSetOf() }) += nextValue
        mapping.computeIfAbsent(nextValue, { mutableSetOf() }) += prevValue
      }
    }

    val resultMapping = mutableMapOf<ValueWithPosition, List<ValueWithPosition>>()
    for (key in mapping.keys) {
      resultMapping.put(key, mapping[key]!!.toList())
    }

    return LinkedValuesWithPositions(prevValues, nextController.trace.map { getValue(it) }, object : LinkedValuesMapping {
      override fun getLinkedValues(value: ValueWithPosition): List<ValueWithPosition>? {
        return resultMapping[value]
      }
    })
  }

  private data class LinkedValuesWithPositions(
    val valuesBefore: List<ValueWithPositionImpl>,
    val valuesAfter: List<ValueWithPositionImpl>,
    val mapping: LinkedValuesMapping
  )
}