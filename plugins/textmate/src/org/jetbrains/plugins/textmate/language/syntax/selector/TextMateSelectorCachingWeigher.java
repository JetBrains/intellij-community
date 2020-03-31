package org.jetbrains.plugins.textmate.language.syntax.selector;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class TextMateSelectorCachingWeigher implements TextMateSelectorWeigher {
  private static final Logger LOG = Logger.getInstance(TextMateSelectorCachingWeigher.class);

  @NotNull private final TextMateSelectorWeigher myOriginalWeigher;
  @NotNull private final LoadingCache<Pair<CharSequence, CharSequence>, TextMateWeigh> myCache;

  public TextMateSelectorCachingWeigher(@NotNull TextMateSelectorWeigher originalWeigher) {
    myOriginalWeigher = originalWeigher;
    myCache = CacheBuilder.newBuilder().maximumSize(200).expireAfterWrite(1, TimeUnit.MINUTES).build(CacheLoader.from(pair -> {
      return pair != null ? myOriginalWeigher.weigh(pair.first, pair.second) : TextMateWeigh.ZERO;
    }));
  }

  @Override
  public TextMateWeigh weigh(@NotNull final CharSequence scopeSelector, @NotNull final CharSequence scope) {
    try {
      return myCache.get(Pair.create(scopeSelector, scope));
    }
    catch (ExecutionException e) {
      LOG.error("Can't perform lazy weigh for textmate selectors", e);
      return myOriginalWeigher.weigh(scopeSelector, scope);
    }
  }
}
