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
  protected final ArrayList<QueryExecutor<Result, Parameters>> myExecutors = new ArrayList<QueryExecutor<Result, Parameters>>();

  public final void registerExecutor(QueryExecutor<Result, Parameters> executor) {
    synchronized(myExecutors) {
      myExecutors.add(executor);
    }
  }

  public final void unregisterExecutor(QueryExecutor<Result, Parameters> executor) {
    synchronized(myExecutors) {
      myExecutors.remove(executor);
    }
  }

  /**
   * @return query to perform the search.
   * @param parameters of the search
   */
  public final Query<Result> createQuery(Parameters parameters) {
    return new ExecutorsQuery<Result, Parameters>(parameters, getExecutors());
  }

  protected ArrayList<QueryExecutor<Result, Parameters>> getExecutors() {
    synchronized(myExecutors) {
      return myExecutors;
    }
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
