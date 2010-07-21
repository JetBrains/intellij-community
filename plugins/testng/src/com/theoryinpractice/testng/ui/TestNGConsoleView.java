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
 * Date: Jul 9, 2005
 * Time: 4:19:20 PM
 */
package com.theoryinpractice.testng.ui;

import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.Printable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.stacktrace.DiffHyperlink;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.testframework.ui.TestResultsPanel;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.model.TestNGConsoleProperties;
import com.theoryinpractice.testng.model.TestProxy;
import org.jetbrains.annotations.NonNls;
import org.testng.remote.strprotocol.TestResultMessage;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestNGConsoleView extends BaseTestsOutputConsoleView {
  @NonNls private static final Pattern COMPARISION_PATTERN =
    Pattern.compile("([^\\<\\>]*)expected[^\\<\\>]*\\<([^\\<\\>]*)\\>[^\\<\\>]*\\<([^\\<\\>]*)\\>[^\\<\\>]*");
  @NonNls private static final Pattern EXPECTED_BUT_WAS_PATTERN =
    Pattern.compile("(.*)expected:\\<(.*)\\> but was:\\<(.*)\\>.*", Pattern.DOTALL);
  @NonNls private static final Pattern EXPECTED_NOT_SAME_BUT_WAS_PATTERN =
    Pattern.compile("(.*)expected not same with:\\<(.*)\\> but was:\\<(.*)\\>.*", Pattern.DOTALL);
  private TestNGResults testNGResults;
  private final List<Printable> currentTestOutput = new ArrayList<Printable>();
  private final List<Printable> nonTestOutput = new ArrayList<Printable>();

  private int myExceptionalMark = -1;
  private final TestNGConfiguration myConfiguration;
  private final RunnerSettings myRunnerSettings;
  private final ConfigurationPerRunnerSettings myConfigurationPerRunnerSettings;

  public TestNGConsoleView(TestNGConfiguration config,
                           final RunnerSettings runnerSettings,
                           final ConfigurationPerRunnerSettings configurationPerRunnerSettings) {
    super(new TestNGConsoleProperties(config), null);
    myConfiguration = config;
    myRunnerSettings = runnerSettings;
    myConfigurationPerRunnerSettings = configurationPerRunnerSettings;
  }

  protected TestResultsPanel createTestResultsPanel() {
    testNGResults = new TestNGResults(getConsole().getComponent(), myConfiguration, this, myRunnerSettings, myConfigurationPerRunnerSettings);
    return testNGResults;
  }


  @Override
  public void dispose() {
    super.dispose();
    testNGResults = null;
  }

  public TestNGResults getResultsView() {
    return testNGResults;
  }

  public void rebuildTree() {
    if (testNGResults != null) {
      testNGResults.rebuildTree();
    }
  }

  public void addTestResult(TestResultMessage result) {
    if (testNGResults != null) {
      int exceptionMark = myExceptionalMark == -1 ? 0 : myExceptionalMark;
      final String stackTrace = result.getStackTrace();
      if (stackTrace != null && stackTrace.length() > 10) {
        //trim useless crud from stacktrace
        String trimmed = trimStackTrace(stackTrace);
        List<Printable> printables = getPrintables(result, trimmed);
        for (Printable printable : printables) {
          printable.printOn(wrapConsoleView(getConsole())); //enable for root element
        }
        synchronized (currentTestOutput) {
          exceptionMark = currentTestOutput.size();
          currentTestOutput.addAll(printables);
        }
      }
      testNGResults.addTestResult(result, new ArrayList<Printable>(currentTestOutput), exceptionMark);

      myExceptionalMark = -1;
      synchronized (currentTestOutput) {
        currentTestOutput.clear();
      }
    }
  }

  public void testStarted(TestResultMessage result) {
    if (testNGResults != null) {
      testNGResults.testStarted(result);
    }
  }

  public void flushOutput() {
    synchronized (currentTestOutput) {
      if (!currentTestOutput.isEmpty()) { //non empty for first test only
        nonTestOutput.addAll(currentTestOutput);
        currentTestOutput.clear();
      }
    }
  }

  public void flush() {
    final TestProxy failedToStart = testNGResults.getFailedToStart();
    if (failedToStart != null) {
      final List<Printable> output = failedToStart.getOutput();
      if (output != null) {
        nonTestOutput.addAll(output);
      }
    }
  }

  private static String trimStackTrace(String stackTrace) {
    String[] lines = stackTrace.split("\n");
    StringBuilder builder = new StringBuilder();

    if (lines.length > 0) {
      int i = lines.length - 1;
      while (i >= 0) {
        //first 4 chars are '\t at '
        int startIndex = lines[i].indexOf('a') + 3;
        if (lines[i].length() > 4 &&
            (lines[i].startsWith("org.testng.", startIndex) ||
             lines[i].startsWith("org.junit.", startIndex) ||
             lines[i].startsWith("sun.reflect.DelegatingMethodAccessorImpl", startIndex) ||
             lines[i].startsWith("sun.reflect.NativeMethodAccessorImpl", startIndex) ||
             lines[i].startsWith("java.lang.reflect.Method", startIndex) ||
             lines[i].startsWith("com.intellij.rt.execution.application.AppMain", startIndex))) {

        }
        else {
          // we're done with internals, so we know the rest are ok
          break;
        }
        i--;
      }
      for (int j = 0; j <= i; j++) {
        builder.append(lines[j]);
        builder.append('\n');
      }
    }
    return builder.toString();
  }

  private List<Printable> getPrintables(final TestResultMessage result, String s) {
    List<Printable> printables = new ArrayList<Printable>();
    //figure out if we have a diff we need to hyperlink
    Matcher matcher = COMPARISION_PATTERN.matcher(s);
    if (!matcher.matches()) {
      matcher = EXPECTED_BUT_WAS_PATTERN.matcher(s);
    }
    if (!matcher.matches()) {
      matcher = EXPECTED_NOT_SAME_BUT_WAS_PATTERN.matcher(s);
    }
    if (matcher.matches()) {
      printables.add(new Chunk(matcher.group(1), ConsoleViewContentType.ERROR_OUTPUT));
      //we have an assert with expected/actual, so we parse it out and create a diff hyperlink
      DiffHyperlink link = new DiffHyperlink(matcher.group(2), matcher.group(3), null) {
        protected String getTitle() {
          //TODO should do some more farting about to find the equality assertion that failed and show that as title
          return result.getTestClass() + '#' + result.getMethod() + "() failed";
        }
      };
      //same as junit diff view
      printables.add(link);
      printables.add(new Chunk(trimStackTrace(s.substring(matcher.end(3) + 1)), ConsoleViewContentType.ERROR_OUTPUT));
    }
    else {
      printables.add(new Chunk(s, ConsoleViewContentType.ERROR_OUTPUT));
    }
    return printables;
  }

  public void print(String s, ConsoleViewContentType contentType) {
    if (myExceptionalMark == -1 && contentType == ConsoleViewContentType.ERROR_OUTPUT) {
      myExceptionalMark = currentTestOutput.size();
    }
    Chunk chunk = new Chunk(s, contentType);
    synchronized (currentTestOutput) {
      currentTestOutput.add(chunk);
    }
  }

  public void reset() {
    final List<Printable> printables = new ArrayList<Printable>();
    printables.addAll(nonTestOutput);
    printables.addAll(testNGResults.getRoot().getOutput());
    printables.addAll(currentTestOutput);
    setView(printables, 0);
  }

  public void attachToProcess(ProcessHandler processHandler) {
    getConsole().attachToProcess(processHandler);
  }
  
  public void setView(final List<Printable> output, final int i) {
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          setView(output, i);
        }
      });
    }
    else {
      final ConsoleView consoleView = getConsole();
      consoleView.clear();
      int idx = 0;
      int offset = 0;
      for (Printable chunk : new ArrayList<Printable>(output)) {
        chunk.printOn(wrapConsoleView(consoleView));
        if (idx++ < i) {
          offset = consoleView.getContentSize();
        }
      }
      consoleView.scrollTo(offset);
    }
  }

  private static Printer wrapConsoleView(final ConsoleView consoleView) {
    return new Printer() {
      public void print(String text, ConsoleViewContentType contentType) {
        consoleView.print(text, contentType);
      }

      public void onNewAvailable(Printable printable) {}

      public void printHyperlink(String text, HyperlinkInfo info) {
        consoleView.printHyperlink(text, info);
      }

      public void mark() {
      }
    };
  }

  public static class Chunk implements Printable {
    public String text;
    public ConsoleViewContentType contentType;

    public void printOn(Printer printer) {
      printer.print(text, contentType);
    }

    public Chunk(String text, ConsoleViewContentType contentType) {
      this.text = text;
      this.contentType = contentType;
    }

    public String toString() {
      return text;
    }
  }
}
