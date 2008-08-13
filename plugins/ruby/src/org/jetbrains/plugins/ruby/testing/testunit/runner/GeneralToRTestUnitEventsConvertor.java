package org.jetbrains.plugins.ruby.testing.testunit.runner;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.support.UIUtil;

import java.util.*;

/**
 * @author: Roman Chernyatchik
 *
 * This class fires events to RTestUnitEventsListener in EventDispatch thread
 */
public class GeneralToRTestUnitEventsConvertor implements GeneralTestEventsProcessor {
  private static final Logger LOG = Logger.getInstance(GeneralToRTestUnitEventsConvertor.class.getName());

  private final Map<String, RTestUnitTestProxy> myRunningTestsFullNameToProxy = new HashMap<String, RTestUnitTestProxy>();

  private final Set<AbstractTestProxy> myFailedTestsSet = new HashSet<AbstractTestProxy>();
  private final TestSuiteStack mySuitesStack = new TestSuiteStack();
  private final List<RTestUnitEventsListener> myEventsListeners = new ArrayList<RTestUnitEventsListener>();
  private RTestUnitTestProxy myTestsRootNode;
  private boolean myIsTestingFinished;

  public GeneralToRTestUnitEventsConvertor(@NotNull final RTestUnitTestProxy testsRootNode) {
    myTestsRootNode = testsRootNode;
  }

  public void addEventsListener(final RTestUnitEventsListener listener) {
    myEventsListeners.add(listener);
  }

  public void onStartTesting() {
    UIUtil.addToInvokeLater(new Runnable() {
      public void run() {
        mySuitesStack.pushSuite(myTestsRootNode);
        myTestsRootNode.setStarted();

        //fire
        fireOnTestingStarted();
      }
    });
  }

  public void onFinishTesting() {
    UIUtil.addToInvokeLater(new Runnable() {
      public void run() {
        if (myIsTestingFinished) {
          // has been already invoked!
          return;
        }
        myIsTestingFinished = true;

        // We don't know whether process was destroyed by user
        // or it finished after all tests have been run
        // Lets assume, if at finish all suites except root suite are passed
        // then all is ok otherwise process was terminated by user
        if (!myTestsRootNode.equals(mySuitesStack.getCurrentSuite())) {
          myTestsRootNode.setTerminated();
          myRunningTestsFullNameToProxy.clear();
        }
        mySuitesStack.clear();
        myTestsRootNode.setFinished();


        //fire events
        fireOnTestingFinished();
      }
    });
  }

  public void onTestStarted(final String testName) {
    UIUtil.addToInvokeLater(new Runnable() {
      public void run() {
        final String fullName = getFullTestName(testName);

        if (myRunningTestsFullNameToProxy.containsKey(fullName)) {
          //Dublicated event
          LOG.warn("Test [" + fullName + "] has been already started");
          return;
        }

        final RTestUnitTestProxy parentSuite = getCurrentSuite();

        // creates test
        final RTestUnitTestProxy testProxy = new RTestUnitTestProxy(testName, false);
        parentSuite.addChild(testProxy);
        // adds to running tests map
        myRunningTestsFullNameToProxy.put(fullName, testProxy);

        //Progress started
        testProxy.setStarted();

        //fire events
        fireOnTestStarted(testProxy);
      }
    });
  }

  public void onSuiteStarted(final String suiteName) {
    UIUtil.addToInvokeLater(new Runnable() {
      public void run() {
        final RTestUnitTestProxy parentSuite = getCurrentSuite();
        //new suite
        final RTestUnitTestProxy newSuite = new RTestUnitTestProxy(suiteName, true);
        parentSuite.addChild(newSuite);

        mySuitesStack.pushSuite(newSuite);

        //Progress started
        newSuite.setStarted();

        //fire event
        fireOnSuiteStarted(newSuite);
      }
    });
  }

  public void onTestFinished(final String testName, final int duration) {
    UIUtil.addToInvokeLater(new Runnable() {
      public void run() {
        final String fullTestName = getFullTestName(testName);
        final RTestUnitTestProxy testProxy = getProxyByFullTestName(fullTestName);
        if (testProxy == null) {
          LOG.error("Test wasn't started! TestFinished event: name = {" + testName + "}");
          return;
        }

        //TODO pass duration
        testProxy.setFinished();
        myRunningTestsFullNameToProxy.remove(fullTestName);

        //fire events
        fireOnTestFinished(testProxy);
      }
    });
  }

  public void onSuiteFinished(final String suiteName) {
    UIUtil.addToInvokeLater(new Runnable() {
      public void run() {
        final RTestUnitTestProxy mySuite = mySuitesStack.popSuite(suiteName);
        mySuite.setFinished();

        //fire events
        fireOnSuiteFinished(mySuite);
      }
    });
  }

  public void onUncapturedOutput(final String text, final Key outputType) {
    UIUtil.addToInvokeLater(new Runnable() {
      public void run() {
        //if we can locate test - we will send outout to it, otherwise to current test suite
        final RTestUnitTestProxy currentProxy;
        if (myRunningTestsFullNameToProxy.size() == 1) {
          //current test
          currentProxy = myRunningTestsFullNameToProxy.values().iterator().next();
        } else {
          //current suite
          //
          // ProcessHandler can fire output available event before processStarted event
          currentProxy = mySuitesStack.isEmpty() ? myTestsRootNode : getCurrentSuite();
        }

        if (ProcessOutputTypes.STDOUT.equals(outputType)) {
          currentProxy.addStdOutput(text);
        } else if (ProcessOutputTypes.STDERR.equals(outputType)) {
          currentProxy.addStdErr(text);
        } else if (ProcessOutputTypes.SYSTEM.equals(outputType)) {
          currentProxy.addSystemOutput(text);
        }
      }
    });
  }

  public void onTestFailure(final String testName,
                            final String localizedMessage, final String stackTrace,
                            final boolean isTestError) {
    UIUtil.addToInvokeLater(new Runnable() {
      public void run() {
        final String fullTestName = getFullTestName(testName);
        final RTestUnitTestProxy testProxy = getProxyByFullTestName(fullTestName);
        if (testProxy == null) {
          LOG.error("Test wasn't started! TestFailure event: name = {" + testName + "}, message = {" + localizedMessage + "}, stackTrace = {" + stackTrace + "}");
          return;
        }
        // if wasn't processed
        if (myFailedTestsSet.contains(testProxy)) {
          // dublicate message
          LOG.warn("Dublicate failure for test [" + fullTestName  + "]: msg = " + localizedMessage + ", stacktrace = " + stackTrace);
          return;
        }

        testProxy.setTestFailed(localizedMessage, stackTrace, isTestError);


        myFailedTestsSet.add(testProxy);

        // fire event
        fireOnTestFailed(testProxy);
      }
    });
  }

  public void onTestOutput(final String testName,
                           final String text, final boolean stdOut) {
    UIUtil.addToInvokeLater(new Runnable() {
      public void run() {
        final String fullTestName = getFullTestName(testName);
        final RTestUnitTestProxy testProxy = getProxyByFullTestName(fullTestName);
        if (testProxy == null) {
          LOG.error("Test wasn't started! TestOutput event: name = {" + testName + "}, isStdOut = " + stdOut + ", text = {" + text + "}");
          return;
        }

        if (stdOut) {
          testProxy.addStdOutput(text);
        } else {
          testProxy.addStdErr(text);
        }
      }
    });
  }

  public void onTestsCountInSuite(final int count) {
    UIUtil.addToInvokeLater(new Runnable() {
      public void run() {
        fireOnTestsCountInSuite(count);
      }
    });
  }

  @NotNull
  protected final RTestUnitTestProxy getCurrentSuite() {
    final RTestUnitTestProxy currentSuite = mySuitesStack.getCurrentSuite();
    assert currentSuite != null;

    return currentSuite;
  }
 
  protected String getFullTestName(final String testName) {
    return getCurrentSuite().getName() + testName;
  }

  protected int getRunningTestsQuantity() {
    return myRunningTestsFullNameToProxy.size();
  }

  protected Set<AbstractTestProxy> getFailedTestsSet() {
    return Collections.unmodifiableSet(myFailedTestsSet);
  }

  @Nullable
  protected RTestUnitTestProxy getProxyByFullTestName(final String fullTestName) {
    final RTestUnitTestProxy testProxy = myRunningTestsFullNameToProxy.get(fullTestName);
    if (testProxy == null) {
      LOG.error("Cant find running test for ["
                + fullTestName
                + "]. Current running tests: {"
                + dumpRunningTestsNames() + "}");
    }
    return testProxy;
  }

  private StringBuilder dumpRunningTestsNames() {
    final Set<String> names = myRunningTestsFullNameToProxy.keySet();
    final StringBuilder namesDump = new StringBuilder();
    for (String name : names) {
      namesDump.append('[').append(name).append(']').append(',');
    }
    return namesDump;
  }

  private void fireOnTestingStarted() {
    for (RTestUnitEventsListener listener : myEventsListeners) {
      listener.onTestingStarted();
    }
  }

  private void fireOnTestingFinished() {
    for (RTestUnitEventsListener listener : myEventsListeners) {
      listener.onTestingFinished();
    }
  }

  private void fireOnTestsCountInSuite(final int count) {
    for (RTestUnitEventsListener listener : myEventsListeners) {
      listener.onTestsCountInSuite(count);
    }
  }


  private void fireOnTestStarted(final RTestUnitTestProxy test) {
    for (RTestUnitEventsListener listener : myEventsListeners) {
      listener.onTestStarted(test);
    }
  }

  private void fireOnTestFinished(final RTestUnitTestProxy test) {
    for (RTestUnitEventsListener listener : myEventsListeners) {
      listener.onTestFinished(test);
    }
  }

  private void fireOnTestFailed(final RTestUnitTestProxy test) {
    for (RTestUnitEventsListener listener : myEventsListeners) {
      listener.onTestFailed(test);
    }
  }

  private void fireOnSuiteStarted(final RTestUnitTestProxy suite) {
    for (RTestUnitEventsListener listener : myEventsListeners) {
      listener.onSuiteStarted(suite);
    }
  }

  private void fireOnSuiteFinished(final RTestUnitTestProxy suite) {
    for (RTestUnitEventsListener listener : myEventsListeners) {
      listener.onSuiteFinished(suite);
    }
  }

  /*
   * Remove listeners,  etc
   */
  public void dispose() {
    UIUtil.addToInvokeLater(new Runnable() {
      public void run() {
        myEventsListeners.clear();

        if (!myRunningTestsFullNameToProxy.isEmpty()) {
          final Application application = ApplicationManager.getApplication();
          if (!application.isHeadlessEnvironment() && !application.isUnitTestMode()) {
            LOG.error("Not all events were processed! " + dumpRunningTestsNames());
          }
        }
        myRunningTestsFullNameToProxy.clear();
        mySuitesStack.clear();
      }
    });
  }
}
