/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 * @author max
 */
public class EmptyQuery<R> implements Query<R> {
  private static final EmptyQuery EMPTY_QUERY_INSTANCE = new EmptyQuery();

  @NotNull
  public Collection<R> findAll() {
    return Collections.emptyList();
  }

  public R findFirst() {
    return null;
  }

  public boolean forEach(final Processor<R> consumer) {
    return true;
  }

  public R[] toArray(final R[] a) {
    return findAll().toArray(a);
  }

  public Iterator<R> iterator() {
    return findAll().iterator();
  }

  public static <T> Query<T> getEmptyQuery() {
    return (Query<T>) EMPTY_QUERY_INSTANCE;
  }
}
