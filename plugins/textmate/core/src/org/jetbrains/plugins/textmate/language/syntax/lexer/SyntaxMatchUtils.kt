package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.regex.MatchData
import org.jetbrains.plugins.textmate.regex.TextMateString
import java.util.regex.Pattern

internal object SyntaxMatchUtils {

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
            val range = matchData.byteOffset(groupIndex)
            val replacement = String(matchingString.bytes, range.start, range.length, Charsets.UTF_8)
            append(BACK_REFERENCE_REPLACEMENT_REGEX.matcher(replacement).replaceAll("\\\\$0"))
            charIndex = digitIndex
            continue
          }
        }
        append(c)
        charIndex++
      }
    }
  }

  private val BACK_REFERENCE_REPLACEMENT_REGEX: Pattern = Pattern.compile("[\\-\\\\{}*+?|^$.,\\[\\]()#\\s]")
  private val CAPTURE_GROUP_REGEX: Pattern = Pattern.compile("\\$([0-9]+)|\\$\\{([0-9]+):/(downcase|upcase)}")

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
    val matcher = CAPTURE_GROUP_REGEX.matcher(string)
    return buildString {
      var lastPosition = 0
      while (matcher.find()) {
        val groupIndex = (if (matcher.group(1) != null) matcher.group(1) else matcher.group(2)).toIntOrNull() ?: -1
        if (groupIndex >= 0 && matchData.count() > groupIndex) {
          append(string, lastPosition, matcher.start())
          val range = matchData.byteOffset(groupIndex)
          val capturedText = String(matchingString.bytes, range.start, range.length, Charsets.UTF_8)
          val replacement = capturedText.trimStart('.')
          val command = matcher.group(3)
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
          lastPosition = matcher.end()
        }
      }
      if (lastPosition < string.length) {
        append(string.subSequence(lastPosition, string.length))
      }
    }
  }
}
