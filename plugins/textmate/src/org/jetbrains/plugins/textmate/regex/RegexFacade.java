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
import java.util.concurrent.ExecutionException;

public class RegexFacade {
  private static final Regex FAILED_REGEX = new Regex("^$", UTF8Encoding.INSTANCE);
  private static final Logger LOGGER = Logger.getInstance(RegexFacade.class);

  @NotNull
  private byte[] myRegexBytes;
  private Regex myRegex = null;

  private RegexFacade(@NotNull byte[] regexBytes) {
    myRegexBytes = regexBytes;
  }

  public MatchData match(String string) {
    return match(string, 0);
  }

  public MatchData match(String string, int at) {
    ProgressManager.checkCanceled();
    byte[] stringBytes = string.getBytes(StandardCharsets.UTF_8);
    int byteOffset = RegexUtil.byteOffsetByCharOffset(string, at);

    final Matcher matcher = getRegex().matcher(stringBytes);
    int matchIndex = matcher.search(byteOffset, stringBytes.length, Option.CAPTURE_GROUP);
    return matchIndex > -1
           ? MatchData.fromRegion(string, stringBytes, matcher.getEagerRegion())
           : MatchData.NOT_MATCHED;
  }

  public Searcher searcher(String string) {
    byte[] stringBytes = string.getBytes(StandardCharsets.UTF_8);
    return new Searcher(string, stringBytes, getRegex().matcher(stringBytes, 0, stringBytes.length));
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
    CacheLoader.from((String regexString) -> new RegexFacade(regexString.getBytes(StandardCharsets.UTF_8))));

  @NotNull
  public static RegexFacade regex(@NotNull String regexString) {
    try {
      return REGEX_CACHE.get(regexString);
    }
    catch (ExecutionException e) {
      return new RegexFacade(regexString.getBytes(StandardCharsets.UTF_8));
    }
  }
}

