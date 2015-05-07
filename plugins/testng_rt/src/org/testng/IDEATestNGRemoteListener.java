package org.testng;

import com.intellij.rt.execution.junit.ComparisonFailureData;
import jetbrains.buildServer.messages.serviceMessages.MapSerializerUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes;
import org.testng.internal.IResultListener;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * User: anna
 * Date: 5/22/13
 */
public class IDEATestNGRemoteListener implements ISuiteListener, IResultListener{

  public static final String INVOCATION_NUMBER = "invocation number: ";
  private final PrintStream myPrintStream;
  private String myCurrentClassName = null;
  private String myMethodName;
  private int myInvocationCount = 0;
  private final Map<ITestResult, Integer> myMap = Collections.synchronizedMap(new HashMap<ITestResult, Integer>());

  public IDEATestNGRemoteListener() {
    myPrintStream = System.out;
  }

  public IDEATestNGRemoteListener(PrintStream printStream) {
    myPrintStream = printStream;
  }

  public synchronized void onStart(final ISuite suite) {
    myPrintStream.println("##teamcity[enteredTheMatrix]");
    onSuiteStart(suite.getName(), false);
  }

  public synchronized void onFinish(ISuite suite) {
    onSuiteFinish(suite.getName());
  }

  public synchronized void onConfigurationSuccess(ITestResult result) {
    onConfigurationSuccess(getClassName(result), getTestMethodName(result));
  }

  public synchronized void onConfigurationFailure(ITestResult result) {
    onConfigurationFailure(getClassName(result), getTestMethodName(result), result.getThrowable());
  }

  public synchronized void onConfigurationSkip(ITestResult itr) {}

  public synchronized void onTestStart(ITestResult result) {
    onTestStart(getClassName(result), getMethodName(result, true));
  }
  
  public synchronized void onTestSuccess(ITestResult result) {
    onTestFinished(getMethodName(result));
  }

  public synchronized void onTestFailure(ITestResult result) {
    onTestFailure(result.getThrowable(), getMethodName(result));
  }

  public synchronized void onTestSkipped(ITestResult result) {
    onTestFinished(getMethodName(result));
  }

  public synchronized void onTestFailedButWithinSuccessPercentage(ITestResult result) {}

  public synchronized void onStart(ITestContext context) {}

  public synchronized void onFinish(ITestContext context) {
    final String currentClassName = myCurrentClassName;
    if (currentClassName != null) {
      onSuiteFinish(currentClassName);
    }
  }

  public void onConfigurationSuccess(String classFQName, String testMethodName) {
    final String className = getShortName(classFQName);
    final boolean startedNode = onSuiteStart(classFQName, true);
    fireTestStarted(testMethodName, classFQName);
    onTestFinished(testMethodName);
    if (startedNode) {
      myPrintStream.println();
      onSuiteFinish(className);
    }
  }

  public void onConfigurationFailure(String classFQName, String testMethodName, Throwable throwable) {
    final String className = getShortName(classFQName);
    final boolean start = onSuiteStart(classFQName, true);

    fireTestStarted(testMethodName, classFQName);
    onTestFailure(throwable, testMethodName);

    if (start) {
      myPrintStream.println();
      onSuiteFinish(className);
    }
  }
  
  public boolean onSuiteStart(String suiteName, boolean provideLocation) {
    final String className = getShortName(suiteName);
    if (myCurrentClassName == null || !myCurrentClassName.equals(className)) {
      if (myCurrentClassName != null) {
        onSuiteFinish(myCurrentClassName);
      }
      myPrintStream.print("\n##teamcity[testSuiteStarted name =\'" + escapeName(provideLocation ? getShortName(suiteName) : suiteName));
      if (provideLocation) {
        myPrintStream.print("\' locationHint = \'java:suite://" + suiteName);
      }
      myPrintStream.println("\']");
      myCurrentClassName = className;
      myInvocationCount = 0;
      return true;
    }
    return false;
  }

  public void onSuiteFinish(String suiteName) {
    myPrintStream.println("##teamcity[testSuiteFinished name=\'" + escapeName(suiteName) + "\']");
    myCurrentClassName = null;
  }

  public void onTestStart(String classFQName, String methodName) {
    onSuiteStart(classFQName, true);
    fireTestStarted(methodName, classFQName);
  }
  
  public void onTestFinished(String methodName) {
    myPrintStream.println("\n##teamcity[testFinished name=\'" + escapeName(methodName) + "\']");
  }

  public void onTestFailure(Throwable ex, String methodName) {
    final Map<String, String> attrs = new HashMap<String, String>();
    attrs.put("name", methodName);
    final String failureMessage = ex.getMessage();
    ComparisonFailureData notification;
    try {
      notification = TestNGExpectedPatterns.createExceptionNotification(failureMessage);
    }
    catch (Throwable e) {
      notification = null;
    }
    ComparisonFailureData.registerSMAttributes(notification, getTrace(ex), failureMessage, attrs);
    myPrintStream.println(ServiceMessage.asString(ServiceMessageTypes.TEST_FAILED, attrs));
    onTestFinished(methodName);
  }

  private static String getClassName(ITestResult result) {
    return result.getTestClass().getName();
  }

  private static String getTestMethodName(ITestResult result) {
    return result.getMethod().getMethodName();
  }

  private void fireTestStarted(String methodName, String className) {
    myPrintStream.println("\n##teamcity[testStarted name=\'" + escapeName(methodName) +
                          "\' locationHint=\'java:test://" + escapeName(className + "." + methodName) + "\']");
  }

  private String getMethodName(ITestResult result) {
    return getMethodName(result, false);
  }

  private String getMethodName(ITestResult result, boolean changeCount) {
    String methodName = getTestMethodName(result);
    final Object[] parameters = result.getParameters();
    if (!methodName.equals(myMethodName)) {
      myInvocationCount = 0;
      myMethodName = methodName;
    }
    if (parameters.length > 0) {
      Integer invocationCount = myMap.get(result);
      if (invocationCount == null) {
        invocationCount = myInvocationCount;
        myMap.put(result, invocationCount);
      }
      
      methodName += "[" + parameters[0].toString() + " (" + INVOCATION_NUMBER + invocationCount + ")" + "]";
      if (changeCount) {
        myInvocationCount++;
      }
    }
    return methodName;
  }

  private static String getTrace(Throwable tr) {
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    tr.printStackTrace(writer);
    StringBuffer buffer = stringWriter.getBuffer();
    return buffer.toString();
  }

  protected static String getShortName(String fqName) {
    int lastPointIdx = fqName.lastIndexOf('.');
    if (lastPointIdx >= 0) {
      return fqName.substring(lastPointIdx + 1);
    }
    return fqName;
  }

  private static String escapeName(String str) {
    return MapSerializerUtil.escapeStr(str, MapSerializerUtil.STD_ESCAPER);
  }
}
