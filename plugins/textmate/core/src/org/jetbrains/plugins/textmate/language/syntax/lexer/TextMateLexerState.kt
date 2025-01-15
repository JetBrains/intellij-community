package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh
import org.jetbrains.plugins.textmate.regex.MatchData
import org.jetbrains.plugins.textmate.regex.TextMateString
import java.util.*

class TextMateLexerState(
  val syntaxRule: SyntaxNodeDescriptor,
  val matchData: MatchData,
  val priorityMatch: TextMateWeigh.Priority,
  /**
   * offset in the line where state was emitted. used for local loop protection only
   */
  val enterByteOffset: Int,
  line: TextMateString?,
) {

  private val hashcode: Int = Objects.hash(syntaxRule, matchData, priorityMatch, stringId())
  val matchedEOL: Boolean = matchData.matched && line != null && matchData.byteOffset().end == line.bytes.size

  val string: TextMateString? = if (matchData.matched) line else null

  override fun toString(): String {
    return "TextMateLexerState{" +
           "syntaxRule=" + syntaxRule +
           ", matchData=" + matchData +
           '}'
  }

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o == null || javaClass != o.javaClass) return false
    val state = o as TextMateLexerState
    return syntaxRule == state.syntaxRule &&
           matchData == state.matchData && priorityMatch == state.priorityMatch && stringId() === state.stringId()
  }

  override fun hashCode(): Int {
    return hashcode
  }

  private fun stringId(): Any? {
    return string?.id
  }

  companion object {
    fun notMatched(syntaxRule: SyntaxNodeDescriptor): TextMateLexerState {
      return TextMateLexerState(syntaxRule, MatchData.NOT_MATCHED, TextMateWeigh.Priority.NORMAL, 0, null)
    }
  }
}
