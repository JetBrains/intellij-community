package org.jetbrains.plugins.textmate.language

import org.jetbrains.plugins.textmate.Constants
import org.jetbrains.plugins.textmate.language.preferences.IndentationRules
import org.jetbrains.plugins.textmate.language.preferences.IndentationRules.Companion.empty
import org.jetbrains.plugins.textmate.language.preferences.ShellVariablesRegistry
import org.jetbrains.plugins.textmate.language.preferences.TextMateBracePair
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope
import org.jetbrains.plugins.textmate.plist.PListValue
import org.jetbrains.plugins.textmate.plist.Plist
import kotlin.jvm.JvmStatic

object PreferencesReadUtil {
  fun readPairs(pairsValue: PListValue?): Set<TextMateBracePair>? {
    if (pairsValue == null) {
      return null
    }

    return buildSet {
      val pairs = pairsValue.array
      for (pair in pairs) {
        val chars = pair.array
        if (chars.size == 2) {
          val left = chars[0].string
          val right = chars[1].string
          if (!left.isNullOrEmpty() && !right.isNullOrEmpty()) {
            add(TextMateBracePair(left, right))
          }
        }
      }
    }
  }

  private fun getPattern(name: String, from: Plist): String? {
    val value = from.getPlistValue(name)
    if (value == null) return null
    return value.string
  }

  fun loadIndentationRules(plist: Plist): IndentationRules {
    val rulesValue = plist.getPlistValue(Constants.INDENTATION_RULES)
    if (rulesValue == null) return empty()
    val rules = rulesValue.plist
    return IndentationRules(
      getPattern(Constants.INCREASE_INDENT_PATTERN, rules),
      getPattern(Constants.DECREASE_INDENT_PATTERN, rules),
      getPattern(Constants.INDENT_NEXT_LINE_PATTERN, rules),
      getPattern(Constants.UNINDENTED_LINE_PATTERN, rules)
    )
  }

  @JvmStatic
  fun readCommentPrefixes(
    registry: ShellVariablesRegistry,
    scope: TextMateScope
  ): TextMateCommentPrefixes {
    var lineCommentPrefix: String? = null
    var blockCommentPair: TextMateBlockCommentPair? = null
    var index = 1
    while (lineCommentPrefix == null || blockCommentPair == null) {
      val variableSuffix = if (index > 1) "_$index" else ""
      val start = registry.getVariableValue(Constants.COMMENT_START_VARIABLE + variableSuffix, scope)
      val end = registry.getVariableValue(Constants.COMMENT_END_VARIABLE + variableSuffix, scope)

      index++

      if (start == null) break
      if ((end == null || end.scopeSelector != start.scopeSelector) && lineCommentPrefix == null) {
        lineCommentPrefix = start.value
      }
      if ((end != null && end.scopeSelector == start.scopeSelector) && blockCommentPair == null) {
        blockCommentPair = TextMateBlockCommentPair(start.value, end.value)
      }
    }

    return TextMateCommentPrefixes(lineCommentPrefix, blockCommentPair)
  }
}