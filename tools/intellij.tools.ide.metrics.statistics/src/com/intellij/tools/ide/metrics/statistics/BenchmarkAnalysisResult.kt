package com.intellij.tools.ide.metrics.statistics

data class BenchmarkAnalysisResult(
  val history: Perfolizer.Sample?,
  val current: Perfolizer.Sample?,
  val verdict: BenchmarkVerdict,
  val muteReasons: List<String>,
  val change: Perfolizer.Collation,
  val canRetry: Boolean
) {
  fun withMuteReasons(additionalMuteReasons: List<String>) =
    BenchmarkAnalysisResult(history, current, verdict, muteReasons + additionalMuteReasons, change, canRetry)
}
