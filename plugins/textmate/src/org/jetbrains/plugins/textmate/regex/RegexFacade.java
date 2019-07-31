package org.jetbrains.plugins.textmate.regex;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.ArrayUtilRt;
import org.jcodings.specific.UTF8Encoding;
import org.jetbrains.annotations.NotNull;
import org.joni.Matcher;
import org.joni.Option;
import org.joni.Regex;
import org.joni.exception.JOniException;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class RegexFacade {
  private static final Regex FAILED_REGEX = new Regex("^$", UTF8Encoding.INSTANCE);
  private static final Logger LOGGER = Logger.getInstance(RegexFacade.class);

  @NotNull
  private byte[] myRegexBytes;
  private Regex myRegex = null;

  private final ThreadLocal<LastMatch> matchResult = new ThreadLocal<>();

  private RegexFacade(@NotNull byte[] regexBytes) {
    myRegexBytes = regexBytes;
  }

  public MatchData match(StringWithId string) {
    return match(string, 0);
  }

  public MatchData match(@NotNull StringWithId string, int byteOffset) {
    LastMatch lastResult = matchResult.get();
    Object lastId = lastResult != null ? lastResult.lastId : null;
    int lastOffset = lastResult != null ? lastResult.lastOffset : Integer.MAX_VALUE;
    MatchData lastMatch = lastResult != null ? lastResult.lastMatch : MatchData.NOT_MATCHED;

    if (lastId == string.id && lastOffset <= byteOffset) {
      if (!lastMatch.matched() || lastMatch.byteOffset().getStartOffset() >= byteOffset) {
        checkMatched(lastMatch, string);
        return lastMatch;
      }
    }
    ProgressManager.checkCanceled();
    lastId = string.id;
    lastOffset = byteOffset;
    final Matcher matcher = getRegex().matcher(string.bytes);
    int matchIndex = matcher.search(byteOffset, string.bytes.length, Option.CAPTURE_GROUP);
    lastMatch = matchIndex > -1 ? MatchData.fromRegion(matcher.getEagerRegion()) : MatchData.NOT_MATCHED;
    checkMatched(lastMatch, string);
    matchResult.set(new LastMatch(lastId, lastOffset, lastMatch));
    return lastMatch;
  }

  private static void checkMatched(MatchData match, StringWithId string) {
    if (match.matched() && match.byteOffset().getEndOffset() > string.bytes.length) {
      throw new IllegalStateException(
        "Match data out of bounds: " + match.byteOffset().getStartOffset() + " > " + string.bytes.length + "\n" +
        new String(string.bytes, StandardCharsets.UTF_8));
    }
  }

  public Searcher searcher(byte[] stringBytes) {
    return new Searcher(stringBytes, getRegex().matcher(stringBytes, 0, stringBytes.length));
  }

  @NotNull
  private Regex getRegex() {
    if (myRegex == null) {
      try {
        myRegex = new Regex(myRegexBytes, 0, myRegexBytes.length, Option.CAPTURE_GROUP, UTF8Encoding.INSTANCE);
      }
      catch (JOniException e) {
        LOGGER.info("Failed to parse textmate regex", e);
        myRegex = FAILED_REGEX;
      }
      myRegexBytes = ArrayUtilRt.EMPTY_BYTE_ARRAY;
    }
    return myRegex;
  }

  private static final LoadingCache<String, RegexFacade> REGEX_CACHE = CacheBuilder.newBuilder().maximumSize(2048).build(
    CacheLoader.from((String regexString) -> new RegexFacade(Objects.requireNonNull(regexString).getBytes(StandardCharsets.UTF_8))));

  @NotNull
  public static RegexFacade regex(@NotNull String regexString) {
    try {
      return REGEX_CACHE.get(regexString);
    }
    catch (ExecutionException e) {
      return new RegexFacade(regexString.getBytes(StandardCharsets.UTF_8));
    }
  }

  private class LastMatch {
    private final Object lastId;
    private final int lastOffset;
    private final MatchData lastMatch;

    private LastMatch(Object id, int offset, MatchData data) {
      lastId = id;
      lastOffset = offset;
      lastMatch = data;
    }
  }
}

