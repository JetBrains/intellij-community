// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.python.console;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author traff
 */
public class PyConsoleTask extends PyExecutionFixtureTestTask {
  private static final Logger LOG = Logger.getInstance("com.jetbrains.env.python.console.PyConsoleTask");

  private boolean myProcessCanTerminate;

  protected PyConsoleProcessHandler myProcessHandler;
  protected PydevConsoleCommunication myCommunication;

  private boolean shouldPrintOutput = false;
  private volatile PythonConsoleView myConsoleView;
  private Semaphore myCommandSemaphore;
  private Semaphore myConsoleInitSemaphore;
  private PythonConsoleExecuteActionHandler myExecuteHandler;

  private final Ref<RunContentDescriptor> myContentDescriptorRef = Ref.create();

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
    // Prevents thread leak, see its doc
    killRpcThread();

    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        if (myConsoleView != null) {
          disposeConsole();
        }
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }, ModalityState.defaultModalityState());
    super.tearDown();
  }

  /**
   * Kill XML-Rpc thread
   * Due to stupid bug in {@link LiteXmlRpcTransport#initConnection()} which has <strong>infinite</strong> loop that
   * tries to connect to already dead process (already closed socket): See "tries" var.
   */
  private static void killRpcThread() throws InterruptedException {
    final Optional<Thread> rpc = Thread.getAllStackTraces().keySet().stream()
                                       .filter(o -> o.getClass().getName().contains("XmlRpc"))
                                       .findFirst();
    if (rpc.isPresent()) {
      final Thread thread = rpc.get();
      // There is no way to interrupt this thread with "interrupt": it has infinite loop (bug) which does not check ".isInterrupted()"
      //noinspection CallToThreadStopSuspendOrResumeManager
      thread.stop();
      thread.join();
    }
  }

  /**
   * Disposes Python console and waits for Python console server thread to die.
   */
  private void disposeConsole() throws InterruptedException, ExecutionException {
    try {
      disposeConsoleAsync().get();
    }
    finally {
      // Even if console failed in its side we need
      if (myConsoleView != null) {
        ApplicationManager.getApplication().invokeAndWait(() -> Disposer.dispose(myConsoleView), ModalityState.defaultModalityState());
        myConsoleView = null;
      }
    }
  }

  @NotNull
  private Future<Void> disposeConsoleAsync() {
    Future<Void> shutdownFuture;
    if (myCommunication != null) {
      shutdownFuture = UIUtil.invokeAndWaitIfNeeded(() -> {
        try {
          return myCommunication.closeAsync();
        }
        finally {
          myCommunication = null;
        }
      });
    }
    else {
      shutdownFuture = CompletableFuture.completedFuture(null);
    }

    disposeConsoleProcess();

    if (!myContentDescriptorRef.isNull()) {
      ApplicationManager.getApplication().invokeAndWait(() -> Disposer.dispose(myContentDescriptorRef.get()),
                                                        ModalityState.defaultModalityState());
    }

    if (myConsoleView != null) {
      WriteAction.runAndWait(() -> {
        Disposer.dispose(myConsoleView);
        myConsoleView = null;
      });
    }

    return shutdownFuture;
  }

  @Override
  public void runTestOn(@NotNull final String sdkHome, @Nullable Sdk existingSdk) throws Exception {
    setProcessCanTerminate(false);

    PydevConsoleRunner consoleRunner = PythonConsoleRunnerFactory.getInstance().createConsoleRunner(getProject(), myFixture.getModule());

    before();

    myConsoleInitSemaphore = new Semaphore(0);

    consoleRunner.addConsoleListener(new PydevConsoleRunnerImpl.ConsoleListener() {
      @Override
      public void handleConsoleInitialized(LanguageConsoleView consoleView) {
        myConsoleInitSemaphore.release();
      }
    });

    consoleRunner.run(true);

    waitFor(myConsoleInitSemaphore);

    myCommandSemaphore = new Semaphore(1);

    myConsoleView = consoleRunner.getConsoleView();
    Disposer.register(myFixture.getProject(), myConsoleView);
    myProcessHandler = consoleRunner.getProcessHandler();

    myExecuteHandler = consoleRunner.getConsoleExecuteActionHandler();

    myCommunication = consoleRunner.getPydevConsoleCommunication();

    myCommunication.addCommunicationListener(new ConsoleCommunicationListener() {
      @Override
      public void commandExecuted(boolean more) {
        LOG.debug("Some command executed");
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

  private void disposeConsoleProcess() {
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
      Thread.sleep(2000);
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
    LOG.debug("Command " + command + " acquired lock");
    Assert.assertTrue(String.format("Can't execute command: `%s`, because previous one wan't finished \n" +
                                    "Output: %s", command, output()), waitFor(myCommandSemaphore));
    LOG.debug("Command " + command + " got lock");
    myConsoleView.executeInConsole(command);
  }

  protected boolean hasValue(String varName, String value) throws PyDebuggerException, InterruptedException {
    PyDebugValue val = getValue(varName);
    return val != null && value.equals(val.getValue());
  }

  protected void setValue(String varName, String value) throws PyDebuggerException, InterruptedException {
    PyDebugValue val = getValue(varName);
    assertThat(waitFor(myCommandSemaphore))
      .describedAs(String.format("Can't change variable's value: `%s` \n" + "Output: %s", varName, output()))
      .isTrue();
    myCommunication.changeVariable(val, value);
    myCommandSemaphore.release();
  }

  protected PyDebugValue getValue(String varName) throws PyDebuggerException, InterruptedException {
    Assert.assertTrue(String.format("Can't get value for variable: `%s` \n" +
                                    "Output: %s", varName, output()), waitFor(myCommandSemaphore));
    XValueChildrenList l = myCommunication.loadFrame();
    myCommandSemaphore.release();

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
    myConsoleView.executeCode(command, null);
  }

  protected void interrupt() {
    myCommunication.interrupt();
  }


  public void addTextToEditor(final String text) {
    TransactionGuard.getInstance().submitTransactionAndWait(() -> {
      getConsoleView().setInputText(text);
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    });
  }
}
