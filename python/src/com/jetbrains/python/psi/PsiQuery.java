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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.extenstions.PsiElementExtKt;
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

  @NotNull
  private final List<T> myElements;


  public PsiQuery(@NotNull final T elementToStart) {
    this(Collections.singletonList(elementToStart));
  }


  @SuppressWarnings("MethodCanBeVariableArityMethod") // To prevent type safety
  public PsiQuery(@NotNull final T[] elementsToStart) {
    this(Arrays.asList(elementsToStart));
  }

  public PsiQuery(@NotNull final List<T> elementsToStart) {
    myElements = Collections.unmodifiableList(elementsToStart);
  }

  /**
   * Adds elements to query
   */
  @NotNull
  public PsiQuery<T> addElements(@NotNull final PsiQuery<? extends T> newElements) {
    final List<T> newList = new ArrayList<>(myElements);
    newList.addAll(newElements.myElements);
    return new PsiQuery<>(newList);
  }


  @NotNull
  public static <T extends PsiElement> PsiQuery<T> create(@NotNull final T elementToStart) {
    return new PsiQuery<>(elementToStart);
  }

  @NotNull
  public static <T extends PsiElement> PsiQuery<T> createFromQueries(@NotNull final List<PsiQuery<? extends T>> queriesWithElements) {
    final Set<T> result = new LinkedHashSet<>();
    queriesWithElements.forEach(o -> result.addAll(o.getElements()));
    return new PsiQuery<>(new ArrayList<>(result));
  }

  @NotNull
  public <R extends T> PsiQuery<R> filter(@NotNull final PsiFilter<R> filter) {
    return new PsiQuery<>(filter.filter(myElements));
  }

  @NotNull
  public <R extends T> PsiQuery<R> filter(@NotNull final Class<R> filterToType) {
    return filter(new PsiFilter<>(filterToType));
  }

  @NotNull
  public PsiQuery<T> filter(@NotNull final Predicate<T> filter) {
    return new PsiQuery<>(asStream().filter(filter).collect(Collectors.toList()));
  }

  @NotNull
  public <R extends PsiElement> PsiQuery<R> map(@NotNull final Function<T, R> map) {
    return new PsiQuery<>(asStream().map(map).collect(Collectors.toList()));
  }

  @NotNull
  public <R extends PsiElement, F_R extends T> PsiQuery<R> map(@NotNull final PsiFilter<F_R> preFilter,
                                                               @NotNull final Function<F_R, R> map) {
    return filter(preFilter).map(map);
  }

  @NotNull
  public <R extends PsiElement> PsiQuery<R> descendants(@NotNull final Class<R> type) {
    return descendants(new PsiFilter<>(type));
  }

  @NotNull
  public <R extends PsiElement> PsiQuery<R> ancestors(@NotNull final Class<R> type) {
    return ancestors(new PsiFilter<>(type));
  }

  @NotNull
  public <R extends PsiElement> PsiQuery<R> siblings(@NotNull final Class<R> type) {
    return siblings(new PsiFilter<>(type));
  }

  @NotNull
  public <R extends PsiElement> PsiQuery<R> descendants(@NotNull final PsiFilter<R> filter) {
    return getQueryWithProducer(o -> PsiTreeUtil.findChildrenOfType(o, filter.myClass), filter);
  }

  @NotNull
  public <R extends PsiElement> PsiQuery<R> ancestors(@NotNull final PsiFilter<R> filter) {
    return getQueryWithProducer(o -> PsiElementExtKt.getAncestors(o, o.getContainingFile(), filter.myClass), filter);
  }

  @NotNull
  public <R extends PsiElement> PsiQuery<R> siblings(@NotNull final PsiFilter<R> filter) {
    return getQueryWithProducer(o -> PsiTreeUtil.findChildrenOfType(o.getParent(), filter.myClass), filter);
  }

  @NotNull
  private <R extends PsiElement> PsiQuery<R> getQueryWithProducer(@NotNull final Function<T, Collection<R>> elementsProducer,
                                                                  @NotNull final PsiFilter<R> filter) {
    final Set<R> result = new LinkedHashSet<>(); // Set to get rid of duplicates but preserve order
    asStream()
      .map(o -> elementsProducer.apply(o)
        .stream().filter(o2 -> !o2.equals(o)) // Filter out same element in case of siblings
        .collect(Collectors.toList()))
      .forEach(result::addAll);
    return new PsiQuery<>(new ArrayList<>(filter.filter(result)));
  }


  @NotNull
  public Stream<T> asStream() {
    return myElements.stream();
  }

  @NotNull
  public List<T> getElements() {
    return asStream().collect(Collectors.toList());
  }

  @Nullable
  public T getFirstElement() {
    final List<T> elements = getElements();
    return (elements.isEmpty() ? null : elements.get(0));
  }

  @Nullable
  public T getLastElement() {
    final List<T> elements = getElements();
    return (elements.isEmpty() ? null : elements.get(elements.size() - 1));
  }

  public boolean exists() {
    return !getElements().isEmpty();
  }

  public static class PsiFilter<T extends PsiElement> {
    public static final PsiFilter<PsiElement> ANY = new PsiFilter<>(PsiElement.class);
    @NotNull
    private final Class<T> myClass;
    @NotNull
    private final Predicate<T> myPredicate;

    /**
     * Include current element in result set, or not (false by default)
     */

    public PsiFilter(@NotNull final Class<T> aClass, @NotNull final Predicate<T> predicate) {
      myClass = aClass;
      myPredicate = predicate;
    }

    public PsiFilter(@NotNull final Class<T> aClass) {
      this(aClass, o -> true);
    }

    @NotNull
    List<T> filter(@NotNull final Collection<?> list) {
      // IDEA-165420
      //noinspection Guava
      return FluentIterable.from(list).filter(myClass).toList().stream().filter(myPredicate).collect(Collectors.toList());
    }
  }

  /**
   * Filter by name for {@link PsiNamedElement}
   */
  public static class PsiNameFilter<T extends PsiNamedElement> extends PsiFilter<T> {

    @NotNull
    public static PsiNameFilter<PsiNamedElement> create(@NotNull final String name) {
      return new PsiNameFilter<>(PsiNamedElement.class, name);
    }

    public PsiNameFilter(@NotNull final Class<T> type, @NotNull final String name) {
      super(type, o -> name.equals(o.getName()));
    }
  }
}
