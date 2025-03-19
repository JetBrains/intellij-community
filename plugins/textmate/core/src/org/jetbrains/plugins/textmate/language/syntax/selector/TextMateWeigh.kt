package org.jetbrains.plugins.textmate.language.syntax.selector

data class TextMateWeigh(val weigh: Int, val priority: Priority) : Comparable<TextMateWeigh> {
  enum class Priority {
    LOW,
    NORMAL,
    HIGH,
  }

  override fun compareTo(o: TextMateWeigh): Int {
    val priorityCompare = priority.compareTo(o.priority)
    if (priorityCompare != 0) {
      return priorityCompare
    }
    return weigh.compareTo(o.weigh)
  }

  companion object {
    val ZERO: TextMateWeigh = TextMateWeigh(0, Priority.LOW)
  }
}
