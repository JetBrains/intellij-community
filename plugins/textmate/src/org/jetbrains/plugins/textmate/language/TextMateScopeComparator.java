package org.jetbrains.plugins.textmate.language;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateCachingSelectorWeigherKt;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * @deprecated use {@link TextMateScopeComparatorCore}
 */
@Deprecated(forRemoval = true)
public final class TextMateScopeComparator<T> implements Comparator<T> {
  private static final @NotNull TextMateSelectorWeigher myWeigher =
    TextMateCachingSelectorWeigherKt.caching(new TextMateSelectorWeigherImpl());

  private final @NotNull TextMateScopeComparatorCore<T> myScopeComparatorCore;

  public TextMateScopeComparator(@NotNull TextMateScope scope, @NotNull Function<? super T, ? extends CharSequence> scopeSupplier) {
    myScopeComparatorCore = new TextMateScopeComparatorCore<>(myWeigher, scope, scopeSupplier::apply);
  }

  @Override
  public int compare(T first, T second) {
    return myScopeComparatorCore.compare(first, second);
  }

  public @NotNull List<T> sortAndFilter(@NotNull Collection<? extends T> objects) {
    return myScopeComparatorCore.sortAndFilter(objects);
  }

  public @Nullable T max(Collection<T> objects) {
    return myScopeComparatorCore.max(objects);
  }
}
