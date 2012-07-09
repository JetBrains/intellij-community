package com.jetbrains.env.python.console;

import com.google.common.collect.Lists;
import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.env.python.debug.PyExecutionFixtureTestTask;
import com.jetbrains.python.console.*;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.List;
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
  private Semaphore mySemaphore;
  private Semaphore mySemaphore0;
  private PydevConsoleExecuteActionHandler myExecuteHandler;

  public PyConsoleTask() {
    setWorkingFolder(getTestDataPath());
  }

  public PyConsoleTask(String workingFolder, String scriptName, String scriptParameters) {
    setWorkingFolder(getTestDataPath() + workingFolder);
    setScriptParameters(scriptParameters);
  }

  public PyConsoleTask(String workingFolder) {
    this(workingFolder, null, null);
  }

  public PythonConsoleView getConsoleView() {
    return myConsoleView;
  }

  @Override
  public void setUp() throws Exception {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          if (myFixture == null) {
            PyConsoleTask.super.setUp();
          }
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  @NotNull
  protected String output() {
    return myConsoleView.getConsole().getHistoryViewer().getDocument().getText();
  }

  private void outputContains(String substring) {
    Assert.assertTrue(output().contains(substring));
  }

  public void setProcessCanTerminate(boolean processCanTerminate) {
    myProcessCanTerminate = processCanTerminate;
  }

  @Override
  public void tearDown() throws Exception {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      public void run() {
        try {
          if (myConsoleView != null) {
            disposeConsole();
          }
          PyConsoleTask.super.tearDown();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  private void disposeConsole() throws InterruptedException {
    if (myCommunication != null) {
      try {
        myCommunication.close();
      }
      catch (Exception e) {
        e.printStackTrace();
      }
      myCommunication = null;
    }

    disposeConsoleProcess();

    if (myConsoleView != null) {
      new WriteAction() {
        protected void run(Result result) throws Throwable {
          Disposer.dispose(myConsoleView);
          myConsoleView = null;

        }
      }.execute();
    }
  }

  public void runTestOn(final String sdkHome) throws Exception {
    final Project project = getProject();

    SdkType sdkType = PythonSdkType.getInstance();

    final Sdk sdk = new ProjectJdkImpl("Python Test Sdk " + sdkHome, sdkType) {
      @Override
      public String getHomePath() {
        return sdkHome;
      }

      @Override
      public String getVersionString() {
        return "Python 2 Mock SDK";
      }
    };

    setProcessCanTerminate(false);

    PydevConsoleRunner consoleRunner = PydevConsoleRunner.create(project, sdk, PyConsoleType.PYTHON, getWorkingFolder());

    before();

    mySemaphore0 = new Semaphore(0);

    consoleRunner.addConsoleListener(new PydevConsoleRunner.ConsoleListener() {
      @Override
      public void handleConsoleInitialized(LanguageConsoleViewImpl consoleView) {
        mySemaphore0.release();
      }
    });

    consoleRunner.run();

    waitFor(mySemaphore0);

    mySemaphore = new Semaphore(0);

    myConsoleView = consoleRunner.getConsoleView();
    myProcessHandler = (PyConsoleProcessHandler)consoleRunner.getProcessHandler();

    myExecuteHandler = (PydevConsoleExecuteActionHandler)consoleRunner.getConsoleExecuteActionHandler();

    myCommunication = consoleRunner.getPydevConsoleCommunication();

    myCommunication.addCommunicationListener(new ConsoleCommunicationListener() {
      @Override
      public void executionFinished() {
        mySemaphore.release();
      }

      @Override
      public void inputRequested() {
      }
    });

    myProcessHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(ProcessEvent event) {
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

    testing();

    after();

    setProcessCanTerminate(true);

    if (myOutputPrinter != null) {
      myOutputPrinter.stop();
    }

    disposeConsole();
  }

  private void disposeConsoleProcess() throws InterruptedException {
    myProcessHandler.destroyProcess();
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
   * If they don't appear in timelimit, then exception is raised.
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
      myThread = new Thread(new Runnable() {
        @Override
        public void run() {
          doJob();
        }
      });
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
      catch (Exception e) {
      }
    }

    private synchronized void printToConsole() {
      String s = output();
      if (s.length() > myLen) {
        System.out.print(s.substring(myLen));
      }
      myLen = s.length();
    }

    public void stop() {
      printToConsole();
      myThread.interrupt();
    }
  }

  protected void exec(String command) throws InterruptedException {
    waitForReady();
    int p = mySemaphore.availablePermits();
    myConsoleView.executeInConsole(command);
    mySemaphore.acquire(p + 1);
    //waitForOutput(">>> " + command);
  }

  protected void input(String text) {
    myConsoleView.executeInConsole(text);
  }

  protected void waitForFinish() throws InterruptedException {
    waitFor(mySemaphore);
  }

  protected void execNoWait(String command) {
    myConsoleView.executeCode(command);
  }

  protected void interrupt() {
    myCommunication.interrupt();
  }


  public void addTextToEditor(final String text) {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        getConsoleView().getLanguageConsole().addTextToCurrentEditor(text);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      }
    }
    );
  }
}
