/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util;

import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author max
 */
public interface Query<Result> extends Iterable<Result> {
  Condition<Result> getFilter();
  void setFilter(Condition<Result> filter);

  /**
   * Get all of the results in the {@link java.util.Collection}
   * @return results in a collection or empty collection if no results found.
   */
  @NotNull
  Collection<Result> findAll();

  /**
   * Get the first result or <code>null</code> if no results have been found.
   * @return first result of the search or <code>null</code> if no results.
   */
  @Nullable
  Result findFirst();

  /**
   * Process search results one-by-one. All the results will be subsequently fed to a <code>consumer</code> passed.
   * @param consumer - a processor search results should be fed to.
   * @return <code>true</code> if the search was completed normally,
   *         <code>false</code> if the occurrence processing was cancelled by the processor.
   */
  boolean forEach(@NotNull Processor<Result> consumer);

  Result[] toArray(Result[] a);
}
