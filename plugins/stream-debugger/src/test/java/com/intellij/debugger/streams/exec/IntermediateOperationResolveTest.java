/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.streams.exec;

import com.intellij.execution.ExecutionException;

import java.lang.reflect.InvocationTargetException;

/**
 * @author Vitaliy.Bibaev
 */
public class IntermediateOperationResolveTest extends TraceExecutionTestCase {
  public void testFilter() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testMap() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testFlatMap() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testDistinctSame() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testDistinctEquals() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testDistinctHardCase() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testSorted() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testPeek() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testSkip() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testLimit() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }
}
