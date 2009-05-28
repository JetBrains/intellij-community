package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.messages.serviceMessages.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  
  private final List<GeneralTestEventsProcessor> myProcessors = new ArrayList<GeneralTestEventsProcessor>();
  private final MyServiceMessageVisitor myServiceMessageVisitor;

  private final List<Pair<String, Key>> myOutputPairs;

  public OutputToGeneralTestEventsConverter() {
    myServiceMessageVisitor = new MyServiceMessageVisitor();
    myOutputPairs = new ArrayList<Pair<String, Key>>();
  }

  public void addProcessor(final GeneralTestEventsProcessor processor) {
    myProcessors.add(processor);
  }

  public void process(final String text, final Key outputType) {
    if (outputType != ProcessOutputTypes.STDERR || outputType != ProcessOutputTypes.SYSTEM) {
      // we check for consistensy only std output
      // because all events must be send to stdout
      processStdOutConsistently(text, outputType);
    } else {
      processConsistentText(text, outputType, false);
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
    // if osColoredProcessHandler was attached it can split string with several colors
    // in several  parts. Thus '\n' symbol may be send as one part with some color
    // such situation should differ from single '\n' from process that is used by TC reporters
    // to separate TC commands from other stuff + optimize flushing
    // TODO: probably in IDEA mode such runners shouldn't add explicit \n because we can
    // successfully process broken messages across several flushes
    // size of parts may tell us either \n was single in original flushed data or it was
    // separated by process handler
    final boolean isTCLikeFakeOutput = myOutputPairs.size() == 1;
    for (Pair<String, Key> textKeyPair : myOutputPairs) {
      processConsistentText(textKeyPair.first, textKeyPair.second, isTCLikeFakeOutput);
    }
    myOutputPairs.clear();
  }

  private void processStdOutConsistently(final String text, final Key outputType) {
    final int textLength = text.length();
    if (textLength == 0) {
      return;
    }

    myOutputPairs.add(new Pair<String, Key>(text, outputType));

    final char lastChar = text.charAt(textLength - 1);
    if (lastChar == '\n' || lastChar == '\r') {
      // buffer contains consistent string
      flushStdOutputBuffer();
    }
  }

  private void processConsistentText(final String text, final Key outputType, boolean tcLikeFakeOutput) {
    try {
      final ServiceMessage serviceMessage = ServiceMessage.parse(text);
      if (serviceMessage != null) {
        serviceMessage.visit(myServiceMessageVisitor);
      } else {
        // Filters \n
        if (text.equals("\n") && tcLikeFakeOutput) {
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

  private void fireOnTestStarted(final String testName, @Nullable  final String locationUrl) {
    for (GeneralTestEventsProcessor processor : myProcessors) {
      processor.onTestStarted(testName, locationUrl);
    }
  }

  private void fireOnTestFailure(final String testName, final String localizedMessage, final String stackTrace,
                                 final boolean isTestError) {

    for (GeneralTestEventsProcessor processor : myProcessors) {
      processor.onTestFailure(testName, localizedMessage, stackTrace, isTestError);
    }
  }

  private void fireOnTestIgnored(final String testName, final String ignoreComment,
                                 @Nullable final String details) {

    for (GeneralTestEventsProcessor processor : myProcessors) {
      processor.onTestIgnored(testName, ignoreComment, details);
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

  private void fireOnSuiteStarted(final String suiteName, @Nullable final String locationUrl) {
    for (GeneralTestEventsProcessor processor : myProcessors) {
      processor.onSuiteStarted(suiteName, locationUrl);
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
    @NonNls private static final String ATTR_KEY_TEST_COUNT = "count";
    @NonNls private static final String ATTR_KEY_TEST_DURATION = "duration";
    @NonNls private static final String ATTR_KEY_LOCATION_URL = "location";
    @NonNls private static final String ATTR_KEY_STACKTRACE_DETAILS = "details";

    public void visitTestSuiteStarted(@NotNull final TestSuiteStarted suiteStarted) {
      final String locationUrl = suiteStarted.getAttributes().get(ATTR_KEY_LOCATION_URL);
      fireOnSuiteStarted(suiteStarted.getSuiteName(), locationUrl);
    }

    public void visitTestSuiteFinished(@NotNull final TestSuiteFinished suiteFinished) {
      fireOnSuiteFinished(suiteFinished.getSuiteName());
    }

    public void visitTestStarted(@NotNull final TestStarted testStarted) {
      final String locationUrl = testStarted.getAttributes().get(ATTR_KEY_LOCATION_URL);
      fireOnTestStarted(testStarted.getTestName(), locationUrl);
    }

    public void visitTestFinished(@NotNull final TestFinished testFinished) {
      //TODO
      //final Integer duration = testFinished.getTestDuration();
      //fireOnTestFinished(testFinished.getTestName(), duration != null ? duration.intValue() : 0);

      final String durationStr = testFinished.getAttributes().get(ATTR_KEY_TEST_DURATION);

      // Test's duration in milliseconds
      int duration = 0;

      if (!StringUtil.isEmptyOrSpaces(durationStr)) {
        try {
          duration = Integer.parseInt(durationStr);
        } catch (NumberFormatException ex) {
          LOG.error(ex);
        }
      }
      
      fireOnTestFinished(testFinished.getTestName(), duration);
    }

    public void visitTestIgnored(@NotNull final TestIgnored testIgnored) {
      final String details = testIgnored.getAttributes().get(ATTR_KEY_STACKTRACE_DETAILS);
      fireOnTestIgnored(testIgnored.getTestName(), testIgnored.getIgnoreComment(), details);
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
      final String countStr = msg.getAttributes().get(ATTR_KEY_TEST_COUNT);
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
