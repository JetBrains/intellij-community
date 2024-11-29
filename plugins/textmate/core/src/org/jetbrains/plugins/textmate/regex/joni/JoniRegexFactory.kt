package org.jetbrains.plugins.textmate.regex.joni

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.diagnostic.LoggerRt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import org.jcodings.specific.UTF8Encoding
import org.jetbrains.plugins.textmate.regex.RegexFacade
import org.jetbrains.plugins.textmate.regex.RegexFactory
import org.jetbrains.plugins.textmate.regex.TextMateString
import org.joni.Option
import org.joni.Regex
import org.joni.WarnCallback
import org.joni.exception.JOniException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class JoniRegexFactory : RegexFactory {
  companion object {
    private val REGEX_CACHE: Cache<String, JoniRegexFacade> = Caffeine.newBuilder()
      .maximumSize(100_000)
      .expireAfterAccess(1, TimeUnit.MINUTES)
      .executor(Dispatchers.Default.asExecutor())
      .build()

    private val FAILED_REGEX: Regex = Regex("^$", UTF8Encoding.INSTANCE)
    private val LOGGER: LoggerRt = LoggerRt.getInstance(JoniRegexFactory::class.java)

  }

  override fun regex(regexString: String): RegexFacade {
    return REGEX_CACHE.get(regexString) { s ->
      val bytes = regexString.toByteArray(StandardCharsets.UTF_8)
      val regex = try {
        Regex(bytes, 0, bytes.size, Option.CAPTURE_GROUP, UTF8Encoding.INSTANCE, WarnCallback.NONE)
      }
      catch (e: JOniException) {
        LOGGER.info(String.format("Failed to parse textmate regex '%s' with %s: %s", regexString, e::class.java.getName(), e.message))
        FAILED_REGEX
      }
      JoniRegexFacade(regex, regexString.contains("\\G"))
    }
  }

  override fun string(string: CharSequence): TextMateString {
    return TextMateString(string)
  }
}