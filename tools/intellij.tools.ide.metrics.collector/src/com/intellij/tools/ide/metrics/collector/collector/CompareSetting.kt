package com.intellij.tools.ide.metrics.collector.collector

import com.intellij.tools.ide.metrics.collector.analysis.Conclusion

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