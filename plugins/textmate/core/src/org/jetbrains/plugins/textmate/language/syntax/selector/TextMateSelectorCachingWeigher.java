package org.jetbrains.plugins.textmate.language.syntax.selector;

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentMap;

public class TextMateSelectorCachingWeigher implements TextMateSelectorWeigher {
  @NotNull private final TextMateSelectorWeigher myOriginalWeigher;
  @NotNull private final ConcurrentMap<Pair<CharSequence, CharSequence>, TextMateWeigh> myCache;

  public TextMateSelectorCachingWeigher(@NotNull TextMateSelectorWeigher originalWeigher) {
    myOriginalWeigher = originalWeigher;
    myCache = ContainerUtil.createConcurrentSoftKeySoftValueMap();
  }

  @Override
  public TextMateWeigh weigh(@NotNull final CharSequence scopeSelector, @NotNull final CharSequence scope) {
    return myCache.computeIfAbsent(Pair.create(scopeSelector, scope), this::weigh);
  }

  private TextMateWeigh weigh(@NotNull Pair<CharSequence, CharSequence> pair) {
    return myOriginalWeigher.weigh(pair.first, pair.second);
  }
}
