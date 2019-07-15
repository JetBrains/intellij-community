package org.jetbrains.plugins.textmate.regex;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
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
  private final Regex myRegex;

  private Object lastId = null;
  private int lastOffset = Integer.MAX_VALUE;
  private MatchData lastMatch = MatchData.NOT_MATCHED;

  private RegexFacade(@NotNull String regexString) {
    byte[] bytes = regexString.getBytes(StandardCharsets.UTF_8);
    Regex regex;
    try {
      regex = new Regex(bytes, 0, bytes.length, Option.CAPTURE_GROUP, UTF8Encoding.INSTANCE);
    }
    catch (JOniException e) {
      LOGGER.info("Failed to parse textmate regex", e);
      regex = FAILED_REGEX;
    }
    myRegex = regex;
  }

  public MatchData match(StringWithId string) {
    return match(string, 0);
  }

  public MatchData match(@NotNull StringWithId string, int byteOffset) {
    if (lastId == string.id && lastOffset <= byteOffset) {
      if (!lastMatch.matched() || lastMatch.byteOffset().getStartOffset() >= byteOffset) {
        return lastMatch;
      }
    }
    ProgressManager.checkCanceled();
    lastId = string.id;
    lastOffset = byteOffset;
    final Matcher matcher = myRegex.matcher(string.bytes);
    int matchIndex = matcher.search(byteOffset, string.bytes.length, Option.CAPTURE_GROUP);
    lastMatch = matchIndex > -1 ? MatchData.fromRegion(matcher.getEagerRegion()) : MatchData.NOT_MATCHED;
    return lastMatch;
  }

  public Searcher searcher(byte[] stringBytes) {
    return new Searcher(stringBytes, myRegex.matcher(stringBytes, 0, stringBytes.length));
  }

  private static final LoadingCache<String, RegexFacade> REGEX_CACHE = CacheBuilder.newBuilder()
    .maximumSize(1024)
    .softValues()
    .build(CacheLoader.from(regexString -> new RegexFacade(Objects.requireNonNull(regexString))));

  @NotNull
  public static RegexFacade regex(@NotNull String regexString) {
    try {
      return REGEX_CACHE.get(regexString);
    }
    catch (ExecutionException e) {
      return new RegexFacade(regexString);
    }
  }
}

