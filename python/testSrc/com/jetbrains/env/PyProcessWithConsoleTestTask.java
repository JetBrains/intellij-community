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
package com.jetbrains.env;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.xdebugger.XDebuggerTestUtil;
import com.jetbrains.python.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.lang.reflect.InvocationTargetException;

/**
 * <h1>Task that knows how to execute some process with console.</h1>
 * <p>
 * It delegates execution process to {@link ProcessWithConsoleRunner},  there are a lot of runners.
 * This task gives you access to both process output streams while runner should also provide access to console.
 * To use it, you need 3 things:
 * </p>
 * <ol>
 * <li>Inherit it</li>
 * <li>Override {@link #createProcessRunner()} and return appropriate test runner</li>
 * <li>Override {@link #checkTestResults(ProcessWithConsoleRunner, String, String, String)} and check result using arguments or
 * {@link ProcessWithConsoleRunner#getConsole()} or something else (be sure to check all runner methods)</li>
 * </ol>
 * <p>
 * You may optionally override several other methods, like {@link #prepare()} (which is launched before test), check all non-final methods
 * </p>
 *
 * @param <T> expected process runner class
 * @author Ilya.Kazakevich
 */
public abstract class PyProcessWithConsoleTestTask<T extends ProcessWithConsoleRunner> extends PyExecutionFixtureTestTask {
  @NotNull
  private final SdkCreationType myRequiredSdkType;

  /**
   * @param requiredSdkType this task creates sdk and binds it to fixture module. Provide type of SDK your test needs.
   *                        Always use as light sdk as possible (it is faster)
   * @see SdkCreationType
   */
  protected PyProcessWithConsoleTestTask(@NotNull final SdkCreationType requiredSdkType) {
    myRequiredSdkType = requiredSdkType;
  }

  @Override
  public void runTestOn(final String sdkHome) throws Exception {
    createTempSdk(sdkHome, myRequiredSdkType);
    prepare();
    final T runner = createProcessRunner();
    do {
      executeRunner(sdkHome, runner);
    }
    while (runner.shouldRunAgain());
    Disposer.dispose(runner);
  }

  private void executeRunner(final String sdkHome, final T runner) throws InterruptedException, InvocationTargetException {
    // Semaphore to wait end of process
    final Semaphore processFinishedSemaphore = new Semaphore();
    processFinishedSemaphore.down();
    final StringBuilder stdOut = new StringBuilder();
    final StringBuilder stdErr = new StringBuilder();
    final StringBuilder stdAll = new StringBuilder();
    final Ref<Boolean> failed = new Ref<Boolean>(false);

    final ProcessAdapter processListener = new ProcessAdapter() {
      @Override
      public void processTerminated(final ProcessEvent event) {
        super.processTerminated(event);
        processFinishedSemaphore.up();
      }

      @Override
      public void onTextAvailable(final ProcessEvent event, final Key outputType) {
        super.onTextAvailable(event, outputType); //Store text for user
        final String text = event.getText();
        stdAll.append(text);
        if (outputType.equals(ProcessOutputTypes.STDOUT)) {
          stdOut.append(text);
        }
        else if (outputType.equals(ProcessOutputTypes.STDERR)) {
          stdErr.append(text);
        }
      }
    };

    // Invoke runner
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        try {
          runner.runProcess(sdkHome, getProject(), processListener);
        }
        catch (final Throwable e) {
          final IllegalStateException exception = new IllegalStateException("Exception thrown while running test", e);
          failed.set(true);
          processFinishedSemaphore.up();
          throw exception;
        }
      }
    }, ModalityState.NON_MODAL);


    processFinishedSemaphore.waitFor(60000);
    XDebuggerTestUtil.waitForSwing();
    if (failed.get()) {
      Assert.fail("Failed to run test, see logs for exceptions");
    } else {
      checkTestResults(runner, stdOut.toString(), stdErr.toString(), stdAll.toString());
    }
  }

  /**
   * Called right before process run. Has access to {@link #myFixture}.
   * Always call parent when overwrite.
   */
  protected void prepare() {
  }


  /**
   * @return process runner to be used to run process and fetch console
   */
  @NotNull
  protected abstract T createProcessRunner() throws Exception;


  /**
   * Process is finished. Do all checks you need to make sure your test passed.
   *
   * @param runner runner used to run process. You may access {@link ProcessWithConsoleRunner#getConsole()} and other useful staff.
   *               Check concrete runner documentation
   * @param stdout process stdout
   * @param stderr process stderr
   * @param all    joined stdout and stderr
   */
  protected abstract void checkTestResults(@NotNull T runner, @NotNull String stdout, @NotNull String stderr, @NotNull String all);
}
