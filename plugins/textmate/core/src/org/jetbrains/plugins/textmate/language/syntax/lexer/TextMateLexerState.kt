package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.Constants
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh
import org.jetbrains.plugins.textmate.regex.MatchData
import org.jetbrains.plugins.textmate.regex.TextMateByteOffset
import org.jetbrains.plugins.textmate.regex.TextMateString
import org.jetbrains.plugins.textmate.regex.byteOffset

class TextMateLexerState(
  val syntaxRule: SyntaxNodeDescriptor,
  val matchData: MatchData,
  val priorityMatch: TextMateWeigh.Priority,
  /**
   * offset in the line where state was emitted. used for local loop protection only
   */
  val enterByteOffset: TextMateByteOffset,
  line: TextMateString?,
) {

  /**
   * Identity of the line the rule was matched against; distinguishes equal matches on different lines.
   * Only the identity is retained: the state outlives the [line], which may be disposed
   * as soon as the line is processed, see [TextMateString.close].
   */
  private val stringId: Any? = if (matchData.matched) line?.id else null

  val matchedEOL: Boolean = matchData.matched && line != null && matchData.byteRange().end.offset == line.bytesLength

  /**
   * Texts of the groups captured by the rule's begin/while match, extracted eagerly because
   * the state outlives the [line] they are matched on. Non-null only when the rule's end/while patterns
   * contain back-references to substitute, see [SyntaxMatchUtils.replaceGroupsWithMatchDataInRegex].
   */
  val capturedTexts: List<CharSequence>? =
    if (matchData.matched && line != null &&
        (syntaxRule.hasBackReference(Constants.StringKey.END) || syntaxRule.hasBackReference(Constants.StringKey.WHILE))) {
      SyntaxMatchUtils.capturedTexts(line, matchData)
    }
    else {
      null
    }

  private val hashcode: Int = run {
    var result = 1
    result = 31 * result + syntaxRule.hashCode()
    result = 31 * result + matchData.hashCode()
    result = 31 * result + priorityMatch.hashCode()
    result = 31 * result + stringId.hashCode()
    result
  }

  override fun toString(): String {
    return "TextMateLexerState{" +
           "syntaxRule=" + syntaxRule +
           ", matchData=" + matchData +
           '}'
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null) return false
    val state = other as TextMateLexerState
    return syntaxRule == state.syntaxRule &&
           matchData == state.matchData &&
           priorityMatch == state.priorityMatch &&
           stringId === state.stringId
  }

  override fun hashCode(): Int {
    return hashcode
  }

  companion object {
    fun notMatched(syntaxRule: SyntaxNodeDescriptor): TextMateLexerState {
      return TextMateLexerState(syntaxRule, MatchData.NOT_MATCHED, TextMateWeigh.Priority.NORMAL, 0.byteOffset(), null)
    }
  }
}
