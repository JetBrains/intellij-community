// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.configuration;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.rt.testng.IDEATestNGRemoteListener;
import com.intellij.util.ArrayUtilRt;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.testng.*;
import org.testng.internal.TestResult;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlInclude;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TestNGTreeHierarchyTest {

  @Test
  public void testOneTestMethod() {
    final XmlSuite suite = new XmlSuite();
    final XmlTest test = new XmlTest();
    final XmlClass xmlClass = new XmlClass("a.ATest", false);
    xmlClass.getIncludedMethods().add(new XmlInclude("test1"));
    test.getClasses().add(xmlClass);
    suite.getTests().add(test);

    doTest(suite,"##teamcity[enteredTheMatrix]\n" +
                  "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://a.ATest']\n" +
                  "##teamcity[testStarted name='ATest.test1|[0|]' locationHint='java:test://a.ATest/test1']\n" +
                  "##teamcity[testFinished name='ATest.test1|[0|]']\n");
  }

  @Test
  public void testSkipTestMethod() {

    final StringBuffer buf = new StringBuffer();
    final IDEATestNGRemoteListener listener = createListener(buf);
    listener.onStart((ISuite)null);
    listener.onTestSkipped(new MockTestNGResult("ATest", "testName"));
    listener.onFinish((ISuite)null);

    Assert.assertEquals("output: " + buf, "##teamcity[enteredTheMatrix]\n" +
                                          "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://ATest']\n" +
                                          "##teamcity[testStarted name='ATest.testName' locationHint='java:test://ATest/testName']\n" +
                                          "##teamcity[testIgnored name='ATest.testName']\n" +
                                          "##teamcity[testFinished name='ATest.testName']\n" +
                                          "##teamcity[testSuiteFinished name='ATest']\n", StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testParallelTestExecutionPreserveInvocationCount() {
    final StringBuffer buf = new StringBuffer();
    final IDEATestNGRemoteListener listener = createListener(buf);
    listener.onStart((ISuite)null);
    final MockTestNGResult[] results = new MockTestNGResult[] {new MockTestNGResult("ATest", "testName"), new MockTestNGResult("ATest", "testName1"), new MockTestNGResult("ATest", "testName")};
    for (MockTestNGResult result : results) {
      listener.onTestStart(result);
      listener.onTestFinished(result);
    }
    listener.onFinish((ISuite)null);

    Assert.assertEquals("output: " + buf, "##teamcity[enteredTheMatrix]\n" +
                                          "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://ATest']\n" +
                                          "##teamcity[testStarted name='ATest.testName' locationHint='java:test://ATest/testName']\n" +
                                          "##teamcity[testFinished name='ATest.testName']\n" +
                                          "##teamcity[testStarted name='ATest.testName1' locationHint='java:test://ATest/testName1']\n" +
                                          "##teamcity[testFinished name='ATest.testName1']\n" +
                                          "##teamcity[testStarted name='ATest.testName (1)' locationHint='java:test://ATest/testName|[1|]']\n" +
                                          "##teamcity[testFinished name='ATest.testName (1)']\n" +
                                          "##teamcity[testSuiteFinished name='ATest']\n", StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testParallelSameNameTestExecution() {
    final StringBuffer buf = new StringBuffer();
    final IDEATestNGRemoteListener listener = createListener(buf);
    listener.onStart((ISuite)null);
    final MockTestNGResult[] results = new MockTestNGResult[] {new MockTestNGResult("ATest", "testName"), new MockTestNGResult("BTest", "testName")};
    for (MockTestNGResult result : results) {
      listener.onTestStart(result);
    }
    for (MockTestNGResult result : results) {
      listener.onTestFinished(result);
    }
    listener.onFinish((ISuite)null);

    Assert.assertEquals("output: " + buf, "##teamcity[enteredTheMatrix]\n" +
                                          "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://ATest']\n" +
                                          "##teamcity[testStarted name='ATest.testName' locationHint='java:test://ATest/testName']\n" +
                                          "##teamcity[testSuiteFinished name='ATest']\n" +
                                          "##teamcity[testSuiteStarted name ='BTest' locationHint = 'java:suite://BTest']\n" +
                                          "##teamcity[testStarted name='BTest.testName' locationHint='java:test://BTest/testName']\n" +
                                          "##teamcity[testFinished name='ATest.testName']\n" +
                                          "##teamcity[testFinished name='BTest.testName']\n" +
                                          "##teamcity[testSuiteFinished name='BTest']\n", StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testFailureWithoutStart() {

    final StringBuffer buf = new StringBuffer();
    final IDEATestNGRemoteListener listener = createListener(buf);
    listener.onStart((ISuite)null);
    listener.onTestFailure(new MockTestNGResult("ATest", "testName", createExceptionWithoutTrace(), ArrayUtilRt.EMPTY_OBJECT_ARRAY));
    listener.onFinish((ISuite)null);

    Assert.assertEquals("output: " + buf, "##teamcity[enteredTheMatrix]\n" +
                                          "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://ATest']\n" +
                                          "##teamcity[testStarted name='ATest.testName' locationHint='java:test://ATest/testName']\n" +
                                          "##teamcity[testFailed name='ATest.testName' error='true' message='' details='java.lang.Exception|n']\n" +
                                          "##teamcity[testFinished name='ATest.testName']\n" +
                                          "##teamcity[testSuiteFinished name='ATest']\n", StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testSkipMethodAfterStartTest() {
    final StringBuffer buf = new StringBuffer();
    final IDEATestNGRemoteListener listener = createListener(buf);
    listener.onStart((ISuite)null);
    final MockTestNGResult result = new MockTestNGResult("ATest", "testName");
    listener.onTestStart(result);
    listener.onTestSkipped(result);
    listener.onFinish((ISuite)null);

    Assert.assertEquals("output: " + buf, "##teamcity[enteredTheMatrix]\n" +
                                          "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://ATest']\n" +
                                          "##teamcity[testStarted name='ATest.testName' locationHint='java:test://ATest/testName']\n" +
                                          "##teamcity[testIgnored name='ATest.testName']\n" +
                                          "##teamcity[testFinished name='ATest.testName']\n" +
                                          "##teamcity[testSuiteFinished name='ATest']\n", StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testOneTestMethodWithMultipleInvocationCount() {
    final XmlSuite suite = new XmlSuite();
    final XmlTest test = new XmlTest();
    final XmlClass xmlClass = new XmlClass("a.ATest", false);
    xmlClass.getIncludedMethods().add(new XmlInclude("test1", Arrays.asList(0, 1, 2), 0));
    test.getClasses().add(xmlClass);
    suite.getTests().add(test);

    doTest(suite, "##teamcity[enteredTheMatrix]\n" +
                  "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://a.ATest']\n" +
                  "##teamcity[testStarted name='ATest.test1|[0|]' locationHint='java:test://a.ATest/test1']\n" +
                  "##teamcity[testFinished name='ATest.test1|[0|]']\n" +
                  "##teamcity[testStarted name='ATest.test1|[1|] (1)' locationHint='java:test://a.ATest/test1|[1|]']\n" +
                  "##teamcity[testFinished name='ATest.test1|[1|] (1)']\n" +
                  "##teamcity[testStarted name='ATest.test1|[2|] (2)' locationHint='java:test://a.ATest/test1|[2|]']\n" +
                  "##teamcity[testFinished name='ATest.test1|[2|] (2)']\n");
  }

  @Test
  public void testConfigurationMethods() {
    final StringBuffer buf = new StringBuffer();
    final IDEATestNGRemoteListener listener = createListener(buf);
    final String className = "a.ATest";
    listener.onSuiteStart(className, true);
    for(String methodName : new String[] {"test1", "test2"}) {
      final MockTestNGResult setUp = new MockTestNGResult(className, "setUp");
      listener.onConfigurationStart(setUp);
      listener.onConfigurationSuccess(setUp);
      final MockTestNGResult result = new MockTestNGResult(className, methodName);
      listener.onTestStart(result);
      listener.onTestFinished(result);
      final MockTestNGResult tearDown = new MockTestNGResult(className, "tearDown");
      listener.onConfigurationStart(tearDown);
      listener.onConfigurationSuccess(tearDown);
    }
    listener.onSuiteFinish(className);

    Assert.assertEquals("output: " + buf,"##teamcity[enteredTheMatrix]\n" +
                                         "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://a.ATest']\n" +
                                         "##teamcity[testStarted name='ATest.setUp' locationHint='java:test://a.ATest/setUp' config='true']\n" +
                                         "##teamcity[testFinished name='ATest.setUp']\n" +
                                         "##teamcity[testStarted name='ATest.test1' locationHint='java:test://a.ATest/test1']\n" +
                                         "##teamcity[testFinished name='ATest.test1']\n" +
                                         "##teamcity[testStarted name='ATest.tearDown' locationHint='java:test://a.ATest/tearDown' config='true']\n" +
                                         "##teamcity[testFinished name='ATest.tearDown']\n" +
                                         "##teamcity[testStarted name='ATest.setUp (1)' locationHint='java:test://a.ATest/setUp|[1|]' config='true']\n" +
                                         "##teamcity[testFinished name='ATest.setUp (1)']\n" +
                                         "##teamcity[testStarted name='ATest.test2' locationHint='java:test://a.ATest/test2']\n" +
                                         "##teamcity[testFinished name='ATest.test2']\n" +
                                         "##teamcity[testStarted name='ATest.tearDown (1)' locationHint='java:test://a.ATest/tearDown|[1|]' config='true']\n" +
                                         "##teamcity[testFinished name='ATest.tearDown (1)']\n" +
                                         "##teamcity[testSuiteFinished name='a.ATest']\n", StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testConfigurationFailure() {
    final StringBuffer buf = new StringBuffer();
    final IDEATestNGRemoteListener listener = createListener(buf);
    final String className = "a.ATest";
    listener.onSuiteStart(className, true);
    final MockTestNGResult setUp = new MockTestNGResult(className, "setUp", createExceptionWithoutTrace(), ArrayUtilRt.EMPTY_OBJECT_ARRAY);
    listener.onConfigurationStart(setUp);
    listener.onConfigurationFailure(setUp);
    listener.onSuiteFinish(className);

    Assert.assertEquals("output: " + buf, "##teamcity[enteredTheMatrix]\n" +
                                          "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://a.ATest']\n" +
                                          "##teamcity[testStarted name='ATest.setUp' locationHint='java:test://a.ATest/setUp' config='true']\n" +
                                          "##teamcity[testFailed name='ATest.setUp' error='true' message='' details='java.lang.Exception|n']\n" +
                                          "##teamcity[testFinished name='ATest.setUp']\n" +
                                          "##teamcity[testSuiteFinished name='a.ATest']\n", StringUtil.convertLineSeparators(buf.toString()));
  }
 
  @Test
  public void testComparisonFailure() {
    final StringBuffer buf = new StringBuffer();
    final IDEATestNGRemoteListener listener = createListener(buf);
    final String className = "a.ATest";
    AssertionError throwable = new AssertionError("expected [expected\nnewline] but found [actual\nnewline]");
    MockTestNGResult foo = new MockTestNGResult(className, "testFoo",
                                                throwable, new Object[0]);
    listener.onTestFailure(foo);
    String message = buf.toString();
    String expectedFailureMessage =
      "##teamcity[testFailed name='ATest.testFoo' message='java.lang.AssertionError: ' expected='expected|nnewline' actual='actual|nnewline'";
    Assert.assertTrue(message, message.contains(expectedFailureMessage));
    
  }

  @Test
  public void testAfterMethodWithInjectedTestResult() {
    final StringBuffer buf = new StringBuffer();
    final IDEATestNGRemoteListener listener = createListener(buf);
    final String className = "a.ATest";
    listener.onSuiteStart(className, true);

    final MockTestNGResult result = new MockTestNGResult("ATest", "testMe", null, new Object[]{null, null});
    listener.onTestStart(result);
    listener.onTestFinished(result);

    final MockTestNGResult tearDown = new MockTestNGResult(className, "tearDown", null, new Object[] {new MyTestTestResult()});
    listener.onConfigurationStart(tearDown);
    listener.onConfigurationSuccess(tearDown);
    listener.onSuiteFinish(className);

    Assert.assertEquals("output: " + buf, "##teamcity[enteredTheMatrix]\n" +
                                          "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://a.ATest']\n" +
                                          "##teamcity[testStarted name='ATest.testMe|[null, null|]' locationHint='java:test://ATest/testMe']\n" +
                                          "##teamcity[testFinished name='ATest.testMe|[null, null|]']\n" +
                                          "##teamcity[testStarted name='ATest.tearDown|[testName|]' locationHint='java:test://a.ATest/tearDown' config='true']\n" +
                                          "##teamcity[testFinished name='ATest.tearDown|[testName|]']\n" +
                                          "##teamcity[testSuiteFinished name='a.ATest']\n", StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testNullParameters() {
    final StringBuffer buf = new StringBuffer();
    final IDEATestNGRemoteListener listener = createListener(buf);
    final MockTestNGResult result = new MockTestNGResult("ATest", "testMe", null, new Object[]{null, null});
    listener.onTestStart(result);
    listener.onTestFinished(result);
    Assert.assertEquals("output: " + buf, "##teamcity[enteredTheMatrix]\n" +
                                          "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://ATest']\n" +
                                          "##teamcity[testStarted name='ATest.testMe|[null, null|]' locationHint='java:test://ATest/testMe']\n" +
                                          "##teamcity[testFinished name='ATest.testMe|[null, null|]']\n", StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testIncludedMethods() {
    final StringBuffer buf = new StringBuffer();
    final IDEATestNGRemoteListener listener = createListener(buf);
    final MockTestNGResult result = new MockTestNGResult("ATest", "testMe", null, new Object[]{null, null}) {
      @Override
      public List<Integer> getIncludeMethods() {
        return Arrays.asList(1, 3, 5);
      }
    };
    for (int i = 0; i < 3; i++) {
      listener.onTestStart(result);
      listener.onTestFinished(result);
    }
    Assert.assertEquals("output: " + buf, "##teamcity[enteredTheMatrix]\n" +
                                          "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://ATest']\n" +
                                          "##teamcity[testStarted name='ATest.testMe|[null, null|] (1)' locationHint='java:test://ATest/testMe|[1|]']\n" +
                                          "##teamcity[testFinished name='ATest.testMe|[null, null|] (1)']\n" +
                                          "##teamcity[testStarted name='ATest.testMe|[null, null|] (3)' locationHint='java:test://ATest/testMe|[3|]']\n" +
                                          "##teamcity[testFinished name='ATest.testMe|[null, null|] (3)']\n" +
                                          "##teamcity[testStarted name='ATest.testMe|[null, null|] (5)' locationHint='java:test://ATest/testMe|[5|]']\n" +
                                          "##teamcity[testFinished name='ATest.testMe|[null, null|] (5)']\n", StringUtil.convertLineSeparators(buf.toString()));
  }

  private static void doTest(XmlSuite suite, String expected) {
    final StringBuffer buf = new StringBuffer();
    final IDEATestNGRemoteListener listener = createListener(buf);

    for (XmlTest test : suite.getTests()) {
      for (XmlClass aClass : test.getClasses()) {
        final String classFQName = aClass.getName();
        for (XmlInclude include : aClass.getIncludedMethods()) {
          final String methodName = include.getName();
          List<Integer> numbers = include.getInvocationNumbers();
          if (numbers.isEmpty()) {
            numbers = Collections.singletonList(0);
          }
          for (Integer integer : numbers) {
            final MockTestNGResult result = new MockTestNGResult(classFQName, methodName, null, new Object[] {integer});
            listener.onTestStart(result);
            listener.onTestFinished(result);
          }
        }
      }
    }

    Assert.assertEquals("output: " + buf, expected, StringUtil.convertLineSeparators(buf.toString()));
  }

  @NotNull
  private static IDEATestNGRemoteListener createListener(final StringBuffer buf) {
    return new IDEATestNGRemoteListener(new PrintStream(new OutputStream() {
        @Override
        public void write(int b) {
          buf.append(new String(new byte[]{(byte)b}, StandardCharsets.UTF_8));
        }
      })) {
      @Override
      protected String getTrace(Throwable tr) {
        return StringUtil.convertLineSeparators(super.getTrace(tr));
      }
    };
  }

  private static Exception createExceptionWithoutTrace() {
    final Exception exception = new Exception();
    exception.setStackTrace(new StackTraceElement[0]);
    return exception;
  }

  private static class MockTestNGResult implements IDEATestNGRemoteListener.ExposedTestResult {
    private final String myClassName;
    private final String myMethodName;
    private final Throwable myThrowable;
    private final Object[]  myParams;

    MockTestNGResult(String className, String methodName, Throwable throwable, Object[] params) {
      myClassName = className;
      myMethodName = methodName;
      myThrowable = throwable;
      myParams = params;
    }

    private MockTestNGResult(String className, String methodName) {
     this(className, methodName, null, ArrayUtilRt.EMPTY_OBJECT_ARRAY);
    }

    @Override
    public Object[] getParameters() {
      return myParams;
    }

    @Override
    public String getMethodName() {
      return myMethodName;
    }

    @Override
    public String getDisplayMethodName() {
      return myMethodName;
    }

    @Override
    public String getClassName() {
      return myClassName;
    }

    @Override
    public long getDuration() {
      return 0;
    }

    @Override
    public List<String> getTestHierarchy() {
      return Collections.singletonList(myClassName);
    }

    @Override
    public String getFileName() {
      return null;
    }

    @Override
    public String getXmlTestName() {
      return null;
    }

    @Override
    public Throwable getThrowable() {
      return myThrowable;
    }

    @Override
    public List<Integer> getIncludeMethods() {
      return null;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MockTestNGResult result = (MockTestNGResult)o;

      if (!myClassName.equals(result.myClassName)) return false;
      if (!myMethodName.equals(result.myMethodName)) return false;
      if (myThrowable != null ? !myThrowable.equals(result.myThrowable) : result.myThrowable != null) return false;
      // Probably incorrect - comparing Object[] arrays with Arrays.equals
      if (!Arrays.equals(myParams, result.myParams)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myClassName.hashCode();
      result = 31 * result + myMethodName.hashCode();
      result = 31 * result + (myThrowable != null ? myThrowable.hashCode() : 0);
      result = 31 * result + (myParams != null ? Arrays.hashCode(myParams) : 0);
      return result;
    }
  }

  public static class MyTestTestResult implements ITestResult {
    private final TestResult empty = TestResult.newEmptyTestResult();

    @Override
    public void setEndMillis(long millis) {
      empty.setEndMillis(millis);
    }

    @Override
    public String getTestName() {
      return empty.getTestName();
    }

    @Override
    public String getName() {
      return "testName";
    }

    @Override
    public ITestNGMethod getMethod() {
      return empty.getMethod();
    }

    public void setMethod(ITestNGMethod method) {
      empty.setMethod(method);
    }

    @Override
    public int getStatus() {
      return empty.getStatus();
    }

    @Override
    public void setStatus(int status) {
      empty.setStatus(status);
    }

    @Override
    public boolean isSuccess() {
      return empty.isSuccess();
    }

    @Override
    public IClass getTestClass() {
      return empty.getTestClass();
    }

    @Override
    public Throwable getThrowable() {
      return empty.getThrowable();
    }

    @Override
    public void setThrowable(Throwable throwable) {
      empty.setThrowable(throwable);
    }

    @Override
    public long getEndMillis() {
      return empty.getEndMillis();
    }

    @Override
    public long getStartMillis() {
      return empty.getStartMillis();
    }

    @Override
    public String toString() {
      return empty.toString();
    }

    @Override
    public String getHost() {
      return empty.getHost();
    }

    public void setHost(String host) {
      empty.setHost(host);
    }

    @Override
    public Object[] getParameters() {
      return empty.getParameters();
    }

    @Override
    public void setParameters(Object[] parameters) {
      empty.setParameters(parameters);
    }

    @Override
    public Object getInstance() {
      return empty.getInstance();
    }

    @Override
    public Object[] getFactoryParameters() {
      return empty.getFactoryParameters();
    }

    @Override
    public Object getAttribute(String name) {
      return empty.getAttribute(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
      empty.setAttribute(name, value);
    }

    @Override
    public Set<String> getAttributeNames() {
      return empty.getAttributeNames();
    }

    @Override
    public Object removeAttribute(String name) {
      return empty.removeAttribute(name);
    }

    @Override
    public ITestContext getTestContext() {
      return empty.getTestContext();
    }

    public void setContext(ITestContext context) {
      empty.setContext(context);
    }

    @Override
    public int compareTo(ITestResult comparison) {
      return empty.compareTo(comparison);
    }

    @Override
    public String getInstanceName() {
      return empty.getInstanceName();
    }

    @Override
    public void setTestName(String name) {
      empty.setTestName(name);
    }

    public int getParameterIndex() {
      return empty.getParameterIndex();
    }

    @Override
    public boolean wasRetried() {
      return empty.wasRetried();
    }

    @Override
    public void setWasRetried(boolean wasRetried) {
      empty.setWasRetried(wasRetried);
    }

    @Override
    public List<ITestNGMethod> getSkipCausedBy() {
      return empty.getSkipCausedBy();
    }

    public static boolean wasFailureDueToTimeout(ITestResult result) {
      return ITestResult.wasFailureDueToTimeout(result);
    }
  }
}
