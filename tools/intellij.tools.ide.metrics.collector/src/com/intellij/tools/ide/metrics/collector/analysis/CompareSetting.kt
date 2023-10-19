package com.intellij.tools.ide.metrics.collector.analysis

data class CompareSetting(
  val compareWithPrevResults: Boolean = false,
  val table: String = "",
  val notifierHook: ((Conclusion) -> Unit) = { }
) {
  companion object {
    val notComparing = CompareSetting(false)
    val withComparing = CompareSetting(true)
  }
}