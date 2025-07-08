package com.intellij.textmate.joni

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
import kotlin.time.Duration.Companion.milliseconds

class JoniRegexFacade(private val myRegex: Regex) : RegexFacade {
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
    val gosOffset = if (matchBeginPosition) byteOffset else Int.MAX_VALUE
    val options = if (matchBeginString) Option.NONE else Option.NOTBOS

    checkCancelledCallback?.run()

    val matcher = myRegex.matcher(string.bytes, 0, string.bytes.size, MATCHING_TIMEOUT)
    try {
      val matchIndex = matcher.search(gosOffset, byteOffset, string.bytes.size, options)
      return when (matchIndex) {
        org.joni.Matcher.FAILED -> {
          NOT_MATCHED
        }
        org.joni.Matcher.INTERRUPTED -> {
          LOGGER.info("Matching regex was interrupted on string: {}", string.bytes.decodeToString())
          NOT_MATCHED
        }
        else -> {
          matchData(matcher.eagerRegion).also {
            checkMatched(it, string)
          }
        }
      }
    }
    catch (e: JOniException) {
      LOGGER.info("Failed to match textmate regex '{}' with {}: {}", string.bytes.decodeToString(), e.javaClass.getName(), e.message)
      return NOT_MATCHED
    }
    catch (e: ArrayIndexOutOfBoundsException) {
      LOGGER.info("Failed to match textmate regex '{}' with {}: {}", string.bytes.decodeToString(), e.javaClass.getName(), e.message)
      return NOT_MATCHED
    }
  }

  override fun close() {
  }


  companion object {
    private val MATCHING_TIMEOUT = 300.milliseconds.inWholeNanoseconds
    private val LOGGER: Logger = LoggerFactory.getLogger(JoniRegexFacade::class.java)

    private fun checkMatched(match: MatchData, string: TextMateString) {
      check(!(match.matched && match.byteRange().end > string.bytes.size)) {
        "Match data out of bounds: " + match.byteRange().start + " > " + string.bytes.size + "\n" + String(string.bytes,
                                                                                                           Charsets.UTF_8)
      }
    }

    private fun matchData(matchedRegion: Region?): MatchData {
      if (matchedRegion != null) {
        val offsets = IntArray(matchedRegion.numRegs * 2)
        for (i in 0..<matchedRegion.numRegs) {
          val startIndex = i * 2
          offsets[startIndex] = matchedRegion.getBeg(i)
          offsets[startIndex + 1] = matchedRegion.getEnd(i)
        }
        return MatchData(true, offsets)
      }
      return NOT_MATCHED
    }
  }
}

