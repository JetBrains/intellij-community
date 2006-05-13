/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util;

import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * @author max
 */
public final class QueryInstance<Result, Parameter> implements Query<Result> {
  private final Parameter myParameters;
  private final List<QueryExecutor<Result, Parameter>> myExecutors;

  private Condition<Result> myFilter = null;
  private boolean myIsProcessing = false;

  public QueryInstance(@NotNull final Parameter params, @NotNull List<QueryExecutor<Result, Parameter>> executors) {
    myParameters = params;
    myExecutors = executors;
  }

  @NotNull
  public Parameter getParameters() {
    return myParameters;
  }

  public Condition<Result> getFilter() {
    return myFilter;
  }

  public void setFilter(final Condition<Result> filter) {
    assertNotProcessing();
    myFilter = filter;
  }

  @NotNull
  public Collection<Result> findAll() {
    assertNotProcessing();
    final CommonProcessors.CollectUniquesProcessor<Result> processor = new CommonProcessors.CollectUniquesProcessor<Result>();
    forEach(processor);
    return processor.getResults();
  }

  public Iterator<Result> iterator() {
    assertNotProcessing();
    return new UnmodifiableIterator<Result>(findAll().iterator());
  }

  @Nullable
  public Result findFirst() {
    assertNotProcessing();
    final CommonProcessors.FindFirstProcessor<Result> processor = new CommonProcessors.FindFirstProcessor<Result>();
    forEach(processor);
    return processor.getFoundValue();
  }

  public boolean forEach(@NotNull Processor<Result> consumer) {
    assertNotProcessing();

    myIsProcessing = true;
    try {
      if (myFilter != null) {
        consumer = new FilteringProcessor<Result>(myFilter, consumer);
      }

      for (QueryExecutor<Result, Parameter> executor : myExecutors) {
        if (!executor.execute(myParameters, consumer)) return false;
      }

      return true;
    }
    finally {
      myIsProcessing = false;
    }
  }

  private void assertNotProcessing() {
    assert !myIsProcessing : "Operation is not allowed while query is being processed";
  }

  public Result[] toArray(Result[] a) {
    assertNotProcessing();

    final Collection<Result> all = findAll();
    return all.toArray(a);
  }
}
