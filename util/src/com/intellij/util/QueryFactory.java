/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util;

import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author max
 */
public class QueryFactory<Result, Parameters> {
  private final List<QueryExecutor<Result, Parameters>> myExecutors = new CopyOnWriteArrayList<QueryExecutor<Result,Parameters>>();

  public void registerExecutor(QueryExecutor<Result, Parameters> executor) {
    myExecutors.add(executor);
  }

  public void unregisterExecutor(QueryExecutor<Result, Parameters> executor) {
    myExecutors.remove(executor);
  }

  /**
   * @return query to perform the search.
   * @param parameters of the search
   */
  public final Query<Result> createQuery(Parameters parameters) {
    return new ExecutorsQuery<Result, Parameters>(parameters, getExecutors());
  }

  @NotNull
  protected List<QueryExecutor<Result, Parameters>> getExecutors() {
    return myExecutors;
  }

  /**
   * @return query to perform the search. Obtained results are automatically filtered wrt. equals() relation.
   * @param parameters of the search
   */
  public final Query<Result> createUniqueResultsQuery(Parameters parameters) {
    return new UniqueResultsQuery<Result>(new ExecutorsQuery<Result, Parameters>(parameters, getExecutors()));
  }

  /**
   * @return query to perform the search. Obtained results are automatically filtered wrt. equals() relation.
   * @param parameters of the search
   * @param hashingStrategy strategy to factor results
   */
  public final Query<Result> createUniqueResultsQuery(Parameters parameters, TObjectHashingStrategy<Result> hashingStrategy) {
    return new UniqueResultsQuery<Result>(new ExecutorsQuery<Result, Parameters>(parameters, getExecutors()), hashingStrategy);
  }
}
