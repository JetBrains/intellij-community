package com.intellij.lang.properties.diff.data

/**
 * Represents a semi-open interval of lines, e.g. `[a, b)`.
 */
internal class SemiOpenLineRange(val startLine: Int, val endLine: Int) {
  init {
    if (startLine > endLine) {
      throw IllegalArgumentException("startLine $startLine > endLine $endLine")
    }
  }

  /**
   * Checks if the semi-opened range is empty.
   */
  val isEmpty: Boolean = startLine == endLine

  operator fun contains(other: SemiOpenLineRange): Boolean = other.startLine >= startLine && other.endLine <= endLine

  override fun toString(): String {
    return "Lines: [$startLine, $endLine)"
  }
}