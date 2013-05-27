package org.testng;

import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes;
import org.testng.internal.IResultListener;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * User: anna
 * Date: 5/22/13
 */
public class IDEATestNGRemoteListener implements ISuiteListener, IResultListener{

  private String myCurrentClassName;

  public void onConfigurationSuccess(ITestResult itr) {
    //won't be called
  }

  public void onConfigurationFailure(ITestResult itr) {
    //won't be called
  }

  public void onConfigurationSkip(ITestResult itr) {
    //won't be called
  }

  public void onStart(ISuite suite) {
    System.out.println("##teamcity[testSuiteStarted name =\'" + suite.getName() + "\']");
  }

  public void onFinish(ISuite suite) {
    System.out.println("##teamcity[testSuiteFinished name=\'" + suite.getName() + "\']");
  }

  public void onTestStart(ITestResult result) {
    final String className = result.getTestClass().getName();
    if (myCurrentClassName == null || !myCurrentClassName.equals(className)) {
      if (myCurrentClassName != null) {
        System.out.println("##teamcity[testSuiteFinished name=\'" + myCurrentClassName + "\']");
      }
      System.out.println("##teamcity[testSuiteStarted name =\'" + className + "\']");
      myCurrentClassName = className;
    }
    System.out.println("##teamcity[testStarted name=\'" + result.getMethod().getMethodName() + "\']");
  }

  public void onTestSuccess(ITestResult result) {
    System.out.println("##teamcity[testFinished name=\'" + result.getMethod().getMethodName() + "\']");
  }

  public String getTrace(Throwable tr) {
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    tr.printStackTrace(writer);
    StringBuffer buffer = stringWriter.getBuffer();
    return buffer.toString();
  }

  public void onTestFailure(ITestResult result) {
    final Throwable ex = result.getThrowable();
    final String trace = getTrace(ex);
    final Map<String, String> attrs = new HashMap<String, String>();
    attrs.put("name", result.getMethod().getMethodName());
    final String failureMessage = ex.getMessage();
    attrs.put("message", failureMessage != null ? failureMessage : "");
    attrs.put("details", trace);
    attrs.put("error", "true");
    System.out.println(ServiceMessage.asString(ServiceMessageTypes.TEST_FAILED, attrs));
    System.out.println("##teamcity[testFinished name=\'" + result.getMethod().getMethodName() + "\']");
  }

  public void onTestSkipped(ITestResult result) {
    System.out.println("##teamcity[testFinished name=\'" + result.getMethod().getMethodName() + "\']");
  }

  public void onTestFailedButWithinSuccessPercentage(ITestResult result) {

  }

  public void onStart(ITestContext context) {
    //System.out.println("##teamcity[testSuiteStarted name =\'" + context.getName() + "\']");
  }

  public void onFinish(ITestContext context) {
    if (myCurrentClassName != null) {
      System.out.println("##teamcity[testSuiteFinished name=\'" + myCurrentClassName + "\']");
    }
    //System.out.println("##teamcity[testSuiteFinished name=\'" + context.getName() + "\']");
  }
}
