package com.intellij.textmate.joni

import org.jcodings.specific.UTF8Encoding
import org.jetbrains.plugins.textmate.regex.NotMatchingRegexFacade
import org.jetbrains.plugins.textmate.regex.RegexFacade
import org.jetbrains.plugins.textmate.regex.RegexFactory
import org.jetbrains.plugins.textmate.regex.TextMateString
import org.jetbrains.plugins.textmate.regex.TextMateStringImpl
import org.joni.Option
import org.joni.Regex
import org.joni.WarnCallback
import org.joni.exception.JOniException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class JoniRegexFactory : RegexFactory {
  companion object {
    private val LOGGER: Logger = LoggerFactory.getLogger(JoniRegexFactory::class.java)
  }

  override fun regex(pattern: CharSequence): RegexFacade {
    val patternString = pattern.toString()
    return try {
      // joni doesn't support disabling \A at search time, so a variant of the pattern
      // with \A replaced by an unmatchable character is compiled for searches
      // that must not treat the string start as the beginning of the text
      val regexWithoutBeginStringAnchor = replaceBeginStringAnchor(patternString)?.let { rewrittenPattern ->
        try {
          compileRegex(rewrittenPattern)
        }
        catch (e: JOniException) {
          LOGGER.info("Failed to parse textmate regex '{}' with {}: {}", rewrittenPattern, e::class.java.getName(), e.message)
          null
        }
      }
      JoniRegexFacade(compileRegex(patternString), regexWithoutBeginStringAnchor)
    }
    catch (e: JOniException) {
      LOGGER.info("Failed to parse textmate regex '{}' with {}: {}", patternString, e::class.java.getName(), e.message)
      NotMatchingRegexFacade
    }
  }

  private fun compileRegex(patternString: String): Regex {
    val bytes = patternString.encodeToByteArray()
    return Regex(bytes, 0, bytes.size, Option.CAPTURE_GROUP, UTF8Encoding.INSTANCE, WarnCallback.NONE)
  }

  /**
   * Replaces the `\A` escapes with an unmatchable character, keeping all other escapes intact.
   * Returns null when the pattern contains no `\A`.
   */
  private fun replaceBeginStringAnchor(patternString: String): String? {
    var hasAnchor = false
    val result = buildString(patternString.length) {
      var i = 0
      while (i < patternString.length) {
        val c = patternString[i]
        append(c)
        if (c == '\\' && i + 1 < patternString.length) {
          val next = patternString[i + 1]
          if (next == 'A') {
            append('\uFFFF')
            hasAnchor = true
          }
          else {
            append(next)
          }
          i++
        }
        i++
      }
    }
    return if (hasAnchor) result else null
  }

  override fun string(string: CharSequence): TextMateString {
    return TextMateStringImpl.fromString(string.toString())
  }
}