package org.jetbrains.plugins.textmate.regex.joni

import org.jetbrains.plugins.textmate.regex.MatchData
import org.jetbrains.plugins.textmate.regex.MatchData.Companion.fromRegion
import org.jetbrains.plugins.textmate.regex.RegexFacade
import org.jetbrains.plugins.textmate.regex.TextMateString
import org.joni.Option
import org.joni.Regex
import org.joni.exception.JOniException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets

class JoniRegexFacade(private val myRegex: Regex, private val hasGMatch: Boolean) : RegexFacade {
  private val matchResult = ThreadLocal<LastMatch?>()

  override fun match(string: TextMateString, checkCancelledCallback: Runnable?): MatchData {
    return match(string, 0, 0, true, checkCancelledCallback)
  }

  override fun match(
    string: TextMateString,
    byteOffset: Int,
    gosOffset: Int,
    matchBeginOfString: Boolean,
    checkCancelledCallback: Runnable?
  ): MatchData {
    var gosOffset = gosOffset
    gosOffset = if (gosOffset != byteOffset) Int.Companion.MAX_VALUE else byteOffset
    val options = if (matchBeginOfString) Option.NONE else Option.NOTBOS

    val lastResult = matchResult.get()
    val lastId = lastResult?.lastId
    val lastOffset = lastResult?.lastOffset ?: Int.Companion.MAX_VALUE
    val lastGosOffset = lastResult?.lastGosOffset ?: -1
    val lastOptions = lastResult?.lastOptions ?: -1
    val lastMatch = lastResult?.lastMatch ?: MatchData.NOT_MATCHED

    if (lastId === string.id && lastOffset <= byteOffset && lastOptions == options && (!hasGMatch || lastGosOffset == gosOffset)) {
      if (!lastMatch.matched || lastMatch.byteOffset().start >= byteOffset) {
        checkMatched(lastMatch, string)
        return lastMatch
      }
    }
    checkCancelledCallback?.run()

    val matcher = myRegex.matcher(string.bytes)
    try {
      val matchIndex = matcher.search(gosOffset, byteOffset, string.bytes.size, options)
      val matchData = if (matchIndex > -1) fromRegion(matcher.eagerRegion) else MatchData.NOT_MATCHED
      checkMatched(matchData, string)
      matchResult.set(LastMatch(string.id, byteOffset, gosOffset, options, matchData))
      return matchData
    }
    catch (e: JOniException) {
      LOGGER.info("Failed to parse textmate regex '{}' with {}: {}", string, e.javaClass.getName(), e.message)
      return MatchData.NOT_MATCHED
    }
    catch (e: ArrayIndexOutOfBoundsException) {
      LOGGER.info("Failed to parse textmate regex '{}' with {}: {}", string, e.javaClass.getName(), e.message)
      return MatchData.NOT_MATCHED
    }
  }

  private class LastMatch(
    val lastId: Any?,
    val lastOffset: Int,
    val lastGosOffset: Int,
    val lastOptions: Int,
    val lastMatch: MatchData
  )

  companion object {
    private val LOGGER: Logger = LoggerFactory.getLogger(JoniRegexFacade::class.java)

    private fun checkMatched(match: MatchData, string: TextMateString) {
      check(!(match.matched && match.byteOffset().end > string.bytes.size)) {
        "Match data out of bounds: " + match.byteOffset().start + " > " + string.bytes.size + "\n" + String(string.bytes,
                                                                                                            StandardCharsets.UTF_8)
      }
    }
  }
}

