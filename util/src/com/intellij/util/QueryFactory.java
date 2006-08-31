/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util;

import gnu.trove.TObjectHashingStrategy;

import java.util.ArrayList;

/**
 * @author max
 */
public class QueryFactory<Result, Parameters> {
  private ArrayList<QueryExecutor<Result, Parameters>> myExecutors = new ArrayList<QueryExecutor<Result, Parameters>>();

  public final void registerExecutor(QueryExecutor<Result, Parameters> executor) {
    myExecutors.add(executor);
  }

  public final void unregisterExecutor(QueryExecutor<Result, Parameters> executor) {
    myExecutors.remove(executor);
  }

  /**
   * @return query to perform the search.
   * @param parameters of the search
   */
  public final Query<Result> createQuery(Parameters parameters) {
    return new ExecutorsQuery<Result, Parameters>(parameters, myExecutors);
  }

  /**
   * @return query to perform the search. Obtained results are automatically filtered wrt. equals() relation.
   * @param parameters of the search
   */
  public final Query<Result> createUniqueResultsQuery(Parameters parameters) {
    return new UniqueResultsQuery<Result>(new ExecutorsQuery<Result, Parameters>(parameters, myExecutors));
  }

  /**
   * @return query to perform the search. Obtained results are automatically filtered wrt. equals() relation.
   * @param parameters of the search
   * @param hashingStrategy strategy to factor results
   */
  public final Query<Result> createUniqueResultsQuery(Parameters parameters, TObjectHashingStrategy<Result> hashingStrategy) {
    return new UniqueResultsQuery<Result>(new ExecutorsQuery<Result, Parameters>(parameters, myExecutors), hashingStrategy);
  }
}
