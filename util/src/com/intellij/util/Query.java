/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author max
 */
public interface Query<Result> extends Iterable<Result> {
  Filter<Result> getFilter();
  void setFilter(Filter<Result> filter);

  @NotNull
  Collection<Result> findAll();

  @Nullable
  Result findFirst();

  boolean forEach(@NotNull Processor<Result> consumer);

  Result[] toArray(Result[] a);
}
