package org.jetbrains.plugins.ruby.testing.sm.runner;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.Printable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.ui.TestsOutputConsolePrinter;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.plugins.ruby.testing.sm.runner.ui.MockPrinter;
import org.jetbrains.plugins.ruby.testing.sm.runner.ui.SMTRunnerConsoleView;
import org.jetbrains.plugins.ruby.testing.sm.runner.ui.TestResultsViewer;

/**
 * @author Roman Chernyatchik
 */
public class SMTRunnerConsoleTest extends BaseSMTRunnerTestCase {
  private MyConsoleView myConsole;
  private GeneralToSMTRunnerEventsConvertor myEventsProcessor;
  private MockPrinter myMockResetablePrinter;
  private SMTestProxy myRootSuite;
  private TestResultsViewer myResultsViewer;

  private class MyConsoleView extends SMTRunnerConsoleView {
    private TestsOutputConsolePrinter myTestsOutputConsolePrinter;

    private MyConsoleView(final TestConsoleProperties consoleProperties, final TestResultsViewer resultsViewer) {
      super(consoleProperties, resultsViewer);

      myTestsOutputConsolePrinter = new TestsOutputConsolePrinter(MyConsoleView.this, consoleProperties) {
        @Override
        public void print(final String text, final ConsoleViewContentType contentType) {
          myMockResetablePrinter.print(text, contentType);
        }
      };
    }

    @Override
    public TestsOutputConsolePrinter getPrinter() {
      return myTestsOutputConsolePrinter;
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final TestConsoleProperties consoleProperties = createConsoleProperties();
    myResultsViewer = createResultsViewer(consoleProperties);

    myMockResetablePrinter = new MockPrinter(true);
    myRootSuite = myResultsViewer.getTestsRootNode();
    myConsole = new MyConsoleView(consoleProperties, myResultsViewer);
    myEventsProcessor = new GeneralToSMTRunnerEventsConvertor(myResultsViewer.getTestsRootNode());

    myEventsProcessor.onStartTesting();
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
    final SMTestProxy myTest1 = startTestWithPrinter("my_test");

    myEventsProcessor.onTestFailure("my_test", "error msg", "method1:1\nmethod2:2", false);
    myEventsProcessor.onTestOutput("my_test", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test", "stderr1 ", false);

    assertAllOutputs(myMockResetablePrinter, "stdout1 ", "\nerror msg\nmethod1:1\nmethod2:2\nstderr1 ", "");

    final MockPrinter mockPrinter1 = new MockPrinter(true);
    mockPrinter1.onNewAvaliable(myTest1);
    assertAllOutputs(mockPrinter1, "stdout1 ", "stderr1 \nerror msg\nmethod1:1\nmethod2:2\n", "");

    //other output order
    final SMTestProxy myTest2 = startTestWithPrinter("my_test2");
    myEventsProcessor.onTestOutput("my_test2", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test2", "stderr1 ", false);
    myEventsProcessor.onTestFailure("my_test2", "error msg", "method1:1\nmethod2:2", false);

    assertAllOutputs(myMockResetablePrinter, "stdout1 ", "stderr1 \nerror msg\nmethod1:1\nmethod2:2\n", "");
    final MockPrinter mockPrinter2 = new MockPrinter(true);
    mockPrinter2.onNewAvaliable(myTest2);
    assertAllOutputs(mockPrinter2, "stdout1 ", "stderr1 \nerror msg\nmethod1:1\nmethod2:2\n", "");
  }

 public void testProcessor_OnError() {
    final SMTestProxy myTest1 = startTestWithPrinter("my_test");

    myEventsProcessor.onTestFailure("my_test", "error msg", "method1:1\nmethod2:2", true);
    myEventsProcessor.onTestOutput("my_test", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test", "stderr1 ", false);

    assertAllOutputs(myMockResetablePrinter, "stdout1 ", "\nerror msg\nmethod1:1\nmethod2:2\nstderr1 ", "");

    final MockPrinter mockPrinter1 = new MockPrinter(true);
    mockPrinter1.onNewAvaliable(myTest1);
    assertAllOutputs(mockPrinter1, "stdout1 ", "stderr1 \nerror msg\nmethod1:1\nmethod2:2\n", "");

    //other output order
    final SMTestProxy myTest2 = startTestWithPrinter("my_test2");
    myEventsProcessor.onTestOutput("my_test2", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test2", "stderr1 ", false);
    myEventsProcessor.onTestFailure("my_test2", "error msg", "method1:1\nmethod2:2", true);

    assertAllOutputs(myMockResetablePrinter, "stdout1 ", "stderr1 \nerror msg\nmethod1:1\nmethod2:2\n", "");
    final MockPrinter mockPrinter2 = new MockPrinter(true);
    mockPrinter2.onNewAvaliable(myTest2);
    assertAllOutputs(mockPrinter2, "stdout1 ", "stderr1 \nerror msg\nmethod1:1\nmethod2:2\n", "");
  }

  public void testProcessor_OnIgnored() {
    final SMTestProxy myTest1 = startTestWithPrinter("my_test");

    myEventsProcessor.onTestIgnored("my_test", "ignored msg");
    myEventsProcessor.onTestOutput("my_test", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test", "stderr1 ", false);

    assertAllOutputs(myMockResetablePrinter, "stdout1 ", "stderr1 ", "\nTest ignored: ignored msg");

    final MockPrinter mockPrinter1 = new MockPrinter(true);
    mockPrinter1.onNewAvaliable(myTest1);
    assertAllOutputs(mockPrinter1, "stdout1 ", "stderr1 ", "\nTest ignored: ignored msg");

    //other output order
    final SMTestProxy myTest2 = startTestWithPrinter("my_test2");
    myEventsProcessor.onTestOutput("my_test2", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test2", "stderr1 ", false);
    myEventsProcessor.onTestIgnored("my_test2", "ignored msg");

    assertAllOutputs(myMockResetablePrinter, "stdout1 ", "stderr1 ", "\nTest ignored: ignored msg");
    final MockPrinter mockPrinter2 = new MockPrinter(true);
    mockPrinter2.onNewAvaliable(myTest2);
    assertAllOutputs(mockPrinter2, "stdout1 ", "stderr1 ", "\nTest ignored: ignored msg");
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
    final SMTestProxy mySuite = myEventsProcessor.getCurrentSuite();
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

  public void testStopCollectingOutput() {
    myResultsViewer.selectAndNotify(myResultsViewer.getTestsRootNode());
    myConsole.attachToProcess(null);

    myEventsProcessor.onStartTesting();
    myEventsProcessor.onSuiteStarted("suite");
    final SMTestProxy suite = myEventsProcessor.getCurrentSuite();
    myEventsProcessor.onSuiteFinished("suite");
    myEventsProcessor.onUncapturedOutput("preved", ProcessOutputTypes.STDOUT);
    myEventsProcessor.onFinishTesting();

    //myResultsViewer.selectAndNotify(suite);
    //the string above doesn't update tree immediately so we should simulate update
    myConsole.getPrinter().updateOnTestSelected(suite);

    //Lets reset printer /clear console/ before selection changed to
    //get after selection event only actual ouptut
    myMockResetablePrinter.resetIfNecessary();

    //myResultsViewer.selectAndNotify(myResultsViewer.getTestsRootNode());
    //the string above doesn't update tree immediately so we should simulate update
    myConsole.getPrinter().updateOnTestSelected(myResultsViewer.getTestsRootNode());

    assertAllOutputs(myMockResetablePrinter, "preved", "","Empty test suite.\n");
  }

  private SMTestProxy startTestWithPrinter(final String testName) {
    myEventsProcessor.onTestStarted(testName);
    final SMTestProxy proxy =
        myEventsProcessor.getProxyByFullTestName(myEventsProcessor.getFullTestName(testName));
    proxy.setPrintLinstener(myMockResetablePrinter);
    return proxy;
  }

  private void sendToTestProxyStdOut(final SMTestProxy proxy, final String text) {
    proxy.addLast(new Printable() {
      public void printOn(final Printer printer) {
        printer.print(text, ConsoleViewContentType.NORMAL_OUTPUT);
      }
    });
  }
}
