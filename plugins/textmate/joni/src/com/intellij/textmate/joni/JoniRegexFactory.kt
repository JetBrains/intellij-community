package com.intellij.textmate.joni

import org.jcodings.specific.UTF8Encoding
import org.jetbrains.plugins.textmate.regex.NotMatchingRegexFacade
import org.jetbrains.plugins.textmate.regex.RegexFacade
import org.jetbrains.plugins.textmate.regex.RegexFactory
import org.jetbrains.plugins.textmate.regex.TextMateString
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
    val bytes = pattern.toString().toByteArray(Charsets.UTF_8)
    return try {
      val regex = Regex(bytes, 0, bytes.size, Option.CAPTURE_GROUP, UTF8Encoding.INSTANCE, WarnCallback.NONE)
      JoniRegexFacade(regex)
    }
    catch (e: JOniException) {
      LOGGER.info("Failed to parse textmate regex '{}' with {}: {}", pattern, e::class.java.getName(), e.message)
      NotMatchingRegexFacade
    }
  }

  override fun string(string: CharSequence): TextMateString {
    return TextMateString.fromString(string.toString())
  }
}