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

public class IDEATestNGConfigurationListener implements IConfigurationListener {
  private final IDEATestNGRemoteListener myListener;
  private boolean myStarted = false;

  public IDEATestNGConfigurationListener(IDEATestNGRemoteListener listener) {
    myListener = listener;
  }

  public void onConfigurationSuccess(ITestResult itr) {
    myListener.onConfigurationSuccess(itr, !myStarted);
  }

  public void onConfigurationFailure(ITestResult itr) {
    myListener.onConfigurationFailure(itr, !myStarted);
  }

  public void onConfigurationSkip(ITestResult itr) {
    myListener.onConfigurationSkip(itr);
  }

  public void setIgnoreStarted() {
    myStarted = true;
  }
}
