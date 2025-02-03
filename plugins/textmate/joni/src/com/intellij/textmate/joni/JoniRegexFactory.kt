package com.intellij.textmate.joni

import org.jcodings.specific.UTF8Encoding
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
    private val FAILED_REGEX: Regex = Regex("^$", UTF8Encoding.INSTANCE)
    private val LOGGER: Logger = LoggerFactory.getLogger(JoniRegexFactory::class.java)
  }

  override fun regex(pattern: CharSequence): RegexFacade {
    val bytes = pattern.toString().toByteArray(Charsets.UTF_8)
    val regex = try {
      Regex(bytes, 0, bytes.size, Option.CAPTURE_GROUP, UTF8Encoding.INSTANCE, WarnCallback.NONE)
    }
    catch (e: JOniException) {
      LOGGER.info("Failed to parse textmate regex '{}' with {}: {}", pattern, e::class.java.getName(), e.message)
      FAILED_REGEX
    }
    return JoniRegexFacade(regex)
  }

  override fun string(string: CharSequence): TextMateString {
    return TextMateString.Companion.fromString(string.toString())
  }
}