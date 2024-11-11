package org.jetbrains.plugins.textmate.regex;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.openapi.diagnostic.LoggerRt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.ExecutorsKt;
import org.jcodings.specific.UTF8Encoding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joni.Matcher;
import org.joni.Option;
import org.joni.Regex;
import org.joni.WarnCallback;
import org.joni.exception.JOniException;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public final class RegexFacade {
  private static final Regex FAILED_REGEX = new Regex("^$", UTF8Encoding.INSTANCE);
  private static final LoggerRt LOGGER = LoggerRt.getInstance(RegexFacade.class);

  @NotNull
  private final Regex myRegex;
  private final boolean hasGMatch;

  private final ThreadLocal<LastMatch> matchResult = new ThreadLocal<>();

  private RegexFacade(@NotNull String regexString) {
    byte[] bytes = regexString.getBytes(StandardCharsets.UTF_8);
    Regex regex;
    try {
      regex = new Regex(bytes, 0, bytes.length, Option.CAPTURE_GROUP, UTF8Encoding.INSTANCE, WarnCallback.NONE);
    }
    catch (JOniException e) {
      LOGGER.info(String.format("Failed to parse textmate regex '%s' with %s: %s", regexString, e.getClass().getName(), e.getMessage()));
      regex = FAILED_REGEX;
    }
    hasGMatch = regexString.contains("\\G");
    myRegex = regex;
  }

  public MatchData match(StringWithId string, @Nullable Runnable checkCancelledCallback) {
    return match(string, 0, 0, true, checkCancelledCallback);
  }

  public MatchData match(@NotNull StringWithId string,
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

  private static void checkMatched(MatchData match, StringWithId string) {
    if (match.matched() && match.byteOffset().end > string.bytes.length) {
      throw new IllegalStateException(
        "Match data out of bounds: " + match.byteOffset().start + " > " + string.bytes.length + "\n" +
        new String(string.bytes, StandardCharsets.UTF_8));
    }
  }

  private static final Cache<String, RegexFacade> REGEX_CACHE = Caffeine.newBuilder()
    .maximumSize(100_000)
    .expireAfterAccess(1, TimeUnit.MINUTES)
    .executor(ExecutorsKt.asExecutor(Dispatchers.getDefault()))
    .build();

  @NotNull
  public static RegexFacade regex(@NotNull String regexString) {
    return REGEX_CACHE.get(regexString, RegexFacade::new);
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

