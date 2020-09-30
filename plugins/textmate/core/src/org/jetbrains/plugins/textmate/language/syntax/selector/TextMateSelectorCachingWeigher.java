package org.jetbrains.plugins.textmate.language.syntax.selector;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

public class TextMateSelectorCachingWeigher implements TextMateSelectorWeigher {
  @NotNull private final TextMateSelectorWeigher myOriginalWeigher;
  @NotNull private final ConcurrentMap<CacheKey, TextMateWeigh> myCache;

  public TextMateSelectorCachingWeigher(@NotNull TextMateSelectorWeigher originalWeigher) {
    myOriginalWeigher = originalWeigher;
    myCache = ContainerUtil.createConcurrentSoftKeySoftValueMap();
  }

  @Override
  public TextMateWeigh weigh(@NotNull final CharSequence scopeSelector, @NotNull final CharSequence scope) {
    return myCache.computeIfAbsent(new CacheKey(scopeSelector, scope), this::weigh);
  }

  private TextMateWeigh weigh(@NotNull CacheKey pair) {
    return myOriginalWeigher.weigh(pair.selector, pair.scope);
  }

  private static final class CacheKey {
    final CharSequence selector;
    final CharSequence scope;

    private CacheKey(CharSequence selector, CharSequence scope) {
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