package org.testng;

import com.intellij.rt.execution.junit.ComparisonFailureData;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlInclude;
import org.testng.xml.XmlTest;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.*;

public class IDEATestNGRemoteListener {

  private final PrintStream myPrintStream;
  private final List<String> myCurrentSuites = new ArrayList<String>();
  private final Map<String, Integer> myInvocationCounts = new HashMap<String, Integer>();
  private final Map<ExposedTestResult, String> myParamsMap = new HashMap<ExposedTestResult, String>();
  private final Map<ExposedTestResult, DelegatedResult> myResults = new HashMap<ExposedTestResult, DelegatedResult>();
  private int mySkipped = 0;

  public IDEATestNGRemoteListener() {
    this(System.out);
  }

  public IDEATestNGRemoteListener(PrintStream printStream) {
    myPrintStream = printStream;
    myPrintStream.println("##teamcity[enteredTheMatrix]");
  }

  public synchronized void onStart(final ISuite suite) {
    if (suite != null) {
      try {
        final List<ITestNGMethod> allMethods = suite.getAllMethods();
        if (allMethods != null) {
          int count = 0;
          for (ITestNGMethod method : allMethods) {
            if (method.isTest()) count += method.getInvocationCount();
          }
          myPrintStream.println("##teamcity[testCount count = \'" + count + "\']");
        }
      }
      catch (NoSuchMethodError ignore) {}
      myPrintStream.println("##teamcity[rootName name = '" + suite.getName() + "' location = 'file://" + suite.getXmlSuite().getFileName() + "']");
    }
  }

  public synchronized void onFinish(ISuite suite) {
    try {
      if (suite != null && suite.getAllInvokedMethods().size() + mySkipped < suite.getAllMethods().size()) {
        for (ITestNGMethod method : suite.getAllMethods()) {
          if (method.isTest()) {
            boolean found = false;
            for (IInvokedMethod invokedMethod : suite.getAllInvokedMethods()) {
              if (invokedMethod.getTestMethod() == method) {
                found = true;
                break;
              }
            }
            if (!found) {
              final String fullEscapedMethodName = escapeName(getShortName(method.getTestClass().getName()) + "." + method.getMethodName());
              myPrintStream.println("##teamcity[testStarted name=\'" + fullEscapedMethodName + "\']");
              myPrintStream.println("##teamcity[testIgnored name=\'" + fullEscapedMethodName + "\']");
              myPrintStream.println("##teamcity[testFinished name=\'" + fullEscapedMethodName + "\']");
              break;
            }
          }
        }
      }
    }
    catch (NoSuchMethodError ignored) {}
    for (int i = myCurrentSuites.size() - 1; i >= 0; i--) {
      onSuiteFinish(myCurrentSuites.remove(i));
    }
    myCurrentSuites.clear();
  }

  public synchronized void onConfigurationSuccess(ITestResult result, boolean start) {
    final DelegatedResult delegatedResult = createDelegated(result);
    if (start) {
      onConfigurationStart(delegatedResult);
    }
    onConfigurationSuccess(delegatedResult);
  }

  public synchronized void onConfigurationFailure(ITestResult result, boolean start) {
    final DelegatedResult delegatedResult = createDelegated(result);
    if (start) {
      onConfigurationStart(delegatedResult);
    }
    onConfigurationFailure(delegatedResult);
  }

  public synchronized void onConfigurationSkip(ITestResult itr) {}

  public synchronized void onTestStart(ITestResult result) {
    onTestStart(createDelegated(result));
  }

  public synchronized void onTestSuccess(ITestResult result) {
    onTestFinished(createDelegated(result));
  }

  public synchronized void onTestFailure(ITestResult result) {
    onTestFailure(createDelegated(result));
  }

  public synchronized void onTestSkipped(ITestResult result) {
    onTestSkipped(createDelegated(result));
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
   onStartWithParameters(result, false);
  }

  public void onStartWithParameters(ExposedTestResult result, boolean config) {
    final Object[] parameters = result.getParameters();
    final String qualifiedName = result.getClassName() + result.getDisplayMethodName();
    Integer invocationCount = myInvocationCounts.get(qualifiedName);
    if (invocationCount == null) {
      invocationCount = 0;
    }
    Integer normalizedIndex = normalizeInvocationCountInsideIncludedMethods(invocationCount, result);
    final String paramString = getParamsString(parameters, config, normalizedIndex);
    onTestStart(result, paramString, normalizedIndex, config);
    myInvocationCounts.put(qualifiedName, invocationCount + 1);
  }

  private static Integer normalizeInvocationCountInsideIncludedMethods(Integer invocationCount, ExposedTestResult result) {
    List<Integer> includeMethods = result.getIncludeMethods();
    if (includeMethods == null || invocationCount >= includeMethods.size()) {
      return invocationCount;
    }
    return includeMethods.get(invocationCount);
  }

  public void onConfigurationStart(ExposedTestResult result) {
    onStartWithParameters(result, true);
  }

  public void onConfigurationSuccess(ExposedTestResult result) {
    onTestFinished(result);
  }

  public void onConfigurationFailure(ExposedTestResult result) {
    onTestFailure(result);
  }
  
  public boolean onSuiteStart(String classFQName, boolean provideLocation) {
    return onSuiteStart(Collections.singletonList(classFQName), null, provideLocation);
  }

  public boolean onSuiteStart(List<String> parentsHierarchy, ExposedTestResult result, boolean provideLocation) {
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
      String location = "java:suite://" + escapeName(fqName);
      if (result != null) {
        final String testName = result.getXmlTestName();
        if (fqName.equals(testName)) {
          final String fileName = result.getFileName();
          if (fileName != null) {
            location = "file://" + fileName;
          }
        }
      }
      myPrintStream.println("\n##teamcity[testSuiteStarted name =\'" + escapeName(currentClassName) +
                            (provideLocation ? "\' locationHint = \'" + location : "") + "\']");
      myCurrentSuites.add(currentClassName);
    }
    return false;
  }

  public void onSuiteFinish(String suiteName) {
    myPrintStream.println("##teamcity[testSuiteFinished name=\'" + escapeName(suiteName) + "\']");
  }

  private void onTestStart(ExposedTestResult result, String paramString, Integer invocationCount, boolean config) {
    myParamsMap.put(result, paramString);
    onSuiteStart(result.getTestHierarchy(), result, true);
    final String className = result.getClassName();
    final String methodName = result.getDisplayMethodName();
    final String location = className + "." + result.getMethodName() + (invocationCount >= 0 ? "[" + invocationCount + "]" : "");
    myPrintStream.println("\n##teamcity[testStarted name=\'" + escapeName(getShortName(className) + "." + methodName + (paramString != null ? paramString : "")) +
                          "\' locationHint=\'java:test://" + escapeName(location) + (config ? "\' config=\'true" : "") + "\']");
  }

  public void onTestFailure(ExposedTestResult result) {
    if (!myParamsMap.containsKey(result)) {
      onTestStart(result);
    }
    Throwable ex = result.getThrowable();
    String methodName = getTestMethodNameWithParams(result);
    final Map<String, String> attrs = new LinkedHashMap<String, String>();
    attrs.put("name", methodName);
    final String failureMessage = ex != null ? ex.getMessage() : null;
    if (ex != null) {
      ComparisonFailureData notification;
      try {
        notification = TestNGExpectedPatterns.createExceptionNotification(failureMessage);
      }
      catch (Throwable e) {
        notification = null;
      }
      ComparisonFailureData.registerSMAttributes(notification, getTrace(ex), failureMessage, attrs, ex);
    }
    else {
      attrs.put("message", "");
    }
    myPrintStream.println();
    myPrintStream.println(MapSerializerUtil.asString("testFailed", attrs));
    onTestFinished(result);
  }

  public void onTestSkipped(ExposedTestResult result) {
    if (!myParamsMap.containsKey(result)) {
      onTestStart(result);
      mySkipped++;
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
    String methodName = getShortName(result.getClassName()) + "." + result.getDisplayMethodName();
    String paramString = myParamsMap.get(result);
    if (paramString != null) {
      methodName += paramString;
    }
    return methodName;
  }

  private static String getParamsString(Object[] parameters, boolean config, int invocationCount) {
    String paramString = "";
    if (parameters.length > 0) {
      if (config) {
        Object parameter = parameters[0];
        if (parameter != null) {
          Class<?> parameterClass = parameter.getClass();
          if (ITestResult.class.isAssignableFrom(parameterClass) || ITestContext.class.isAssignableFrom(parameterClass) || Method.class.isAssignableFrom(parameterClass)) {
            try {
              paramString = "[" + parameterClass.getMethod("getName").invoke(parameter) + "]";
            }
            catch (Throwable e) {
              paramString = "";
            }
          }
          else {
            paramString = "[" + parameter.toString() + "]";
          }
        }
      }
      else {
        paramString = Arrays.deepToString(parameters);
      }
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
    String getDisplayMethodName();
    String getClassName();
    long getDuration();
    List<String> getTestHierarchy();
    String getFileName();
    String getXmlTestName();
    Throwable getThrowable();
    List<Integer> getIncludeMethods();
  }

  protected DelegatedResult createDelegated(ITestResult result) {
    final DelegatedResult newResult = new DelegatedResult(result);
    final DelegatedResult oldResult = myResults.get(newResult);
    if (oldResult != null) {
      return oldResult;
    }
    myResults.put(newResult, newResult);
    return newResult;
  }
  
  protected static class DelegatedResult implements ExposedTestResult {
    private final ITestResult myResult;
    private final String myTestName;

    public DelegatedResult(ITestResult result) {
      myResult = result;
      myTestName = myResult.getTestName();
    }

    public Object[] getParameters() {
      return myResult.getParameters();
    }

    public String getMethodName() {
      return myResult.getMethod().getMethodName();
    }

    public String getDisplayMethodName() {
      return myTestName != null && myTestName.length() > 0 ? myTestName : myResult.getMethod().getMethodName();
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

    public String getFileName() {
      final XmlTest xmlTest = myResult.getTestClass().getXmlTest();
      return xmlTest != null ? xmlTest.getSuite().getFileName() : null;
    }

    public String getXmlTestName() {
      final XmlTest xmlTest = myResult.getTestClass().getXmlTest();
      return xmlTest != null ? xmlTest.getName() : null;
    }


    public Throwable getThrowable() {
      return myResult.getThrowable();
    }

    public List<Integer> getIncludeMethods() {
      IClass testClass = myResult.getTestClass();
      if (testClass == null) return null;
      XmlClass xmlClass = testClass.getXmlClass();
      if (xmlClass == null) return null;
      List<XmlInclude> includedMethods = xmlClass.getIncludedMethods();
      if (includedMethods.isEmpty()) return null;
      return includedMethods.get(0).getInvocationNumbers();
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
