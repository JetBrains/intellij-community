package org.testng;

import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes;
import org.testng.internal.IResultListener;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: anna
 * Date: 5/22/13
 */
public class IDEATestNGRemoteListener implements ISuiteListener, IResultListener{

  public static final String INVOCATION_NUMBER = "invocation number: ";
  private String myCurrentClassName;
  private String myMethodName;
  private int    myInvocationCount = 0;

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
    System.out.println("##teamcity[enteredTheMatrix]");
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
      myInvocationCount = 0;
    }
    String methodName = getMethodName(result, false);
    System.out.println("##teamcity[testStarted name=\'" +
                       methodName + "\' locationHint=\'java:test://" + className + "." + methodName + "\']");
  }

  private String getMethodName(ITestResult result) {
    return getMethodName(result, true);
  }

  private String getMethodName(ITestResult result, boolean changeCount) {
    String methodName = result.getMethod().getMethodName();
    final Object[] parameters = result.getParameters();
    if (changeCount) {
      if (!methodName.equals(myMethodName)) {
        myInvocationCount = 0;
        myMethodName = methodName;
      }
    }
    if (parameters.length > 0) {
      final List<Integer> invocationNumbers = result.getMethod().getInvocationNumbers();
      methodName += "[" + parameters[0].toString() + " (" + INVOCATION_NUMBER + 
                    (invocationNumbers.isEmpty() ? myInvocationCount : invocationNumbers.get(myInvocationCount)) + ")" + "]";
      if (changeCount) {
        myInvocationCount++;
      }
    }
    return methodName;
  }

  public void onTestSuccess(ITestResult result) {
    System.out.println("\n##teamcity[testFinished name=\'" + getMethodName(result) + "\']");
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
    final String methodName = getMethodName(result);
    attrs.put("name", methodName);
    final String failureMessage = ex.getMessage();
    attrs.put("message", failureMessage != null ? failureMessage : "");
    attrs.put("details", trace);
    attrs.put("error", "true");
    System.out.println(ServiceMessage.asString(ServiceMessageTypes.TEST_FAILED, attrs));
    System.out.println("\n##teamcity[testFinished name=\'" + methodName + "\']");
  }

  public void onTestSkipped(ITestResult result) {
    System.out.println("\n##teamcity[testFinished name=\'" + getMethodName(result) + "\']");
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
