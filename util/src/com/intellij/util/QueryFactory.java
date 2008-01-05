/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
   * @return query to perform the search. @param parameters of the search
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
    return new UniqueResultsQuery<Result>(createQuery(parameters));
  }

  /**
   * @return query to perform the search. Obtained results are automatically filtered wrt. equals() relation.
   * @param parameters of the search
   * @param hashingStrategy strategy to factor results
   */
  public final Query<Result> createUniqueResultsQuery(Parameters parameters, TObjectHashingStrategy<Result> hashingStrategy) {
    return new UniqueResultsQuery<Result>(createQuery(parameters), hashingStrategy);
  }
}
