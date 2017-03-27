package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.TraceElement
import com.intellij.debugger.streams.ui.LinkedValuesMapping
import com.intellij.debugger.streams.ui.TraceController
import com.intellij.debugger.streams.ui.ValueWithPosition
import com.intellij.debugger.streams.ui.ValuesPositionsListener
import java.awt.GridLayout
import javax.swing.JPanel

/**
 * @author Vitaliy.Bibaev
 */
class StreamTracesMappingView(
  evaluationContext: EvaluationContextImpl,
  prevController: TraceController,
  nextController: TraceController) : JPanel(GridLayout(1, 3)) {
  init {
    val resolve = resolve(prevController)

    val mappingPane = MappingPane(resolve.valuesBefore, resolve.mapping)

    val listener = object : ValuesPositionsListener {
      override fun valuesPositionsChanged() {
        mappingPane.repaint()
      }
    }

    val beforeView = PositionsAwareCollectionView("Before", evaluationContext, resolve.valuesBefore)
    prevController.register(beforeView)
    beforeView.addValuesPositionsListener(listener)

    val afterView = PositionsAwareCollectionView("After", evaluationContext, resolve.valuesAfter)
    nextController.register(afterView)
    afterView.addValuesPositionsListener(listener)

    add(beforeView)
    add(mappingPane)
    add(afterView)
  }

  fun resolve(prev: TraceController): LinkedValuesWithPositions {
    val prevTrace = prev.resolvedTrace

    val pool = mutableMapOf<TraceElement, ValueWithPositionImpl>()
    fun getValue(element: TraceElement): ValueWithPositionImpl = pool.computeIfAbsent(element, ::ValueWithPositionImpl)

    val prevValues = mutableListOf<ValueWithPositionImpl>()
    val nextValues = mutableSetOf<ValueWithPositionImpl>()
    val mapping = mutableMapOf<ValueWithPositionImpl, MutableSet<ValueWithPositionImpl>>()

    for (element in prevTrace.values) {
      val prevValue = getValue(element)
      prevValues += prevValue
      for (nextElement in prevTrace.getNextValues(element)) {
        val nextValue = getValue(nextElement)
        nextValues += nextValue
        mapping.computeIfAbsent(prevValue, { mutableSetOf() }) += nextValue
        mapping.computeIfAbsent(nextValue, { mutableSetOf() }) += prevValue
      }
    }

    val resultMapping = mutableMapOf<ValueWithPosition, List<ValueWithPosition>>()
    for (key in mapping.keys) {
      resultMapping.put(key, mapping[key]!!.toList())
    }

    return LinkedValuesWithPositions(prevValues, nextValues.toList(), object : LinkedValuesMapping {
      override fun getLinkedValues(value: ValueWithPosition): List<ValueWithPosition>? {
        return resultMapping[value]
      }
    })
  }

  data class LinkedValuesWithPositions(
    val valuesBefore: List<ValueWithPositionImpl>,
    val valuesAfter: List<ValueWithPositionImpl>,
    val mapping: LinkedValuesMapping
  )
}