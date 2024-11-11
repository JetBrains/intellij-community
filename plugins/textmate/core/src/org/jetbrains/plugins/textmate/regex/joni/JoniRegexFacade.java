package org.jetbrains.plugins.textmate.regex.joni;

import com.intellij.openapi.diagnostic.LoggerRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.regex.MatchData;
import org.jetbrains.plugins.textmate.regex.RegexFacade;
import org.jetbrains.plugins.textmate.regex.TextMateString;
import org.joni.Matcher;
import org.joni.Option;
import org.joni.Regex;
import org.joni.exception.JOniException;

import java.nio.charset.StandardCharsets;

public final class JoniRegexFacade implements RegexFacade {
  private static final LoggerRt LOGGER = LoggerRt.getInstance(JoniRegexFacade.class);

  @NotNull
  private final Regex myRegex;
  private final boolean hasGMatch;

  private final ThreadLocal<LastMatch> matchResult = new ThreadLocal<>();

  public JoniRegexFacade(@NotNull Regex regex, boolean hasGMatch) {
    myRegex = regex;
    this.hasGMatch = hasGMatch;
  }

  @Override
  public @NotNull MatchData match(@NotNull TextMateString string, @Nullable Runnable checkCancelledCallback) {
    return match(string, 0, 0, true, checkCancelledCallback);
  }

  @Override
  public @NotNull MatchData match(@NotNull TextMateString string,
                                  int byteOffset,
                                  int gosOffset,
                                  boolean matchBeginOfString,
                                  @Nullable Runnable checkCancelledCallback) {
    gosOffset = gosOffset != byteOffset ? Integer.MAX_VALUE : byteOffset;
    int options = matchBeginOfString ? Option.NONE : Option.NOTBOS;

    LastMatch lastResult = matchResult.get();
    Object lastId = lastResult != null ? lastResult.lastId : null;
    int lastOffset = lastResult != null ? lastResult.lastOffset : Integer.MAX_VALUE;
    int lastGosOffset = lastResult != null ? lastResult.lastGosOffset : -1;
    int lastOptions = lastResult != null ? lastResult.lastOptions : -1;
    MatchData lastMatch = lastResult != null ? lastResult.lastMatch : MatchData.NOT_MATCHED;

    if (lastId == string.id && lastOffset <= byteOffset && lastOptions == options && (!hasGMatch || lastGosOffset == gosOffset)) {
      if (!lastMatch.matched() || lastMatch.byteOffset().start >= byteOffset) {
        checkMatched(lastMatch, string);
        return lastMatch;
      }
    }
    if (checkCancelledCallback != null) {
      checkCancelledCallback.run();
    }

    final Matcher matcher = myRegex.matcher(string.bytes);
    try {
      final int matchIndex = matcher.search(gosOffset, byteOffset, string.bytes.length, options);
      final MatchData matchData = matchIndex > -1 ? MatchData.fromRegion(matcher.getEagerRegion()) : MatchData.NOT_MATCHED;
      checkMatched(matchData, string);
      matchResult.set(new LastMatch(string.id, byteOffset, gosOffset, options, matchData));
      return matchData;
    }
    catch (JOniException
           // We catch AIOOBE here because of a bug in joni,
           // apparently the lengths of code units are not calculated correctly in UnicodeEncoding.mbcCaseFold
           | ArrayIndexOutOfBoundsException e) {
      LOGGER.info(String.format("Failed to parse textmate regex '%s' with %s: %s", string, e.getClass().getName(), e.getMessage()));
      return MatchData.NOT_MATCHED;
    }
  }

  private static void checkMatched(MatchData match, TextMateString string) {
    if (match.matched() && match.byteOffset().end > string.bytes.length) {
      throw new IllegalStateException(
        "Match data out of bounds: " + match.byteOffset().start + " > " + string.bytes.length + "\n" +
        new String(string.bytes, StandardCharsets.UTF_8));
    }
  }

  private static final class LastMatch {
    private final Object lastId;
    private final int lastOffset;
    private final int lastGosOffset;
    private final int lastOptions;
    private final MatchData lastMatch;

    private LastMatch(Object id, int offset, int gosOffset, int options, MatchData data) {
      lastId = id;
      lastOffset = offset;
      lastGosOffset = gosOffset;
      lastOptions = options;
      lastMatch = data;
    }
  }
}

