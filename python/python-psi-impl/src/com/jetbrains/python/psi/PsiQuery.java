/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi;

import com.google.common.collect.FluentIterable;
import com.intellij.openapi.util.Predicates;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JQuery-like tool that makes PSI navigation easier.
 * You just "drive" your query filtering results, and no need to check result for null.
 * There are 3 axes: ancestors, descendants and siblings. Each axis has N elements which you can filter or map.
 *
 * @param <T> type of current elements
 * @author Ilya.Kazakevich
 */
public final class PsiQuery<T extends PsiElement> {

  private final @NotNull List<T> myElements;


  public PsiQuery(final @NotNull T elementToStart) {
    this(Collections.singletonList(elementToStart));
  }


  @SuppressWarnings("MethodCanBeVariableArityMethod") // To prevent type safety
  public PsiQuery(final T @NotNull [] elementsToStart) {
    this(Arrays.asList(elementsToStart));
  }

  public PsiQuery(final @NotNull List<? extends T> elementsToStart) {
    myElements = Collections.unmodifiableList(elementsToStart);
  }

  /**
   * Adds elements to query
   */
  public @NotNull PsiQuery<T> addElements(final @NotNull PsiQuery<? extends T> newElements) {
    final List<T> newList = new ArrayList<>(myElements);
    newList.addAll(newElements.myElements);
    return new PsiQuery<>(newList);
  }


  public static @NotNull <T extends PsiElement> PsiQuery<T> create(final @NotNull T elementToStart) {
    return new PsiQuery<>(elementToStart);
  }

  public static @NotNull <T extends PsiElement> PsiQuery<T> createFromQueries(final @NotNull List<? extends PsiQuery<? extends T>> queriesWithElements) {
    final Set<T> result = new LinkedHashSet<>();
    queriesWithElements.forEach(o -> result.addAll(o.getElements()));
    return new PsiQuery<>(new ArrayList<>(result));
  }

  public @NotNull <R extends T> PsiQuery<R> filter(final @NotNull PsiFilter<R> filter) {
    return new PsiQuery<>(filter.filter(myElements));
  }

  public @NotNull <R extends T> PsiQuery<R> filter(final @NotNull Class<R> filterToType) {
    return filter(new PsiFilter<>(filterToType));
  }

  public @NotNull PsiQuery<T> filter(final @NotNull Predicate<? super T> filter) {
    return new PsiQuery<>(asStream().filter(filter).collect(Collectors.toList()));
  }

  public @NotNull <R extends PsiElement> PsiQuery<R> map(final @NotNull Function<? super T, ? extends R> map) {
    return new PsiQuery<>(asStream().map(map).collect(Collectors.toList()));
  }

  public @NotNull <R extends PsiElement, F_R extends T> PsiQuery<R> map(final @NotNull PsiFilter<F_R> preFilter,
                                                                        final @NotNull Function<? super F_R, ? extends R> map) {
    return filter(preFilter).map(map);
  }

  public @NotNull <R extends PsiElement> PsiQuery<R> descendants(final @NotNull Class<R> type) {
    return descendants(new PsiFilter<>(type));
  }

  public @NotNull <R extends PsiElement> PsiQuery<R> ancestors(final @NotNull Class<R> type) {
    return ancestors(new PsiFilter<>(type));
  }

  public @NotNull <R extends PsiElement> PsiQuery<R> siblings(final @NotNull Class<R> type) {
    return siblings(new PsiFilter<>(type));
  }

  public @NotNull <R extends PsiElement> PsiQuery<R> descendants(final @NotNull PsiFilter<R> filter) {
    return getQueryWithProducer(o -> PsiTreeUtil.findChildrenOfType(o, filter.myClass), filter);
  }

  public @NotNull <R extends PsiElement> PsiQuery<R> ancestors(final @NotNull PsiFilter<R> filter) {
    return getQueryWithProducer(o -> PsiElementExtKt.getAncestors(o, o.getContainingFile(), filter.myClass), filter);
  }

  public @NotNull <R extends PsiElement> PsiQuery<R> siblings(final @NotNull PsiFilter<R> filter) {
    return getQueryWithProducer(o -> PsiTreeUtil.getChildrenOfTypeAsList(o.getParent(), filter.myClass), filter);
  }

  private @NotNull <R extends PsiElement> PsiQuery<R> getQueryWithProducer(final @NotNull Function<? super T, ? extends Collection<R>> elementsProducer,
                                                                           final @NotNull PsiFilter<R> filter) {
    final Set<R> result = new LinkedHashSet<>(); // Set to get rid of duplicates but preserve order
    asStream()
      .map(o -> ContainerUtil.filter(elementsProducer.apply(o), o2 -> !o2.equals(o)))
      .forEach(result::addAll);
    return new PsiQuery<>(new ArrayList<>(filter.filter(result)));
  }


  public @NotNull Stream<T> asStream() {
    return myElements.stream();
  }

  public @NotNull List<T> getElements() {
    return asStream().collect(Collectors.toList());
  }

  public @Nullable T getFirstElement() {
    final List<T> elements = getElements();
    return (elements.isEmpty() ? null : elements.get(0));
  }

  public @Nullable T getLastElement() {
    final List<T> elements = getElements();
    return (elements.isEmpty() ? null : elements.get(elements.size() - 1));
  }

  public boolean exists() {
    return !getElements().isEmpty();
  }

  public static class PsiFilter<T extends PsiElement> {
    public static final PsiFilter<PsiElement> ANY = new PsiFilter<>(PsiElement.class);
    private final @NotNull Class<? extends T> myClass;
    private final @NotNull Predicate<? super T> myPredicate;

    /**
     * Include current element in result set, or not (false by default)
     */

    public PsiFilter(final @NotNull Class<? extends T> aClass, final @NotNull Predicate<? super T> predicate) {
      myClass = aClass;
      myPredicate = predicate;
    }

    public PsiFilter(final @NotNull Class<? extends T> aClass) {
      this(aClass, Predicates.alwaysTrue());
    }

    @NotNull
    List<T> filter(final @NotNull Collection<?> list) {
      // IDEA-165420
      //noinspection Guava
      return FluentIterable.from(list).filter(myClass).toList().stream().filter(myPredicate).collect(Collectors.toList());
    }
  }

  /**
   * Filter by name for {@link PsiNamedElement}
   */
  public static class PsiNameFilter<T extends PsiNamedElement> extends PsiFilter<T> {

    public static @NotNull PsiNameFilter<PsiNamedElement> create(final @NotNull String name) {
      return new PsiNameFilter<>(PsiNamedElement.class, name);
    }

    public PsiNameFilter(final @NotNull Class<? extends T> type, final @NotNull String name) {
      super(type, o -> name.equals(o.getName()));
    }
  }
}
