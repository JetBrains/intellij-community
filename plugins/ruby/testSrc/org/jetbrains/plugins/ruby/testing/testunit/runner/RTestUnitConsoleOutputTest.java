package org.jetbrains.plugins.ruby.testing.testunit.runner;

import com.intellij.execution.testframework.Printable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.MockPrinter;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.RTestUnitConsoleView;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.TestResultsViewer;

/**
 * @author Roman Chernyatchik
 */
public class RTestUnitConsoleOutputTest extends BaseRUnitTestsTestCase {
  private RTestUnitConsoleView myConsole;
  private GeneralToRTestUnitEventsConvertor myEventsProcessor;
  private MockPrinter myMockResetablePrinter;
  private RTestUnitTestProxy myRootSuite;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final RTestUnitConsoleProperties consoleProperties = createConsoleProperties();
    final TestResultsViewer resultsViewer = createResultsViewer(consoleProperties);

    myRootSuite = resultsViewer.getTestsRootNode();
    myConsole = new RTestUnitConsoleView(consoleProperties, resultsViewer);
    myEventsProcessor = new GeneralToRTestUnitEventsConvertor(resultsViewer.getTestsRootNode());

    myEventsProcessor.onStartTesting();
    myMockResetablePrinter = new MockPrinter(true);
  }

  @Override
  protected void tearDown() throws Exception {
    Disposer.dispose(myEventsProcessor);
    Disposer.dispose(myConsole);

    super.tearDown();
  }

  public void testPrintTestProxy() {
    mySimpleTest.setPrintLinstener(myMockResetablePrinter);
    mySimpleTest.addLast(new Printable() {
      public void printOn(final Printer printer) {
        printer.print("std out", ConsoleViewContentType.NORMAL_OUTPUT);
        printer.print("std err", ConsoleViewContentType.ERROR_OUTPUT);
        printer.print("std sys", ConsoleViewContentType.SYSTEM_OUTPUT);
      }
    });
    assertAllOutputs(myMockResetablePrinter, "std out", "std err", "std sys");
  }

  public void testAddStdOut() {
    mySimpleTest.setPrintLinstener(myMockResetablePrinter);

    mySimpleTest.addStdOutput("one");
    assertStdOutput(myMockResetablePrinter, "one");

    mySimpleTest.addStdErr("two");
    assertStdErr(myMockResetablePrinter, "two");

    mySimpleTest.addStdOutput("one");
    mySimpleTest.addStdOutput("one");
    mySimpleTest.addStdErr("two");
    mySimpleTest.addStdErr("two");
    assertAllOutputs(myMockResetablePrinter, "oneone", "twotwo", "");
  }

  public void testAddStdSys() {
    mySimpleTest.setPrintLinstener(myMockResetablePrinter);

    mySimpleTest.addSystemOutput("sys");
    assertAllOutputs(myMockResetablePrinter, "", "", "sys");
  }

  public void testPrintTestProxy_Order() {
    mySimpleTest.setPrintLinstener(myMockResetablePrinter);

    sendToTestProxyStdOut(mySimpleTest, "first ");
    sendToTestProxyStdOut(mySimpleTest, "second");

    assertStdOutput(myMockResetablePrinter, "first second");
  }

  public void testSetPrintListener_ForExistingChildren() {
    mySuite.addChild(mySimpleTest);

    mySuite.setPrintLinstener(myMockResetablePrinter);

    sendToTestProxyStdOut(mySimpleTest, "child ");
    sendToTestProxyStdOut(mySuite, "root");

    assertStdOutput(myMockResetablePrinter, "child root");
  }

  public void testSetPrintListener_OnNewChild() {
    mySuite.setPrintLinstener(myMockResetablePrinter);

    sendToTestProxyStdOut(mySuite, "root ");

    sendToTestProxyStdOut(mySimpleTest, "[child old msg] ");
    mySuite.addChild(mySimpleTest);

    sendToTestProxyStdOut(mySuite, "{child added} ");
    sendToTestProxyStdOut(mySimpleTest, "[child new msg]");
    // printer for parent have been already set, thus new
    // child should immeadiatly print himself on this printer
    assertStdOutput(myMockResetablePrinter, "root [child old msg] {child added} [child new msg]");
  }

  public void testDefferedPrint() {
    sendToTestProxyStdOut(mySimpleTest, "one ");
    sendToTestProxyStdOut(mySimpleTest, "two ");
    sendToTestProxyStdOut(mySimpleTest, "three");

    myMockResetablePrinter.onNewAvaliable(mySimpleTest);
    assertStdOutput(myMockResetablePrinter, "one two three");

    myMockResetablePrinter.resetIfNecessary();
    assertFalse(myMockResetablePrinter.hasPrinted());

    myMockResetablePrinter.onNewAvaliable(mySimpleTest);
    assertStdOutput(myMockResetablePrinter, "one two three");
  }

  public void testProcessor_OnTestStdOutput() {
    startTestWithPrinter("my_test");

    myEventsProcessor.onTestOutput("my_test", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test", "stdout2", true);

    assertStdOutput(myMockResetablePrinter, "stdout1 stdout2");
  }

  public void testProcessor_OnTestStdErr() {
    startTestWithPrinter("my_test");

    myEventsProcessor.onTestOutput("my_test", "stderr1 ", false);
    myEventsProcessor.onTestOutput("my_test", "stderr2", false);

    assertStdErr(myMockResetablePrinter, "stderr1 stderr2");
  }

  public void testProcessor_OnTestMixedStd() {
    startTestWithPrinter("my_test");

    myEventsProcessor.onTestOutput("my_test", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test", "stderr1 ", false);
    myEventsProcessor.onTestOutput("my_test", "stdout2", true);
    myEventsProcessor.onTestOutput("my_test", "stderr2", false);

    assertAllOutputs(myMockResetablePrinter, "stdout1 stdout2", "stderr1 stderr2", "");
  }

  public void testProcessor_OnFailure() {
    final RTestUnitTestProxy myTest1 = startTestWithPrinter("my_test");

    myEventsProcessor.onTestFailure("my_test", "error msg", "method1:1\nmethod2:2", false);
    myEventsProcessor.onTestOutput("my_test", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test", "stderr1 ", false);

    assertAllOutputs(myMockResetablePrinter, "stdout1 ", "\nerror msg\nmethod1:1\nmethod2:2\nstderr1 ", "");

    final MockPrinter mockPrinter1 = new MockPrinter(true);
    mockPrinter1.onNewAvaliable(myTest1);
    assertAllOutputs(mockPrinter1, "stdout1 ", "stderr1 \nerror msg\nmethod1:1\nmethod2:2\n", "");

    //other output order
    final RTestUnitTestProxy myTest2 = startTestWithPrinter("my_test2");
    myEventsProcessor.onTestOutput("my_test2", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test2", "stderr1 ", false);
    myEventsProcessor.onTestFailure("my_test2", "error msg", "method1:1\nmethod2:2", false);

    assertAllOutputs(myMockResetablePrinter, "stdout1 ", "stderr1 \nerror msg\nmethod1:1\nmethod2:2\n", "");
    final MockPrinter mockPrinter2 = new MockPrinter(true);
    mockPrinter2.onNewAvaliable(myTest2);
    assertAllOutputs(mockPrinter2, "stdout1 ", "stderr1 \nerror msg\nmethod1:1\nmethod2:2\n", "");
  }

 public void testProcessor_OnError() {
    final RTestUnitTestProxy myTest1 = startTestWithPrinter("my_test");

    myEventsProcessor.onTestFailure("my_test", "error msg", "method1:1\nmethod2:2", true);
    myEventsProcessor.onTestOutput("my_test", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test", "stderr1 ", false);

    assertAllOutputs(myMockResetablePrinter, "stdout1 ", "\nerror msg\nmethod1:1\nmethod2:2\nstderr1 ", "");

    final MockPrinter mockPrinter1 = new MockPrinter(true);
    mockPrinter1.onNewAvaliable(myTest1);
    assertAllOutputs(mockPrinter1, "stdout1 ", "stderr1 \nerror msg\nmethod1:1\nmethod2:2\n", "");

    //other output order
    final RTestUnitTestProxy myTest2 = startTestWithPrinter("my_test2");
    myEventsProcessor.onTestOutput("my_test2", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test2", "stderr1 ", false);
    myEventsProcessor.onTestFailure("my_test2", "error msg", "method1:1\nmethod2:2", true);

    assertAllOutputs(myMockResetablePrinter, "stdout1 ", "stderr1 \nerror msg\nmethod1:1\nmethod2:2\n", "");
    final MockPrinter mockPrinter2 = new MockPrinter(true);
    mockPrinter2.onNewAvaliable(myTest2);
    assertAllOutputs(mockPrinter2, "stdout1 ", "stderr1 \nerror msg\nmethod1:1\nmethod2:2\n", "");
  }

  public void testOnUncapturedOutput_BeforeProcessStarted() {
    myRootSuite.setPrintLinstener(myMockResetablePrinter);

    assertOnUncapturedOutput();
  }

  public void testOnUncapturedOutput_BeforeFirstSuiteStarted() {
    myRootSuite.setPrintLinstener(myMockResetablePrinter);

    myEventsProcessor.onStartTesting();
    assertOnUncapturedOutput();
  }

  public void testOnUncapturedOutput_SomeSuite() {
    myEventsProcessor.onStartTesting();

    myEventsProcessor.onSuiteStarted("my suite");
    final RTestUnitTestProxy mySuite = myEventsProcessor.getCurrentSuite();
    assertTrue(mySuite != myRootSuite);
    mySuite.setPrintLinstener(myMockResetablePrinter);

    assertOnUncapturedOutput();
  }

  public void testOnUncapturedOutput_SomeTest() {
    myEventsProcessor.onStartTesting();

    myEventsProcessor.onSuiteStarted("my suite");
    startTestWithPrinter("my test");

    assertOnUncapturedOutput();
  }


  public void assertOnUncapturedOutput() {
    myEventsProcessor.onUncapturedOutput("stdout", ProcessOutputTypes.STDOUT);
    myEventsProcessor.onUncapturedOutput("stderr", ProcessOutputTypes.STDERR);
    myEventsProcessor.onUncapturedOutput("system", ProcessOutputTypes.SYSTEM);

    assertAllOutputs(myMockResetablePrinter, "stdout", "stderr", "system");
  }

  public void assertStdOutput(final MockPrinter printer, final String out) {
    assertAllOutputs(printer, out, "", "");
  }

  public void assertStdErr(final MockPrinter printer, final String out) {
    assertAllOutputs(printer, "", out, "");
  }

  public void assertAllOutputs(final MockPrinter printer,
                              final String out, final String err, final String sys) {
    assertTrue(printer.hasPrinted());
    assertEquals(out, printer.getStdOut());
    assertEquals(err, printer.getStdErr());
    assertEquals(sys, printer.getStdSys());

    printer.resetIfNecessary();
  }

  private RTestUnitTestProxy startTestWithPrinter(final String testName) {
    myEventsProcessor.onTestStarted(testName);
    final RTestUnitTestProxy proxy =
        myEventsProcessor.getProxyByFullTestName(myEventsProcessor.getFullTestName(testName));
    proxy.setPrintLinstener(myMockResetablePrinter);
    return proxy;
  }

  private void sendToTestProxyStdOut(final RTestUnitTestProxy proxy, final String text) {
    proxy.addLast(new Printable() {
      public void printOn(final Printer printer) {
        printer.print(text, ConsoleViewContentType.NORMAL_OUTPUT);
      }
    });
  }
}
