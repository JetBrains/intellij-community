/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.theoryinpractice.testng.configuration;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.testng.IDEATestNGRemoteListener;
import org.testng.ISuite;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlInclude;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public class TestNGTreeHierarchyTest {
 
  @Test
  public void testOneTestMethod() throws Exception {
    final XmlSuite suite = new XmlSuite();
    final XmlTest test = new XmlTest();
    final XmlClass xmlClass = new XmlClass("a.ATest", false);
    xmlClass.getIncludedMethods().add(new XmlInclude("test1"));
    test.getClasses().add(xmlClass);
    suite.getTests().add(test);
    
    doTest(suite, "##teamcity[testCount count='1']\n" +
                  "\n" +
                  "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://a.ATest']\n" +
                  "\n" +
                  "##teamcity[testStarted name='ATest.test1|[0|]' locationHint='java:test://a.ATest.test1|[0|]']\n" +
                  "\n" +
                  "##teamcity[testFinished name='ATest.test1|[0|]']\n");
  }

  @Test
  public void testSkipTestMethod() throws Exception {

    final StringBuffer buf = new StringBuffer();
    final IDEATestNGRemoteListener listener = createListener(buf);
    listener.onStart((ISuite)null);
    listener.onTestSkipped(new MockTestNGResult("ATest", "testName"));
    listener.onFinish((ISuite)null);

    Assert.assertEquals("output: " + buf, "##teamcity[enteredTheMatrix]\n" +
                                          "##teamcity[testCount count='1']\n" +
                                          "\n" +
                                          "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://ATest']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='ATest.testName' locationHint='java:test://ATest.testName|[0|]']\n" +
                                          "\n" +
                                          "##teamcity[testIgnored name='ATest.testName']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='ATest.testName']\n" +
                                          "##teamcity[testSuiteFinished name='ATest']\n", StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testParallelTestExecutionPreserveInvocationCount() throws Exception {
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
                                          "##teamcity[testCount count='1']\n" +
                                          "\n" +
                                          "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://ATest']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='ATest.testName' locationHint='java:test://ATest.testName|[0|]']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='ATest.testName']\n" +
                                          "##teamcity[testCount count='1']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='ATest.testName1' locationHint='java:test://ATest.testName1|[0|]']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='ATest.testName1']\n" +
                                          "##teamcity[testCount count='1']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='ATest.testName (1)' locationHint='java:test://ATest.testName|[1|]']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='ATest.testName (1)']\n" +
                                          "##teamcity[testSuiteFinished name='ATest']\n", StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testParallelSameNameTestExecution() throws Exception {
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
                                          "##teamcity[testCount count='1']\n" +
                                          "\n" +
                                          "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://ATest']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='ATest.testName' locationHint='java:test://ATest.testName|[0|]']\n" +
                                          "##teamcity[testCount count='1']\n" +
                                          "##teamcity[testSuiteFinished name='ATest']\n" +
                                          "\n" +
                                          "##teamcity[testSuiteStarted name ='BTest' locationHint = 'java:suite://BTest']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='BTest.testName' locationHint='java:test://BTest.testName|[0|]']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='ATest.testName']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='BTest.testName']\n" +
                                          "##teamcity[testSuiteFinished name='BTest']\n", StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testFailureWithoutStart() throws Exception {

    final StringBuffer buf = new StringBuffer();
    final IDEATestNGRemoteListener listener = createListener(buf);
    listener.onStart((ISuite)null);
    listener.onTestFailure(new MockTestNGResult("ATest", "testName", createExceptionWithoutTrace(), ArrayUtil.EMPTY_OBJECT_ARRAY));
    listener.onFinish((ISuite)null);

    Assert.assertEquals("output: " + buf, "##teamcity[enteredTheMatrix]\n" +
                                          "##teamcity[testCount count='1']\n" +
                                          "\n" +
                                          "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://ATest']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='ATest.testName' locationHint='java:test://ATest.testName|[0|]']\n" +
                                          "##teamcity[testFailed name='ATest.testName' details='java.lang.Exception|n' error='true' message='']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='ATest.testName']\n" +
                                          "##teamcity[testSuiteFinished name='ATest']\n", StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testSkipMethodAfterStartTest() throws Exception {
    final StringBuffer buf = new StringBuffer();
    final IDEATestNGRemoteListener listener = createListener(buf);
    listener.onStart((ISuite)null);
    final MockTestNGResult result = new MockTestNGResult("ATest", "testName");
    listener.onTestStart(result);
    listener.onTestSkipped(result);
    listener.onFinish((ISuite)null);

    Assert.assertEquals("output: " + buf, "##teamcity[enteredTheMatrix]\n" +
                                          "##teamcity[testCount count='1']\n" +
                                          "\n" +
                                          "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://ATest']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='ATest.testName' locationHint='java:test://ATest.testName|[0|]']\n" +
                                          "\n" +
                                          "##teamcity[testIgnored name='ATest.testName']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='ATest.testName']\n" +
                                          "##teamcity[testSuiteFinished name='ATest']\n", StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testOneTestMethodWithMultipleInvocationCount() throws Exception {
    final XmlSuite suite = new XmlSuite();
    final XmlTest test = new XmlTest();
    final XmlClass xmlClass = new XmlClass("a.ATest", false);
    xmlClass.getIncludedMethods().add(new XmlInclude("test1", Arrays.asList(0, 1, 2), 0));
    test.getClasses().add(xmlClass);
    suite.getTests().add(test);

    doTest(suite, "##teamcity[testCount count='1']\n" +
                  "\n" +
                  "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://a.ATest']\n" +
                  "\n" +
                  "##teamcity[testStarted name='ATest.test1|[0|]' locationHint='java:test://a.ATest.test1|[0|]']\n" +
                  "\n" +
                  "##teamcity[testFinished name='ATest.test1|[0|]']\n" +
                  "##teamcity[testCount count='1']\n" +
                  "\n" +
                  "##teamcity[testStarted name='ATest.test1|[1|] (1)' locationHint='java:test://a.ATest.test1|[1|]']\n" +
                  "\n" +
                  "##teamcity[testFinished name='ATest.test1|[1|] (1)']\n" +
                  "##teamcity[testCount count='1']\n" +
                  "\n" +
                  "##teamcity[testStarted name='ATest.test1|[2|] (2)' locationHint='java:test://a.ATest.test1|[2|]']\n" +
                  "\n" +
                  "##teamcity[testFinished name='ATest.test1|[2|] (2)']\n");
  }

  @Test
  public void testConfigurationMethods() throws Exception {
    final StringBuffer buf = new StringBuffer();
    final IDEATestNGRemoteListener listener = createListener(buf);
    final String className = "a.ATest";
    listener.onSuiteStart(className, true);
    for(String methodName : new String[] {"test1", "test2"}) {
      listener.onConfigurationSuccess(new MockTestNGResult(className, "setUp"));
      final MockTestNGResult result = new MockTestNGResult(className, methodName);
      listener.onTestStart(result);
      listener.onTestFinished(result);
      listener.onConfigurationSuccess(new MockTestNGResult(className, "tearDown"));
    }
    listener.onSuiteFinish(className);

    Assert.assertEquals("output: " + buf, "\n" +
                                          "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://a.ATest']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='ATest.setUp' locationHint='java:test://a.ATest.setUp' config='true']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='ATest.setUp']\n" +
                                          "##teamcity[testCount count='1']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='ATest.test1' locationHint='java:test://a.ATest.test1|[0|]']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='ATest.test1']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='ATest.tearDown' locationHint='java:test://a.ATest.tearDown' config='true']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='ATest.tearDown']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='ATest.setUp' locationHint='java:test://a.ATest.setUp' config='true']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='ATest.setUp']\n" +
                                          "##teamcity[testCount count='1']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='ATest.test2' locationHint='java:test://a.ATest.test2|[0|]']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='ATest.test2']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='ATest.tearDown' locationHint='java:test://a.ATest.tearDown' config='true']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='ATest.tearDown']\n" +
                                          "##teamcity[testSuiteFinished name='a.ATest']\n", StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testConfigurationFailure() throws Exception {
    final StringBuffer buf = new StringBuffer();
    final IDEATestNGRemoteListener listener = createListener(buf);
    final String className = "a.ATest";
    listener.onSuiteStart(className, true);
    listener.onConfigurationFailure(new MockTestNGResult(className, "setUp", createExceptionWithoutTrace(), ArrayUtil.EMPTY_OBJECT_ARRAY));
    listener.onSuiteFinish(className);

    Assert.assertEquals("output: " + buf, "\n" +
                                          "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://a.ATest']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='ATest.setUp' locationHint='java:test://a.ATest.setUp' config='true']\n" +
                                          "##teamcity[testFailed name='ATest.setUp' details='java.lang.Exception|n' error='true' message='']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='ATest.setUp']\n" +
                                          "##teamcity[testSuiteFinished name='a.ATest']\n", StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testNullParameters() throws Exception {
    final StringBuffer buf = new StringBuffer();
    final IDEATestNGRemoteListener listener = createListener(buf);
    final MockTestNGResult result = new MockTestNGResult("ATest", "testMe", null, new Object[]{null, null});
    listener.onTestStart(result);
    listener.onTestFinished(result);
    Assert.assertEquals("output: " + buf, "##teamcity[testCount count='1']\n" +
                                          "\n" +
                                          "##teamcity[testSuiteStarted name ='ATest' locationHint = 'java:suite://ATest']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='ATest.testMe|[null, null|]' locationHint='java:test://ATest.testMe|[0|]']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='ATest.testMe|[null, null|]']\n", StringUtil.convertLineSeparators(buf.toString()));
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
        public void write(int b) throws IOException {
          buf.append(new String(new byte[]{(byte)b}));
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

    public MockTestNGResult(String className, String methodName, Throwable throwable, Object[] params) {
      myClassName = className;
      myMethodName = methodName;
      myThrowable = throwable;
      myParams = params;
    }

    private MockTestNGResult(String className, String methodName) {
     this(className, methodName, null, ArrayUtil.EMPTY_OBJECT_ARRAY);
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
}
