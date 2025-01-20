package org.jetbrains.plugins.textmate.regex.joni

import org.jetbrains.plugins.textmate.regex.MatchData
import org.jetbrains.plugins.textmate.regex.MatchData.Companion.NOT_MATCHED
import org.jetbrains.plugins.textmate.regex.RegexFacade
import org.jetbrains.plugins.textmate.regex.TextMateString
import org.joni.Option
import org.joni.Regex
import org.joni.Region
import org.joni.exception.JOniException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.max

class JoniRegexFacade(private val myRegex: Regex, private val hasGMatch: Boolean) : RegexFacade {
  private val matchResult = ThreadLocal<LastMatch?>()

  override fun match(string: TextMateString, checkCancelledCallback: Runnable?): MatchData {
    return match(string, 0, true, true, checkCancelledCallback)
  }

  override fun match(
    string: TextMateString,
    byteOffset: Int,
    matchBeginPosition: Boolean,
    matchBeginString: Boolean,
    checkCancelledCallback: Runnable?
  ): MatchData {
    val gosOffset = if (matchBeginPosition) byteOffset else Int.Companion.MAX_VALUE
    val options = if (matchBeginString) Option.NONE else Option.NOTBOS

    val lastResult = matchResult.get()
    val lastId = lastResult?.lastId
    val lastOffset = lastResult?.lastOffset ?: Int.Companion.MAX_VALUE
    val lastGosOffset = lastResult?.lastGosOffset ?: -1
    val lastOptions = lastResult?.lastOptions ?: -1
    val lastMatch = lastResult?.lastMatch ?: NOT_MATCHED

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
      val matchData = if (matchIndex > -1) matchData(matcher.eagerRegion) else NOT_MATCHED
      checkMatched(matchData, string)
      matchResult.set(LastMatch(string.id, byteOffset, gosOffset, options, matchData))
      return matchData
    }
    catch (e: JOniException) {
      LOGGER.info("Failed to parse textmate regex '{}' with {}: {}", string, e.javaClass.getName(), e.message)
      return NOT_MATCHED
    }
    catch (e: ArrayIndexOutOfBoundsException) {
      LOGGER.info("Failed to parse textmate regex '{}' with {}: {}", string, e.javaClass.getName(), e.message)
      return NOT_MATCHED
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
                                                                                                            Charsets.UTF_8)
      }
    }

    private fun matchData(matchedRegion: Region?): MatchData {
      if (matchedRegion != null) {
        val offsets = IntArray(matchedRegion.numRegs * 2)
        for (i in 0..<matchedRegion.numRegs) {
          val startIndex = i * 2
          offsets[startIndex] = max(matchedRegion.getBeg(i), 0)
          offsets[startIndex + 1] = max(matchedRegion.getEnd(i), 0)
        }
        return MatchData(true, offsets)
      }
      return NOT_MATCHED
    }
  }
}

