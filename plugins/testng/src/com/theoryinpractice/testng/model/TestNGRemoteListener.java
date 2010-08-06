/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 11, 2005
 * Time: 9:02:27 PM
 */
package com.theoryinpractice.testng.model;

import com.theoryinpractice.testng.ui.TestNGConsoleView;
import com.theoryinpractice.testng.ui.TestNGResults;
import org.testng.remote.strprotocol.*;

public class TestNGRemoteListener implements IRemoteSuiteListener, IRemoteTestListener {
  private final TestNGConsoleView console;
  private final TreeRootNode unboundOutputRoot;

  public TestNGRemoteListener(TestNGConsoleView console, TreeRootNode unboundOutputRoot) {
    this.console = console;
    this.unboundOutputRoot = unboundOutputRoot;
  }

  public void onInitialization(GenericMessage genericMessage) {
  }

  public void onStart(SuiteMessage suiteMessage) {
    final TestNGResults view = console.getResultsView();
    if (view != null) {
      view.start();
    }
  }

  public void onFinish(SuiteMessage suiteMessage) {
    unboundOutputRoot.flush();
    console.finish();
    final TestNGResults view = console.getResultsView();
    if (view != null) {
      view.finish();
    }
  }

  public void onStart(TestMessage tm) {
    unboundOutputRoot.addChild(console.getResultsView().getRoot());
    final TestNGResults view = console.getResultsView();
    if (view != null) {
      view.setTotal(tm.getTestMethodCount());
    }
  }

  public void onTestStart(TestResultMessage trm) {
    console.testStarted(trm);
  }

  public void onFinish(TestMessage tm) {
    console.rebuildTree();
  }

  public void onTestSuccess(TestResultMessage trm) {
    console.addTestResult(trm);
  }

  public void onTestFailure(TestResultMessage trm) {
    console.addTestResult(trm);
  }

  public void onTestSkipped(TestResultMessage trm) {
    console.addTestResult(trm);
  }

  public void onTestFailedButWithinSuccessPercentage(TestResultMessage trm) {
  }
}
