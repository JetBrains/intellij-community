package com.intellij.ide.actions.searcheverywhere.ml.performance

internal class PerformanceTracker {
  private var startTime = -1L
  private var endTime = -1L

  private val isStarted: Boolean
    get() = startTime != -1L

  private val isFinished: Boolean
    get() = endTime != -1L

  private val isMeasurementReady: Boolean
    get() = isStarted && isFinished

  val timeElapsed: Int
    get() = if(isMeasurementReady) (endTime - startTime).toInt() else -1

  fun start() {
    startTime = System.currentTimeMillis()
    endTime = -1
  }

  fun stop() {
    if (isStarted && !isFinished) {
      endTime = System.currentTimeMillis()
    }
  }

  fun reset() {
    startTime = -1
    endTime = -1
  }
}
