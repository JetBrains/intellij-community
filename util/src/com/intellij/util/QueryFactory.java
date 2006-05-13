/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util;

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
   * This method is intentionally made protected to be used only by QueryFactory implementations
   * It is supposed that the implementors will box Parameters themselves based on info passed to them
   * @return query to perform the search
   * @param parameters of the search
   */
  protected final Query<Result> createQuery(Parameters parameters) {
    return new UniqueResultsQuery<Result>(new ExecutorsQuery<Result, Parameters>(parameters, myExecutors));
  }
}
