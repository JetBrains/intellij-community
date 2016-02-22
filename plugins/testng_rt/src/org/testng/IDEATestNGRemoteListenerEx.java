/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.testng;

import java.io.PrintStream;

public class IDEATestNGRemoteListenerEx extends IDEATestNGRemoteListener implements IInvokedMethodListener {
  public IDEATestNGRemoteListenerEx() {}

  public IDEATestNGRemoteListenerEx(PrintStream printStream) {
    super(printStream);
  }

  public synchronized void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
    if (!testResult.getMethod().isTest()) {
      onConfigurationStart(createDelegated(testResult));
    }
  }

  //should be covered by test listeners
  public void afterInvocation(IInvokedMethod method, ITestResult testResult) {}

  @Override
  public synchronized void onConfigurationSuccess(ITestResult result) {
    onConfigurationSuccess(createDelegated(result));
  }

  @Override
  public synchronized void onConfigurationFailure(ITestResult result) {
    onConfigurationFailure(createDelegated(result));
  }
}
