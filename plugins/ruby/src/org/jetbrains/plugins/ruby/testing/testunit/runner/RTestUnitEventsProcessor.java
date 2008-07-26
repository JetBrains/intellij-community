package org.jetbrains.plugins.ruby.testing.testunit.runner;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.RTestUnitResultsForm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author: Roman Chernyatchik
 */
public class RTestUnitEventsProcessor {
  private static final Logger LOG = Logger.getInstance(RTestUnitEventsProcessor.class.getName());

  private final RTestUnitResultsForm myResultsForm;
  private final Map<String, RTestUnitTestProxy> myRunningTestsFullNameToProxy = new HashMap<String, RTestUnitTestProxy>();

  private int myTestsCurrentCount;
  private int myTestsTotal;
  private long myStartTime;
  private long myEndTime;

  private final Set<AbstractTestProxy> myFailedTestsSet = new HashSet<AbstractTestProxy>();

  public RTestUnitEventsProcessor(final RTestUnitResultsForm resultsForm) {
    myResultsForm = resultsForm;
  }

  public void onStartTesting() {
    // prepare view
    myResultsForm.startTesting();

    myStartTime = System.currentTimeMillis();
    myResultsForm.getTestsRootNode().setStarted();
    updateProgress();
  }

  public void onFinishTesting() {
    if (myEndTime > 0) {
      // has been already invoked!
      return;
    }
    myEndTime = System.currentTimeMillis();
    updateProgress();

    myResultsForm.getTestsRootNode().setFinished();
    // update view
    myResultsForm.finishTesting();
  }

  public void onTestStart(final String testName) {

    final String fullName = getFullTestName(testName);

    if (myRunningTestsFullNameToProxy.containsKey(fullName)) {
      //Dublicated event
      LOG.warn("Test [" + fullName + "] has been already started");
      return;
    }

    final RTestUnitTestProxy testProxy = new RTestUnitTestProxy(testName, false);
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
    myResultsForm.addTestNode(testProxy, myTestsTotal, myTestsCurrentCount);
    // update progress bar
    updateProgress();
  }

  public void onTestSuiteStart(final String suiteName) {
    //TODO[romeo] implement
  }

  public void onTestFinish(final String testName) {
    final String fullTestName = getFullTestName(testName);
    final RTestUnitTestProxy testProxy = getProxyByFullTestName(fullTestName);

    testProxy.setFinished();
    myRunningTestsFullNameToProxy.remove(fullTestName);
  }

  public void onTestSuiteFinish(final String suiteName) {
    //TODO[romeo] implement
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
    //TODO[romeo]
    return testName;
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
    myResultsForm.updateStatusLabel(myStartTime, myEndTime, myTestsTotal,
                                    myTestsCurrentCount, myFailedTestsSet);
  }  
}
