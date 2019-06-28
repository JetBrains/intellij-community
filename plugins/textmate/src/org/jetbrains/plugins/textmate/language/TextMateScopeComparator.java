package org.jetbrains.plugins.textmate.language;

import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorCachingWeigher;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class TextMateScopeComparator<T extends TextMateScopeSelectorOwner> implements Comparator<T> {
  @NotNull
  private static final TextMateSelectorWeigher myWeigher = new TextMateSelectorCachingWeigher(new TextMateSelectorWeigherImpl());
  @NotNull
  private final String myScope;

  public TextMateScopeComparator(@NotNull String scope) {
    myScope = scope;
  }

  @Override
  public int compare(T first, T second) {
    return myWeigher.weigh(first.getScopeSelector(), myScope).compareTo(myWeigher.weigh(second.getScopeSelector(), myScope));
  }

  @NotNull
  public List<T> sortAndFilter(@NotNull Collection<? extends T> objects) {
    return ContainerUtil.reverse(ContainerUtil.sorted(ContainerUtil.filter(objects, (Condition<T>)t -> myWeigher.weigh(t.getScopeSelector(), myScope).weigh > 0), this));
  }

  @Nullable
  public T max(Collection<T> objects) {
    TextMateWeigh max = TextMateWeigh.ZERO;
    T result = null;
    for (T object : objects) {
      TextMateWeigh weigh = myWeigher.weigh(object.getScopeSelector(), myScope);
      if (weigh.weigh > 0 && weigh.compareTo(max) > 0) {
        max = weigh;
        result = object;
      }
    }
    return result;
  }
}
