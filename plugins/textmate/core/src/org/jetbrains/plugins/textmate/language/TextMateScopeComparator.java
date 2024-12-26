package org.jetbrains.plugins.textmate.language;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorCachingWeigher;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TextMateScopeComparator<T> implements Comparator<T> {
  private static final @NotNull TextMateSelectorWeigher myWeigher = new TextMateSelectorCachingWeigher(new TextMateSelectorWeigherImpl());

  private final @NotNull TextMateScope myScope;
  private final @NotNull Function<? super T, ? extends CharSequence> myScopeSupplier;

  public TextMateScopeComparator(@NotNull TextMateScope scope, @NotNull Function<? super T, ? extends CharSequence> scopeSupplier) {
    myScope = scope;
    myScopeSupplier = scopeSupplier;
  }

  @Override
  public int compare(T first, T second) {
    return myWeigher.weigh(myScopeSupplier.apply(first), myScope)
      .compareTo(myWeigher.weigh(myScopeSupplier.apply(second), myScope));
  }

  public @NotNull List<T> sortAndFilter(@NotNull Collection<? extends T> objects) {
    return sortAndFilter(objects.stream());
  }

  public @NotNull List<T> sortAndFilter(Stream<? extends T> stream) {
    return stream.filter(t -> myWeigher.weigh(myScopeSupplier.apply(t), myScope).weigh > 0)
      .sorted(Collections.reverseOrder(this)).collect(Collectors.toList());
  }

  public @Nullable T max(Collection<T> objects) {
    TextMateWeigh max = TextMateWeigh.ZERO;
    T result = null;
    for (T object : objects) {
      TextMateWeigh weigh = myWeigher.weigh(myScopeSupplier.apply(object), myScope);
      if (weigh.weigh > 0 && weigh.compareTo(max) > 0) {
        max = weigh;
        result = object;
      }
    }
    return result;
  }
}
