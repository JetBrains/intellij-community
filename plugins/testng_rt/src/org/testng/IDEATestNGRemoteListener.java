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

  public static final String INVOCATION_NUMBER = "invocation number: ";
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
    onTestStart(getTestHierarchy(result), getMethodName(result, true));
  }

  public synchronized void onTestSuccess(ITestResult result) {
    onTestFinished(getMethodName(result));
  }

  public synchronized void onTestFailure(ITestResult result) {
    onTestFailure(result.getThrowable(), getMethodName(result));
  }

  public synchronized void onTestSkipped(ITestResult result) {
    myPrintStream.println("\n##teamcity[testIgnored name=\'" + escapeName(getMethodName(result)) + "\']");
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

  public void onTestStart(String classFQName, String methodName) {
    onTestStart(Collections.singletonList(classFQName), methodName);
  }

  public void onTestStart(List<String> classFQName, String methodName) {
    onSuiteStart(classFQName, true);
    fireTestStarted(methodName, classFQName.get(0));
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
    return result.getMethod().getTestClass().getName();
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
