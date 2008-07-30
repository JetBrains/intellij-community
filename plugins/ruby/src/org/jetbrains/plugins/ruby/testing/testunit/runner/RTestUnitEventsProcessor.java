package org.jetbrains.plugins.ruby.testing.testunit.runner;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.TestResultsViewer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author: Roman Chernyatchik
 */
public class RTestUnitEventsProcessor {
  private static final Logger LOG = Logger.getInstance(RTestUnitEventsProcessor.class.getName());

  private final TestResultsViewer myResultsViewer;
  private final Map<String, RTestUnitTestProxy> myRunningTestsFullNameToProxy = new HashMap<String, RTestUnitTestProxy>();

  private int myTestsCurrentCount;
  private int myTestsTotal;
  private long myStartTime;
  private long myEndTime;

  private final Set<AbstractTestProxy> myFailedTestsSet = new HashSet<AbstractTestProxy>();
  private final TestSuiteStack mySuitesStack = new TestSuiteStack();

  public RTestUnitEventsProcessor(final TestResultsViewer resultsViewer) {
    myResultsViewer = resultsViewer;
  }

  public void onStartTesting() {
    // prepare view
    myResultsViewer.onStartTesting();

    myStartTime = System.currentTimeMillis();

    final RTestUnitTestProxy testsRootNode = myResultsViewer.getTestsRootNode();
    mySuitesStack.pushSuite(testsRootNode);
    testsRootNode.setStarted();
    updateProgress();
  }

  public void onFinishTesting() {
    if (myEndTime > 0) {
      // has been already invoked!
      return;
    }
    myEndTime = System.currentTimeMillis();
    updateProgress();

    final RTestUnitTestProxy testsRootNode = myResultsViewer.getTestsRootNode();
    testsRootNode.setFinished();

    // update view
    myResultsViewer.onFinishTesting();

    mySuitesStack.popSuite(testsRootNode.getName());
    LOG.assertTrue(mySuitesStack.getStackSize() == 0);
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

    // Prerequisites
    myTestsCurrentCount++;
    // fix total count if it is corrupted
    if (myTestsCurrentCount > myTestsTotal) {
      myTestsTotal = myTestsCurrentCount;
    }

    //Progress started
    testProxy.setStarted();
    // update tree
    myResultsViewer.onTestStarted(testProxy, myTestsTotal, myTestsCurrentCount);
    // update progress bar
    updateProgress();
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

    //update tree
    myResultsViewer.onSuiteStarted(newSuite);
  }

  public void onTestFinish(final String testName) {
    final String fullTestName = getFullTestName(testName);
    final RTestUnitTestProxy testProxy = getProxyByFullTestName(fullTestName);

    testProxy.setFinished();
    myRunningTestsFullNameToProxy.remove(fullTestName);
  }

  public void onTestSuiteFinish(final String suiteName) {
    final RTestUnitTestProxy mySuite = mySuitesStack.popSuite(suiteName);
    mySuite.setFinished();
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
   
    // update progress bar
    updateProgress();
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
    myTestsTotal = count;
  }

  protected String getFullTestName(final String testName) {
    return getCurrentTestSuite().getName() + testName;
  }

  protected Map<String, RTestUnitTestProxy> getRunningTestsFullNameToProxy() {
    return myRunningTestsFullNameToProxy;
  }

  protected int getTestsCurrentCount() {
    return myTestsCurrentCount;
  }

  protected int getTestsTotal() {
    return myTestsTotal;
  }

  protected long getStartTime() {
    return myStartTime;
  }

  protected long getEndTime() {
    return myEndTime;
  }

  protected Set<AbstractTestProxy> getFailedTestsSet() {
    return myFailedTestsSet;
  }

  protected RTestUnitTestProxy getProxyByFullTestName(final String fullTestName) {
    final RTestUnitTestProxy testProxy = myRunningTestsFullNameToProxy.get(fullTestName);
    LOG.assertTrue(testProxy != null);
    return testProxy;
  }

  private void updateProgress() {
    myResultsViewer.updateStatusLabel(myStartTime, myEndTime, myTestsTotal,
                                      myTestsCurrentCount, myFailedTestsSet);
  }  
}
