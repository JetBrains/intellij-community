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
public class AccessibilityTest extends TraceExecutionTestCase {
  public void testAccessToPrivateMethodsInStaticContext() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testAccessToPrivateMethods() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testAccessToPrivateClassInStaticContext() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testAccessToPrivateClass() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testAccessToPrivateClassWithMethodReference() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }
}
