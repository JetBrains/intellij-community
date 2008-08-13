package org.jetbrains.plugins.ruby.testing.testunit.runner.server;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import jetbrains.buildServer.messages.serviceMessages.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.testing.testunit.runner.GeneralTestEventsProcessor;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Roman Chernyatchik
 *
 * This implementation also supports messages splitted in parts by early flush.
 * Implementation assumes that buffer is being flushed on line end or by timer,
 * i.e. incomming text contains no more than one line's end marker ('\r', '\n', or "\r\n")
 * (e.g. process was run with IDEA program's runner)
 */
public class OutputToGeneralTestEventsConverter implements ProcessOutputConsumer {
  private static final Logger LOG = Logger.getInstance(OutputToGeneralTestEventsConverter.class.getName());
  
  private List<GeneralTestEventsProcessor> myProcessors = new ArrayList<GeneralTestEventsProcessor>();
  private MyServiceMessageVisitor myServiceMessageVisitor;

  private final StringBuilder myStdoutBuffer;

  public OutputToGeneralTestEventsConverter() {
    myServiceMessageVisitor = new MyServiceMessageVisitor();
    myStdoutBuffer = new StringBuilder();
  }

  public void addProcessor(final GeneralTestEventsProcessor processor) {
    myProcessors.add(processor);
  }

  public void process(final String text, final Key outputType) {
    if (ProcessOutputTypes.STDOUT.equals(outputType)) {
      // we check for consistensy only std output
      // because all events must be send to stdout
      processStdOutConsistently(text);
    } else {
      processConsistentText(text, outputType);
    }
  }

  /**
   * Flashes the rest of stdout text buffer after output has been stopped
   */
  public void flushBufferBeforeTerminating() {
    flushStdOutputBuffer();
  }

  public void dispose() {
    myProcessors.clear();
  }

  private void flushStdOutputBuffer() {
    processConsistentText(myStdoutBuffer.toString(), ProcessOutputTypes.STDOUT);
    myStdoutBuffer.setLength(0);
  }

  private void processStdOutConsistently(final String text) {
    final int textLength = text.length();
    if (textLength == 0) {
      return;
    }

    myStdoutBuffer.append(text);

    final char lastChar = text.charAt(textLength - 1);
    if (lastChar == '\n' || lastChar == '\r') {
      // buffer contains consistent string
      flushStdOutputBuffer();
    }
  }

  private void processConsistentText(final String text, final Key outputType) {
    try {
      final ServiceMessage serviceMessage = ServiceMessage.parse(text);
      if (serviceMessage != null) {
        serviceMessage.visit(myServiceMessageVisitor);
      } else {
        // Filters \n
        if (text.equals("\n")) {
          // ServiceMessages protocol requires that every message
          // should start with new line, so such behaviour may led to generating
          // some number of useless \n.
          //
          // This will not affect tests output because all
          // output will be in TestOutput message
          return;
        }
        //fire current output
        fireOnUncapturedOutput(text, outputType);
      }
    }
    catch (ParseException e) {
      LOG.error(e);
    }
  }

  private void fireOnTestStarted(final String testName) {
    for (GeneralTestEventsProcessor processor : myProcessors) {
      processor.onTestStarted(testName);
    }
  }

  private void fireOnTestFailure(final String testName, final String localizedMessage, final String stackTrace,
                                 final boolean isTestError) {

    for (GeneralTestEventsProcessor processor : myProcessors) {
      processor.onTestFailure(testName, localizedMessage, stackTrace, isTestError);
    }
  }

  private void fireOnTestOutput(final String testName, final String text, final boolean stdOut) {
    for (GeneralTestEventsProcessor processor : myProcessors) {
      processor.onTestOutput(testName, text, stdOut);
    }
  }

  private void fireOnUncapturedOutput(final String text, final Key outputType) {
    for (GeneralTestEventsProcessor processor : myProcessors) {
      processor.onUncapturedOutput(text, outputType);
    }
  }

  private void fireOnTestsCountInSuite(final int count) {
    for (GeneralTestEventsProcessor processor : myProcessors) {
      processor.onTestsCountInSuite(count);
    }
  }

  private void fireOnTestFinished(final String testName, final int duration) {
    for (GeneralTestEventsProcessor processor : myProcessors) {
      processor.onTestFinished(testName, duration);
    }
  }

  private void fireOnSuiteStarted(final String suiteName) {
    for (GeneralTestEventsProcessor processor : myProcessors) {
      processor.onSuiteStarted(suiteName);
    }
  }

  private void fireOnSuiteFinished(final String suiteName) {
    for (GeneralTestEventsProcessor processor : myProcessors) {
      processor.onSuiteFinished(suiteName);
    }
  }

  private class MyServiceMessageVisitor extends DefaultServiceMessageVisitor {
    @NonNls public static final String KEY_TESTS_COUNT = "testCount";
    @NonNls private static final String ATTR_KEY_TEST_ERROR = "error";
    @NonNls private static final String ATTR_KEY_TEST_DURATION = "duration";

    public void visitTestSuiteStarted(@NotNull final TestSuiteStarted suiteStarted) {
      fireOnSuiteStarted(suiteStarted.getSuiteName());
    }

    public void visitTestSuiteFinished(@NotNull final TestSuiteFinished suiteFinished) {
      fireOnSuiteFinished(suiteFinished.getSuiteName());
    }

    public void visitTestStarted(@NotNull final TestStarted testStarted) {
      fireOnTestStarted(testStarted.getTestName());
    }

    public void visitTestFinished(@NotNull final TestFinished testFinished) {
      final String durationStr = testFinished.getAttributes().get(ATTR_KEY_TEST_DURATION);

      // Test's duration in milliseconds
      int duration = 0;
      try {
        duration = Integer.parseInt(durationStr);
      } catch (NumberFormatException ex) {
        LOG.error(ex);
      }
      fireOnTestFinished(testFinished.getTestName(), duration);
    }

    public void visitTestIgnored(@NotNull final TestIgnored testIgnored) {
      //TODO[romeo] implement
    }

    public void visitTestStdOut(@NotNull final TestStdOut testStdOut) {
      fireOnTestOutput(testStdOut.getTestName(),testStdOut.getStdOut(), true);
    }

    public void visitTestStdErr(@NotNull final TestStdErr testStdErr) {
      fireOnTestOutput(testStdErr.getTestName(),testStdErr.getStdErr(), false);
    }

    public void visitTestFailed(@NotNull final TestFailed testFailed) {
      final boolean isTestError = testFailed.getAttributes().get(ATTR_KEY_TEST_ERROR) != null;

      fireOnTestFailure(testFailed.getTestName(), testFailed.getFailureMessage(), testFailed.getStacktrace(), isTestError);
    }

    public void visitPublishArtifacts(@NotNull final PublishArtifacts publishArtifacts) {
      //Do nothing
    }

    public void visitProgressMessage(@NotNull final ProgressMessage progressMessage) {
      //Do nothing
    }

    public void visitProgressStart(@NotNull final ProgressStart progressStart) {
      //Do nothing
    }

    public void visitProgressFinish(@NotNull final ProgressFinish progressFinish) {
      //Do nothing
    }

    public void visitBuildStatus(@NotNull final BuildStatus buildStatus) {
      //Do nothing
    }

    public void visitBuildNumber(@NotNull final BuildNumber buildNumber) {
      //Do nothing
    }

    public void visitBuildStatisticValue(@NotNull final BuildStatisticValue buildStatsValue) {
      //Do nothing
    }

    public void visitServiceMessage(@NotNull final ServiceMessage msg) {
      final String name = msg.getMessageName();

      if (KEY_TESTS_COUNT.equals(name)) {
        processTestCountInSuite(msg);
      } else {
        //Do nothing
      }
    }

    private void processTestCountInSuite(final ServiceMessage msg) {
      final String countStr = msg.getArgument();
      int count = 0;
      try {
        count = Integer.parseInt(countStr);
      } catch (NumberFormatException ex) {
        LOG.error(ex);
      }
      fireOnTestsCountInSuite(count);
    }
  }
}
