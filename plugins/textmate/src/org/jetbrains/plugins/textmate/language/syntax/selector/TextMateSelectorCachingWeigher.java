package org.jetbrains.plugins.textmate.language.syntax.selector;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutionException;

public class TextMateSelectorCachingWeigher implements TextMateSelectorWeigher {
  @NotNull private final TextMateSelectorWeigher myOriginalWeigher;
  @NotNull private final Cache<String, TextMateWeigh> myCache = CacheBuilder.newBuilder()
    .initialCapacity(100).maximumSize(200).build();

  private static final Logger LOG = Logger.getInstance(TextMateSelectorCachingWeigher.class);

  public TextMateSelectorCachingWeigher(@NotNull TextMateSelectorWeigher originalWeigher) {
    myOriginalWeigher = originalWeigher;
  }

  @Override
  public TextMateWeigh weigh(@NotNull final String scopeSelector, @NotNull final String scope) {
    try {
      return myCache.get(createCacheKey(scopeSelector, scope), () -> myOriginalWeigher.weigh(scopeSelector, scope));
    }
    catch (ExecutionException e) {
      LOG.error("Can't perform lazy weigh for textmate selectors", e);
      return myOriginalWeigher.weigh(scopeSelector, scope);
    }
  }

  private static String createCacheKey(String highlightingRule, String targetSelector) {
    return highlightingRule + ";" + targetSelector;
  }
}
