// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ModuleRunConfiguration;
import com.intellij.execution.testframework.sm.runner.GeneralIdBasedToSMTRunnerEventsConvertor;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter;
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.events.TestDurationStrategy;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman.Chernyatchik
 */
public class PythonTRunnerConsoleProperties extends SMTRunnerConsoleProperties {
  public static final String FRAMEWORK_NAME = "PythonUnitTestRunner";

  private final boolean myIsEditable;
  private final SMTestLocator myLocator;

  /**
   * @param editable if user should have ability to print something to test stdin
   */
  public PythonTRunnerConsoleProperties(@NotNull ModuleRunConfiguration config,
                                        @NotNull Executor executor,
                                        boolean editable,
                                        @Nullable SMTestLocator locator) {
    super(config, FRAMEWORK_NAME, executor);
    myIsEditable = editable;
    myLocator = locator;
  }

  @Override
  public boolean isEditable() {
    return myIsEditable;
  }

  @Override
  public @Nullable SMTestLocator getTestLocator() {
    return myLocator;
  }

  /**
   * Makes configuration id-based,
   *
   * @see GeneralIdBasedToSMTRunnerEventsConvertor
   */
  final void makeIdTestBased() {
    setIdBasedTestTree(true);
    getProject().getMessageBus().connect(this)
      .subscribe(SMTRunnerEventsListener.TEST_STATUS, new MySMTRunnerEventsAdapter());
  }

  private static final class MySMTRunnerEventsAdapter extends SMTRunnerEventsAdapter {

    private final long myStarted = System.currentTimeMillis();



    @Override
    public void onBeforeTestingFinished(final @NotNull SMTestProxy.SMRootTestProxy testsRoot) {
      // manual duration for root means root must have wall time
      if (testsRoot.getDurationStrategy() == TestDurationStrategy.MANUAL) {
        testsRoot.setDuration(System.currentTimeMillis() - myStarted);
      }
      if (testsRoot.isEmptySuite()) {
        testsRoot.setPresentation(getEmptySuite());
        testsRoot.setTestFailed(getEmptySuite(), null, false);
      }
      super.onBeforeTestingFinished(testsRoot);
    }

    private static String getEmptySuite() {
      return PyBundle.message("runcfg.tests.empty_suite");
    }
  }
}
