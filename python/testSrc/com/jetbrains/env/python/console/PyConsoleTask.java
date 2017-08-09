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
package com.jetbrains.env.python.console;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.python.console.*;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * @author traff
 */
public class PyConsoleTask extends PyExecutionFixtureTestTask {
  private boolean myProcessCanTerminate;

  protected PyConsoleProcessHandler myProcessHandler;
  protected PydevConsoleCommunication myCommunication;

  private boolean shouldPrintOutput = false;
  private PythonConsoleView myConsoleView;
  private Semaphore myCommandSemaphore;
  private Semaphore myConsoleInitSemaphore;
  private PythonConsoleExecuteActionHandler myExecuteHandler;

  private Ref<RunContentDescriptor> myContentDescriptorRef = Ref.create();

  public PyConsoleTask() {
    super(null);
  }

  @Nullable
  @Override
  public Set<String> getTagsToCover() {
    return Sets.newHashSet("python3.6", "python2.7", "ipython", "ipython200", "jython", "IronPython");
  }

  public PythonConsoleView getConsoleView() {
    return myConsoleView;
  }

  @Override
  public void setUp(final String testName) throws Exception {
    super.setUp(testName);
  }

  @NotNull
  protected String output() {
    return myConsoleView.getHistoryViewer().getDocument().getText();
  }

  public void setProcessCanTerminate(boolean processCanTerminate) {
    myProcessCanTerminate = processCanTerminate;
  }

  @Override
  public void tearDown() throws Exception {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      try {
        if (myConsoleView != null) {
          disposeConsole();
          myCommunication.waitForTerminate();
        }
        super.tearDown();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  private void disposeConsole() throws InterruptedException {
    if (myCommunication != null) {
      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
        try {
          myCommunication.close();
        }
        catch (Exception e) {
          e.printStackTrace();
        }
        myCommunication = null;
      });
    }

    disposeConsoleProcess();

    if (!myContentDescriptorRef.isNull()) {
      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> Disposer.dispose(myContentDescriptorRef.get()));
    }

    if (myConsoleView != null) {
      new WriteAction() {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          Disposer.dispose(myConsoleView);
          myConsoleView = null;
        }
      }.execute();
    }
  }

  @Override
  public void runTestOn(final String sdkHome) throws Exception {
    final Project project = getProject();

    final Sdk sdk = createTempSdk(sdkHome, SdkCreationType.EMPTY_SDK);

    setProcessCanTerminate(false);

    PydevConsoleRunner consoleRunner =
      new PydevConsoleRunnerImpl(project, sdk, PyConsoleType.PYTHON, myFixture.getTempDirPath(), Maps.newHashMap(),
                                 PyConsoleOptions.getInstance(project).getPythonConsoleSettings(),
                                 (s) -> {
                                 }) {
        protected void showContentDescriptor(RunContentDescriptor contentDescriptor) {
          myContentDescriptorRef.set(contentDescriptor);
          super.showContentDescriptor(contentDescriptor);
        }
      };

    before();

    myConsoleInitSemaphore = new Semaphore(0);

    consoleRunner.addConsoleListener(new PydevConsoleRunnerImpl.ConsoleListener() {
      @Override
      public void handleConsoleInitialized(LanguageConsoleView consoleView) {
        myConsoleInitSemaphore.release();
      }
    });

    consoleRunner.run();

    waitFor(myConsoleInitSemaphore);

    myCommandSemaphore = new Semaphore(1);

    myConsoleView = consoleRunner.getConsoleView();
    myProcessHandler = consoleRunner.getProcessHandler();

    myExecuteHandler = consoleRunner.getConsoleExecuteActionHandler();

    myCommunication = consoleRunner.getPydevConsoleCommunication();

    myCommunication.addCommunicationListener(new ConsoleCommunicationListener() {
      @Override
      public void commandExecuted(boolean more) {
        myCommandSemaphore.release();
      }

      @Override
      public void inputRequested() {
      }
    });

    myProcessHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        if (event.getExitCode() != 0 && !myProcessCanTerminate) {
          Assert.fail("Process terminated unexpectedly\n" + output());
        }
      }
    });

    OutputPrinter myOutputPrinter = null;
    if (shouldPrintOutput) {
      myOutputPrinter = new OutputPrinter();
      myOutputPrinter.start();
    }

    waitForOutput("PyDev console");

    try {
      testing();
      after();
    }
    finally {
      setProcessCanTerminate(true);

      if (myOutputPrinter != null) {
        myOutputPrinter.stop();
      }

      disposeConsole();
    }
  }

  private void disposeConsoleProcess() throws InterruptedException {
    myProcessHandler.destroyProcess();

    waitFor(myProcessHandler);

    if (!myProcessHandler.isProcessTerminated()) {
      if (!waitFor(myProcessHandler)) {
        if (!myProcessHandler.isProcessTerminated()) {
          throw new RuntimeException("Cannot stop console process");
        }
      }
    }
    myProcessHandler = null;
  }

  /**
   * Waits until all passed strings appear in output.
   * If they don't appear in time limit, then exception is raised.
   *
   * @param string
   * @throws InterruptedException
   */
  public void waitForOutput(String... string) throws InterruptedException {
    int count = 0;
    while (true) {
      List<String> missing = Lists.newArrayList();
      String out = output();
      boolean flag = true;
      for (String s : string) {
        if (!out.contains(s)) {
          flag = false;
          missing.add(s);
        }
      }
      if (flag) {
        break;
      }
      if (count > 10) {
        Assert.fail("Strings: <--\n" + StringUtil.join(missing, "\n---\n") + "-->" + "are not present in output.\n" + output());
      }
      Thread.sleep(2000);
      count++;
    }
  }

  protected void waitForReady() throws InterruptedException {
    int count = 0;
    while (!myExecuteHandler.isEnabled() || !canExecuteNow()) {
      if (count > 10) {
        Assert.fail("Console is not ready");
      }
      Thread.sleep(300);
      count++;
    }
  }

  protected boolean canExecuteNow() {
    return myExecuteHandler.canExecuteNow();
  }

  public void setShouldPrintOutput(boolean shouldPrintOutput) {
    this.shouldPrintOutput = shouldPrintOutput;
  }

  private class OutputPrinter {
    private Thread myThread;
    private int myLen = 0;

    public void start() {
      myThread = new Thread(() -> doJob(), "py console printer");
      myThread.setDaemon(true);
      myThread.start();
    }

    private void doJob() {
      try {
        while (true) {
          printToConsole();

          Thread.sleep(500);
        }
      }
      catch (Exception ignored) {
      }
    }

    private synchronized void printToConsole() {
      String s = output();
      if (s.length() > myLen) {
        System.out.print(s.substring(myLen));
      }
      myLen = s.length();
    }

    public void stop() throws InterruptedException {
      printToConsole();
      myThread.interrupt();
      myThread.join();
    }
  }

  protected void exec(final String command) throws InterruptedException {
    waitForReady();
    myCommandSemaphore.acquire(1);
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> myConsoleView.executeInConsole(command));
    Assert.assertTrue(String.format("Command execution wasn't finished: `%s` \n" +
                                    "Output: %s", command, output()), waitFor(myCommandSemaphore));
    myCommandSemaphore.release();
  }

  protected boolean hasValue(String varName, String value) throws PyDebuggerException {
    PyDebugValue val = getValue(varName);
    return val != null && value.equals(val.getValue());
  }

  protected void setValue(String varName, String value) throws PyDebuggerException {
    PyDebugValue val = getValue(varName);
    myCommunication.changeVariable(val, value);
  }

  protected PyDebugValue getValue(String varName) throws PyDebuggerException {
    XValueChildrenList l = myCommunication.loadFrame();

    if (l == null) {
      return null;
    }
    for (int i = 0; i < l.size(); i++) {
      String name = l.getName(i);
      if (varName.equals(name)) {
        return (PyDebugValue)l.getValue(i);
      }
    }

    return null;
  }

  protected List<String> getCompoundValueChildren(PyDebugValue value) throws PyDebuggerException {
    XValueChildrenList list = myCommunication.loadVariable(value);
    List<String> result = Lists.newArrayList();
    for (int i = 0; i < list.size(); i++) {
      result.add(((PyDebugValue)list.getValue(i)).getValue());
    }
    return result;
  }

  protected void input(String text) {
    myConsoleView.executeInConsole(text);
  }

  protected void waitForFinish() throws InterruptedException {
    waitFor(myCommandSemaphore);
  }

  protected void execNoWait(final String command) {
    UIUtil.invokeLaterIfNeeded(() -> myConsoleView.executeCode(command, null));
  }

  protected void interrupt() {
    myCommunication.interrupt();
  }


  public void addTextToEditor(final String text) {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
                                   getConsoleView().setInputText(text);
                                   PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
                                 }
    );
  }
}
