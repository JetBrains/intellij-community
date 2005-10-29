/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * @author max
 */
public final class Query<Result, Parameter> implements Iterable<Result> {
  private final Parameter myParameters;
  private final List<QueryExecutor<Result, Parameter>> myExecutors;

  public Query(@NotNull final Parameter params, @NotNull List<QueryExecutor<Result, Parameter>> executors) {
    myParameters = params;
    myExecutors = executors;
  }

  @NotNull
  public Parameter getParameters() {
    return myParameters;
  }

  /**
   * Get all of the results in the {@link java.util.Collection}
   * @return results in a collection or empty collection if no results found.
   */
  @NotNull
  public Collection<Result> findAll() {
    final CommonProcessors.CollectProcessor<Result> processor = new CommonProcessors.CollectProcessor<Result>();
    forEach(processor);
    return processor.getResults();
  }

  public Iterator<Result> iterator() {
    return new UnmodifiableIterator<Result>(findAll().iterator());
  }

  /**
   * Get the first result or <code>null</code> if no results have been found.
   * @return first result of the search or <code>null</code> if no results.
   */
  @Nullable
  public Result findFirst() {
    final CommonProcessors.FindFirstProcessor<Result> processor = new CommonProcessors.FindFirstProcessor<Result>();
    forEach(processor);
    return processor.getFoundValue();
  }

  /**
   * Process search results one-by-one. All the results will be subsequently fed to a <code>consumer</code> passed.
   * @param consumer - a processor search results should be fed to.
   * @return <code>true</code> if the search was completed normally,
   *         <code>false</code> if the occurrence processing was cancelled by the processor.
   */
  public boolean forEach(@NotNull Processor<Result> consumer) {
    for (QueryExecutor<Result, Parameter> executor : myExecutors) {
      if (!executor.execute(myParameters, consumer)) return false;
    }

    return true;
  }
}
