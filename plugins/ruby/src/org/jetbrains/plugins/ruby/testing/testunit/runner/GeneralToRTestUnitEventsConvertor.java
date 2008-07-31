package org.jetbrains.plugins.ruby.testing.testunit.runner;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author: Roman Chernyatchik
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
    mySuitesStack.pushSuite(myTestsRootNode);
    myTestsRootNode.setStarted();

    //fire
    fireOnTestingStarted();
  }

  public void onFinishTesting() {
    if (myIsTestingFinished) {
      // has been already invoked!
      return;
    }
    myIsTestingFinished = true;

    myTestsRootNode.setFinished();
    mySuitesStack.popSuite(myTestsRootNode.getName());
    LOG.assertTrue(mySuitesStack.getStackSize() == 0);

    //fire events
    fireOnTestingFinished();
  }

  public void onTestStart(final String testName) {

    final String fullName = getFullTestName(testName);

    if (myRunningTestsFullNameToProxy.containsKey(fullName)) {
      //Dublicated event
      LOG.warn("Test [" + fullName + "] has been already started");
      return;
    }

    final RTestUnitTestProxy parentSuite = getCurrentTestSuite();

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

  @NotNull
  private RTestUnitTestProxy getCurrentTestSuite() {
    final RTestUnitTestProxy parentSuite = mySuitesStack.getCurrentSuite();
    assert parentSuite != null;

    return parentSuite;
  }

  public void onTestSuiteStart(final String suiteName) {
    final RTestUnitTestProxy parentSuite = getCurrentTestSuite();
    //new suite
    final RTestUnitTestProxy newSuite = new RTestUnitTestProxy(suiteName, true);
    parentSuite.addChild(newSuite);

    mySuitesStack.pushSuite(newSuite);

    //Progress started
    newSuite.setStarted();

    //fire event
    fireOnSuiteStarted(newSuite);
  }

  public void onTestFinish(final String testName) {
    final String fullTestName = getFullTestName(testName);
    final RTestUnitTestProxy testProxy = getProxyByFullTestName(fullTestName);

    testProxy.setFinished();
    myRunningTestsFullNameToProxy.remove(fullTestName);

    //fire events
    fireOnTestFinished(testProxy);
  }

  public void onTestSuiteFinish(final String suiteName) {
    final RTestUnitTestProxy mySuite = mySuitesStack.popSuite(suiteName);
    mySuite.setFinished();

    //fire events
    fireOnSuiteFinished(mySuite);
  }

  public void onTestFailure(final String testName,
                            final String localizedMessage, final String stackTrace) {

    final String fullTestName = getFullTestName(testName);
    final RTestUnitTestProxy testProxy = getProxyByFullTestName(fullTestName);

    // if wasn't processed
    if (myFailedTestsSet.contains(testProxy)) {
      // dublicate message
      LOG.warn("Dublicate failure for test [" + fullTestName  + "]: msg = " + localizedMessage + ", stacktrace = " + stackTrace);
      return;
    }

    testProxy.setTestFailed(localizedMessage, stackTrace);


    myFailedTestsSet.add(testProxy);

    // fire event
    fireOnTestFailed(testProxy);
  }

  public void onTestOutput(final String testName,
                           final String text, final boolean stdOut) {
    final String fullTestName = getFullTestName(testName);
    final RTestUnitTestProxy testProxy = getProxyByFullTestName(fullTestName);

    if (stdOut) {
      testProxy.addStdOutput(text);
    } else {
      testProxy.addStdErr(text);
    }
  }

  public void onTestsCount(final int count) {
    fireOnTestsCount(count);
  }

  protected String getFullTestName(final String testName) {
    return getCurrentTestSuite().getName() + testName;
  }

  protected int getRunningTestsQuantity() {
    return myRunningTestsFullNameToProxy.size();
  }

  protected Set<AbstractTestProxy> getFailedTestsSet() {
    return Collections.unmodifiableSet(myFailedTestsSet);
  }

  protected RTestUnitTestProxy getProxyByFullTestName(final String fullTestName) {
    final RTestUnitTestProxy testProxy = myRunningTestsFullNameToProxy.get(fullTestName);
    LOG.assertTrue(testProxy != null);
    return testProxy;
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

  private void fireOnTestsCount(final int count) {
    for (RTestUnitEventsListener listener : myEventsListeners) {
      listener.onTestsCount(count);
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
  public void cleanupOnProcessTerminated() {
    myEventsListeners.clear();
    myRunningTestsFullNameToProxy.clear();
    mySuitesStack.clear();        
  }

}
