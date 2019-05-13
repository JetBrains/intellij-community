/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

public class IDEATestNGTestListener implements ITestListener {
  private final IDEATestNGRemoteListener myListener;

  public IDEATestNGTestListener(IDEATestNGRemoteListener listener) {
    myListener = listener;
  }

  public void onTestStart(ITestResult result) {
    myListener.onTestStart(result);
  }

  public void onTestSuccess(ITestResult result) {
    myListener.onTestSuccess(result);
  }

  public void onTestFailure(ITestResult result) {
    myListener.onTestFailure(result);
  }

  public void onTestSkipped(ITestResult result) {
    myListener.onTestSkipped(result);
  }

  public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
    myListener.onTestFailedButWithinSuccessPercentage(result);
  }

  public void onStart(ITestContext context) {
    myListener.onStart(context);
  }

  public void onFinish(ITestContext context) {
    myListener.onFinish(context);
  }
}
