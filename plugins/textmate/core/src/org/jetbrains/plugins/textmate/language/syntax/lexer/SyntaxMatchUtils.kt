package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.regex.MatchData
import org.jetbrains.plugins.textmate.regex.TextMateString

object SyntaxMatchUtils {

  /**
   * Extracts the texts of all groups captured by [matchData] from [matchingString].
   * Unlike [matchingString], the returned texts can be retained after the string is disposed.
   *
   * Groups that didn't match anything are represented by empty strings, and so are the groups
   * captured beyond the string: [matchData] may capture more than [matchingString] covers
   * when a rule inside a capture is matched against the line cut at the end of the capture.
   */
  fun capturedTexts(matchingString: TextMateString, matchData: MatchData): List<CharSequence> {
    return (0..<matchData.count()).map { group ->
      val byteRange = matchData.byteRange(group)
      if (byteRange.isEmpty || byteRange.end.offset > matchingString.bytesLength) "" else matchingString.subSequenceByByteRange(byteRange)
    }
  }

  /**
   * Replaces parts like \1 or \20 in string parameter with the captured group texts.
   *
   *
   * E.g., given string "\1-\2" and capturedTexts consisting of a full match and two groups: "first" and "second",
   * then string "first-second" will be returned.
   *
   * @param string        string pattern
   * @param capturedTexts texts of the captured groups to replace the group-references with, see [capturedTexts]
   * @return string with replaced group-references
   */
  fun replaceGroupsWithMatchDataInRegex(
    string: CharSequence,
    capturedTexts: List<CharSequence>?,
  ): String {
    if (capturedTexts == null) {
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
          if (hasGroupIndex) {
            // references to non-existing groups are replaced with an empty string
            if (capturedTexts.size > groupIndex) {
              append(BACK_REFERENCE_REPLACEMENT_REGEX.replace(capturedTexts[groupIndex], "\\\\$0"))
            }
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
