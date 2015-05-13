package org.testng;

import com.intellij.rt.execution.junit.ComparisonFailureData;
import jetbrains.buildServer.messages.serviceMessages.MapSerializerUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes;
import org.testng.internal.IResultListener;
import org.testng.xml.XmlTest;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

/**
 * User: anna
 * Date: 5/22/13
 */
public class IDEATestNGRemoteListener implements ISuiteListener, IResultListener{

  private final PrintStream myPrintStream;
  private final List<String> myCurrentSuites = new ArrayList<String>();
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
    onConfigurationSuccess(getTestHierarchy(result), getTestMethodName(result));
  }

  public synchronized void onConfigurationFailure(ITestResult result) {
    onConfigurationFailure(getTestHierarchy(result), getTestMethodName(result), result.getThrowable());
  }

  public synchronized void onConfigurationSkip(ITestResult itr) {}

  public synchronized void onTestStart(ITestResult result) {
    final String testMethodName = getTestMethodName(result);
    final Object[] parameters = result.getParameters();
    if (!testMethodName.equals(myMethodName)) {
      myInvocationCount = 0;
      myMethodName = testMethodName;
    }
    Integer invocationCount = myMap.get(result);
    if (invocationCount == null) {
      invocationCount = myInvocationCount;
      myMap.put(result, invocationCount);
    }
    onTestStart(getTestHierarchy(result), testMethodName, parameters.length > 0 ? "[" + getParamsString(parameters) + "]" : null, invocationCount);
    myInvocationCount++;
  }

  public synchronized void onTestSuccess(ITestResult result) {
    onTestFinished(getTestMethodNameWithParams(result));
  }

  public synchronized void onTestFailure(ITestResult result) {
    onTestFailure(result.getThrowable(), getTestMethodNameWithParams(result));
  }

  public synchronized void onTestSkipped(ITestResult result) {
    myPrintStream.println("\n##teamcity[testIgnored name=\'" + escapeName(getTestMethodNameWithParams(result)) + "\']");
  }

  public synchronized void onTestFailedButWithinSuccessPercentage(ITestResult result) {}

  public synchronized void onStart(ITestContext context) {}

  public synchronized void onFinish(ITestContext context) {
    for (int i = myCurrentSuites.size() - 1; i >= 0; i--) {
      onSuiteFinish(myCurrentSuites.remove(i));
    }
    myCurrentSuites.clear();
  }

  private static List<String> getTestHierarchy(ITestResult result) {
    final List<String> hierarchy;
    final XmlTest xmlTest = result.getTestClass().getXmlTest();
    if (xmlTest != null) {
      hierarchy = Arrays.asList(getClassName(result), xmlTest.getName());
    } else {
      hierarchy = Collections.singletonList(getClassName(result));
    }
    return hierarchy;
  }

  public void onConfigurationSuccess(List<String> classFQName, String testMethodName) {
    onSuiteStart(classFQName, true);
    fireTestStarted(testMethodName, classFQName.get(0));
    onTestFinished(testMethodName);
  }

  public void onConfigurationFailure(List<String> classFQName, String testMethodName, Throwable throwable) {
    onSuiteStart(classFQName, true);
    fireTestStarted(testMethodName, classFQName.get(0));
    onTestFailure(throwable, testMethodName);
  }
  
  public boolean onSuiteStart(String classFQName, boolean provideLocation) {
    return onSuiteStart(Collections.singletonList(classFQName), provideLocation);
  }
  
  public boolean onSuiteStart(List<String> parentsHierarchy, boolean provideLocation) {
    int idx = 0;
    String currentClass;
    String currentParent;
    while (idx < myCurrentSuites.size() && idx < parentsHierarchy.size()) {
      currentClass = myCurrentSuites.get(idx);
      currentParent =parentsHierarchy.get(parentsHierarchy.size() - 1 - idx);
      if (!currentClass.equals(getShortName(currentParent))) break;
      idx++;
    }

    for (int i = myCurrentSuites.size() - 1; i >= idx; i--) {
      currentClass = myCurrentSuites.remove(i);
      myPrintStream.println("##teamcity[testSuiteFinished name=\'" + escapeName(currentClass) + "\']");
    }

    for (int i = idx; i < parentsHierarchy.size(); i++) {
      String fqName = parentsHierarchy.get(parentsHierarchy.size() - 1 - i);
      String currentClassName = getShortName(fqName);
      myPrintStream.print("\n##teamcity[testSuiteStarted name =\'" + escapeName(currentClassName));
      if (provideLocation) {
        myPrintStream.print("\' locationHint = \'java:suite://" + fqName);
      }
      myPrintStream.println("\']");
      myCurrentSuites.add(currentClassName);
    }
    return false;
  }

  public void onSuiteFinish(String suiteName) {
    myPrintStream.println("##teamcity[testSuiteFinished name=\'" + escapeName(suiteName) + "\']");
  }

  //testOnly
  public void onTestStart(String classFQName, String methodName) {
    onTestStart(Collections.singletonList(classFQName), methodName, null, -1);
  }

  public void onTestStart(List<String> classFQName, String methodName, String paramString, Integer invocationCount) {
    onSuiteStart(classFQName, true);
    fireTestStarted(methodName, classFQName.get(0), paramString, invocationCount);
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
    ComparisonFailureData.registerSMAttributes(notification, getTrace(ex), failureMessage, attrs, ex);
    myPrintStream.println(ServiceMessage.asString(ServiceMessageTypes.TEST_FAILED, attrs));
    onTestFinished(methodName);
  }

  private static String getClassName(ITestResult result) {
    return result.getMethod().getTestClass().getName();
  }

  private static String getTestMethodName(ITestResult result) {
    return result.getMethod().getMethodName();
  }

  private void fireTestStarted(String methodName, String className) {
    fireTestStarted(methodName, className, null, -1);
  }

  private void fireTestStarted(String methodName, String className, String paramString, Integer invocationCount) {
    myPrintStream.println("\n##teamcity[testStarted name=\'" + escapeName(methodName) + (paramString != null ? paramString : "") +
                          "\' locationHint=\'java:test://" + escapeName(className + "." + methodName +  ( invocationCount >= 0 ? "[" + invocationCount + "]" : "")) + "\']");
  }

  private static String getTestMethodNameWithParams(ITestResult result) {
    String methodName = getTestMethodName(result);
    final Object[] parameters = result.getParameters();
    if (parameters.length > 0) {
      methodName += "[" + getParamsString(parameters) + "]";
    }
    return methodName;
  }

  private static String getParamsString(Object[] parameters) {
    StringBuilder buf = new StringBuilder(); 
    for (int i = 0; i < parameters.length; i++) {
      if (i > 0) {
        buf.append(", ");
      }
      buf.append(parameters[i].toString());
    }
    return buf.toString();
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
