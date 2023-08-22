package org.jetbrains.plugins.textmate.language.syntax.selector;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class TextMateSelectorCachingWeigher implements TextMateSelectorWeigher {
  @NotNull private final TextMateSelectorWeigher myOriginalWeigher;
  private final Cache<@NotNull CacheKey, @NotNull TextMateWeigh> myCache;

  public TextMateSelectorCachingWeigher(@NotNull TextMateSelectorWeigher originalWeigher) {
    myOriginalWeigher = originalWeigher;
    myCache = Caffeine.newBuilder().maximumSize(100_000).expireAfterAccess(1, TimeUnit.MINUTES).build();
  }

  @Override
  public TextMateWeigh weigh(@NotNull final CharSequence scopeSelector, final @NotNull TextMateScope scope) {
    return myCache.get(new CacheKey(scopeSelector, scope), this::weigh);
  }

  private TextMateWeigh weigh(@NotNull CacheKey pair) {
    return myOriginalWeigher.weigh(pair.selector, pair.scope);
  }

  private static final class CacheKey {
    final CharSequence selector;
    final TextMateScope scope;

    private CacheKey(CharSequence selector, TextMateScope scope) {
      this.selector = selector;
      this.scope = scope;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      CacheKey key = (CacheKey)o;
      return selector.equals(key.selector) &&
             scope.equals(key.scope);
    }

    @Override
    public int hashCode() {
      return Objects.hash(selector, scope);
    }
  }
}