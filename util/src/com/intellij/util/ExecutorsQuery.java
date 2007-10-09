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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * @author max
 */
public final class ExecutorsQuery<Result, Parameter> implements Query<Result> {
  private final Parameter myParameters;
  private final List<QueryExecutor<Result, Parameter>> myExecutors;

  private boolean myIsProcessing = false;

  public ExecutorsQuery(@NotNull final Parameter params, @NotNull List<QueryExecutor<Result, Parameter>> executors) {
    myParameters = params;
    myExecutors = executors;
  }

  @NotNull
  public Parameter getParameters() {
    return myParameters;
  }

  @NotNull
  public Collection<Result> findAll() {
    assertNotProcessing();
    final CommonProcessors.CollectProcessor<Result> processor = new CommonProcessors.CollectProcessor<Result>();
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
      for (QueryExecutor<Result, Parameter> executor : myExecutors) {
        if (!executor.execute(myParameters, consumer)) {
          return false;
        }
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
