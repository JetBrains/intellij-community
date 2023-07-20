package com.intellij.turboComplete.analysis

import com.intellij.completion.ml.performance.PerformanceTracker

class KindPerformanceRecorder : PerformanceRecorder<SingleLifetimeKindsRecorder>() {
  override val performanceMetricName = "turboComplete.kindPerf"

  override fun createPipelineListener() = SingleLifetimeKindsRecorder()

  override fun capturePerformance(pipelineListener: SingleLifetimeKindsRecorder, tracker: PerformanceTracker) {
    val completionKindLifetimes = pipelineListener.captureCompletionKindLifetimeDurations()
    completionKindLifetimes.entries.forEach { (completionKind, performance) ->
      tracker.addByKey("init.${completionKind.name}", performance.created)
      performance.executed?.let { tracker.addByKey("run.${completionKind.name}", it) }
      performance.executedAsFirst?.let { tracker.addByKey("runFirst.${completionKind.name}", it) }
    }
  }
}