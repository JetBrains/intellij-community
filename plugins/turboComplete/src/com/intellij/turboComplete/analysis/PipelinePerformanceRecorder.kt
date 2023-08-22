package com.intellij.turboComplete.analysis

import com.intellij.completion.ml.performance.PerformanceTracker

class PipelinePerformanceRecorder : PerformanceRecorder<SinglePipelineRecorder>() {
  override val performanceMetricName = "turboComplete.pipeline"

  override fun createPipelineListener() = SinglePipelineRecorder()

  override fun capturePerformance(pipelineListener: SinglePipelineRecorder, tracker: PerformanceTracker) {
    pipelineListener.captureChronologyDurations()?.let { chronologyDurations ->
      tracker.addByKey("gen", chronologyDurations.generation)
      tracker.addByKey("rank", chronologyDurations.ranking)
    }
  }
}