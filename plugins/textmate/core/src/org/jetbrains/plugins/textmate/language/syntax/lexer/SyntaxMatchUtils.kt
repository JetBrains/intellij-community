package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.regex.MatchData
import org.jetbrains.plugins.textmate.regex.TextMateString

object SyntaxMatchUtils {

  /**
   * Replaces parts like \1 or \20 in string parameter with group captures from matchData.
   *
   *
   * E.g., given string "\1-\2" and matchData consists of two groups: "first" and "second"
   * then string "first-second" will be returned.
   *
   * @param string         string pattern
   * @param matchingString matched matchingString
   * @param matchData      matched data with captured groups for replacement
   * @return string with replaced group-references
   */
  fun replaceGroupsWithMatchDataInRegex(
    string: CharSequence,
    matchingString: TextMateString?,
    matchData: MatchData
  ): String {
    if (matchingString == null || !matchData.matched) {
      return string.toString()
    }
    return buildString {
      var charIndex = 0
      val length = string.length
      while (charIndex < length) {
        val c = string[charIndex]
        if (c == '\\') {
          var hasGroupIndex = false
          var groupIndex = 0
          var digitIndex = charIndex + 1
          while (digitIndex < length) {
            val digit = string[digitIndex].digitToIntOrNull() ?: -1
            if (digit == -1) {
              break
            }
            hasGroupIndex = true
            groupIndex = groupIndex * 10 + digit
            digitIndex++
          }
          if (hasGroupIndex && matchData.count() > groupIndex) {
            val byteRange = matchData.byteRange(groupIndex)
            val replacement = if (byteRange.isEmpty) "" else matchingString.subSequenceByByteRange(byteRange).toString()
            append(BACK_REFERENCE_REPLACEMENT_REGEX.replace(replacement, "\\\\$0"))
            charIndex = digitIndex
            continue
          }
        }
        append(c)
        charIndex++
      }
    }
  }

  private val BACK_REFERENCE_REPLACEMENT_REGEX: Regex = Regex("[\\-\\\\{}*+?|^$.,\\[\\]()#\\s]")
  private val CAPTURE_GROUP_REGEX: Regex = Regex("\\$([0-9]+)|\\$\\{([0-9]+):/(downcase|upcase)}")

  /**
   * Replaces parts like $1 or $20 in string parameter with group captures from matchData,
   * specifically for [org.jetbrains.plugins.textmate.language.syntax.TextMateCapture].
   *
   *
   * Unlike [.replaceGroupsWithMatchDataInRegex],
   * this method also supports `upcase` and `downcase` command for the replacement.
   *
   * @param string         string pattern
   * @param matchingString matched matchingString
   * @param matchData      matched data with captured groups for replacement
   * @return string with replaced group-references
   */
  fun replaceGroupsWithMatchDataInCaptures(
    string: CharSequence,
    matchingString: TextMateString,
    matchData: MatchData
  ): CharSequence {
    if (!matchData.matched) {
      return string
    }
    var matcher = CAPTURE_GROUP_REGEX.find(string)
    return buildString {
      var lastPosition = 0
      while (matcher != null) {
        val groupIndex = (matcher.groups[1] ?: matcher.groups[2])?.value?.toIntOrNull() ?: -1
        if (groupIndex >= 0 && matchData.count() > groupIndex) {
          append(string, lastPosition, matcher.range.first)
          val byteRange = matchData.byteRange(groupIndex)
          val capturedText = if (byteRange.isEmpty) "" else matchingString.subSequenceByByteRange(byteRange).toString()
          val replacement = capturedText.trimStart('.')
          val command = matcher.groups[3]?.value
          when (command) {
            "downcase" -> {
              append(replacement.lowercase())
            }
            "upcase" -> {
              append(replacement.uppercase())
            }
            else -> {
              append(replacement)
            }
          }
          lastPosition = matcher.range.last + 1
        }
        matcher = matcher.next()
      }
      if (lastPosition < string.length) {
        append(string.subSequence(lastPosition, string.length))
      }
    }
  }
}
