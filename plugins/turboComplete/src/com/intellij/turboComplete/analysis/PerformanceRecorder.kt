package com.intellij.turboComplete.analysis

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl
import com.intellij.completion.ml.performance.PerformanceMetricCollector
import com.intellij.completion.ml.performance.PerformanceMetricCollectorFactory
import com.intellij.completion.ml.performance.PerformanceTracker
import com.intellij.completion.ml.storage.MutableLookupStorage

abstract class PerformanceRecorder<T : PipelineListener>
  : DelegatingPipelineListener,
    PerformanceMetricCollectorFactory {

  private lateinit var currentListener: T

  abstract fun createPipelineListener(): T

  abstract fun capturePerformance(pipelineListener: T, tracker: PerformanceTracker)

  override val delegatedListeners: MutableList<PipelineListener>
    get() = mutableListOf(currentListener)

  override fun onInitialize(parameters: CompletionParameters) {
    val lookup = LookupManagerImpl.getActiveLookup(parameters.editor) as? LookupImpl ?: return
    val lookupStorage = MutableLookupStorage.get(lookup)!!
    lookupStorage.performanceTracker.addMetricCollector(this)
    currentListener = createPipelineListener()
    super.onInitialize(parameters)
  }

  override fun createMetricCollector(tracker: PerformanceTracker): PerformanceMetricCollector {
    return CompletionKindMetricsCollector(tracker)
  }

  inner class CompletionKindMetricsCollector(private val tracker: PerformanceTracker) : PerformanceMetricCollector {
    override fun onFinishCollecting() {
      capturePerformance(currentListener, tracker)
    }
  }
}