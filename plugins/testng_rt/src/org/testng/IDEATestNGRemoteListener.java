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
  private final Map<String, Integer> myInvocationCounts = new HashMap<String, Integer>();
  private final Map<ExposedTestResult, String> myParamsMap = new HashMap<ExposedTestResult, String>();

  public IDEATestNGRemoteListener() {
    myPrintStream = System.out;
  }

  public IDEATestNGRemoteListener(PrintStream printStream) {
    myPrintStream = printStream;
  }

  public synchronized void onStart(final ISuite suite) {
    myPrintStream.println("##teamcity[enteredTheMatrix]");
  }

  public synchronized void onFinish(ISuite suite) {
    for (int i = myCurrentSuites.size() - 1; i >= 0; i--) {
      onSuiteFinish(myCurrentSuites.remove(i));
    }
    myCurrentSuites.clear();
  }

  public synchronized void onConfigurationSuccess(ITestResult result) {
    onConfigurationSuccess(new DelegatedResult(result));
  }

  public synchronized void onConfigurationFailure(ITestResult result) {
    onConfigurationFailure(new DelegatedResult(result));
  }

  public synchronized void onConfigurationSkip(ITestResult itr) {}

  public synchronized void onTestStart(ITestResult result) {
    onTestStart(new DelegatedResult(result));
  }

  public synchronized void onTestSuccess(ITestResult result) {
    onTestFinished(new DelegatedResult(result));
  }

  public synchronized void onTestFailure(ITestResult result) {
    onTestFailure(new DelegatedResult(result));
  }

  public synchronized void onTestSkipped(ITestResult result) {
    onTestSkipped(new DelegatedResult(result));
  }

  public synchronized void onTestFailedButWithinSuccessPercentage(ITestResult result) {
    final Throwable throwable = result.getThrowable();
    if (throwable != null) {
      throwable.printStackTrace();
    }
    onTestSuccess(result);
  }

  public synchronized void onStart(ITestContext context) {}

  public synchronized void onFinish(ITestContext context) {}

  public void onTestStart(ExposedTestResult result) {
    final Object[] parameters = result.getParameters();
    final String qualifiedName = result.getClassName() + result.getMethodName();
    Integer invocationCount = myInvocationCounts.get(qualifiedName);
    if (invocationCount == null) {
      invocationCount = 0;
    }
    
    final String paramString = getParamsString(parameters, invocationCount);
    onTestStart(result, paramString, invocationCount);
    myInvocationCounts.put(qualifiedName, invocationCount + 1);
  }

  public void onConfigurationSuccess(ExposedTestResult result) {
    onTestStart(result, null, -1);
    onTestFinished(result);
  }

  public void onConfigurationFailure(ExposedTestResult result) {
    onTestStart(result, null, -1);
    onTestFailure(result);
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
      myPrintStream.println("\n##teamcity[testSuiteStarted name =\'" + escapeName(currentClassName) +
                            (provideLocation ? "\' locationHint = \'java:suite://" + escapeName(fqName) : "") + "\']");
      myCurrentSuites.add(currentClassName);
    }
    return false;
  }

  public void onSuiteFinish(String suiteName) {
    myPrintStream.println("##teamcity[testSuiteFinished name=\'" + escapeName(suiteName) + "\']");
  }

  private void onTestStart(ExposedTestResult result, String paramString, Integer invocationCount) {
    myParamsMap.put(result, paramString);
    final List<String> fqns = result.getTestHierarchy();
    onSuiteStart(fqns, true);
    final String methodName = result.getMethodName();
    final String className = result.getClassName();
    final String location = className + "." + methodName + (invocationCount >= 0 ? "[" + invocationCount + "]" : "");
    myPrintStream.println("\n##teamcity[testStarted name=\'" + escapeName(methodName + (paramString != null ? paramString : "")) +
                          "\' locationHint=\'java:test://" + escapeName(location) + "\']");
  }

  public void onTestFailure(ExposedTestResult result) {
    if (!myParamsMap.containsKey(result)) {
      onTestStart(result);
    }
    Throwable ex = result.getThrowable();
    String methodName = getTestMethodNameWithParams(result);
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
    onTestFinished(result);
  }

  public void onTestSkipped(ExposedTestResult result) {
    if (!myParamsMap.containsKey(result)) {
      onTestStart(result);
    }
    myPrintStream.println("\n##teamcity[testIgnored name=\'" + escapeName(getTestMethodNameWithParams(result)) + "\']");
    onTestFinished(result);
  }

  public void onTestFinished(ExposedTestResult result) {
    final long duration = result.getDuration();
    myPrintStream.println("\n##teamcity[testFinished name=\'" +
                          escapeName(getTestMethodNameWithParams(result)) +
                          (duration > 0 ? "\' duration=\'" + Long.toString(duration) : "") +
                          "\']");
  }

  private synchronized String getTestMethodNameWithParams(ExposedTestResult result) {
    String methodName = result.getMethodName();
    String paramString = myParamsMap.get(result);
    if (paramString != null) {
      methodName += paramString;
    }
    return methodName;
  }

  private static String getParamsString(Object[] parameters, int invocationCount) {
    String paramString = "";
    if (parameters.length > 0) {
      StringBuilder buf = new StringBuilder();
      for (int i = 0; i < parameters.length; i++) {
        if (i > 0) {
          buf.append(", ");
        }
        buf.append(parameters[i]);
      }
      paramString = "[" + buf.toString() + "]";
    }
    if (invocationCount > 0) {
      paramString += " (" + invocationCount + ")";
    }
    return paramString.length() > 0 ? paramString : null;
  }

  protected String getTrace(Throwable tr) {
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

  public interface ExposedTestResult {
    Object[] getParameters();
    String getMethodName();
    String getClassName();
    long getDuration();
    List<String> getTestHierarchy();
    Throwable getThrowable();
  }

  private static class DelegatedResult implements ExposedTestResult {
    private final ITestResult myResult;

    public DelegatedResult(ITestResult result) {
      myResult = result;
    }

    public Object[] getParameters() {
      return myResult.getParameters();
    }

    public String getMethodName() {
      return myResult.getMethod().getMethodName();
    }

    public String getClassName() {
      return myResult.getMethod().getTestClass().getName();
    }

    public long getDuration() {
      return myResult.getEndMillis() - myResult.getStartMillis();
    }

    public List<String> getTestHierarchy() {
      final List<String> hierarchy;
      final XmlTest xmlTest = myResult.getTestClass().getXmlTest();
      if (xmlTest != null) {
        hierarchy = Arrays.asList(getClassName(), xmlTest.getName());
      } else {
        hierarchy = Collections.singletonList(getClassName());
      }
      return hierarchy;
    }

    public Throwable getThrowable() {
      return myResult.getThrowable();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      return myResult.equals(((DelegatedResult)o).myResult);
    }

    @Override
    public int hashCode() {
      return myResult.hashCode();
    }
  }
}
