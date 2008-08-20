package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.execution.testframework.PoolOfTestIcons;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.execution.testframework.ui.TestsProgressAnimator;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.ruby.lang.TextUtil;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.states.TestStateInfo;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class TestsPresentationUtil {
  @NonNls private static final String DOUBLE_SPACE = "  ";
  @NonNls private static final String WORLD_CREATION_TIME = "0.0";
  @NonNls private static final String SECONDS_SUFFIX = " " + RBundle.message("ruby.test.runner.ui.tests.tree.presentation.labels.seconds");
  @NonNls private static final String DURATION_UNKNOWN = RBundle.message(
      "ruby.test.runner.ui.tabs.statistics.columns.duration.unknown");
  @NonNls private static final String DURATION_NO_TESTS = RBundle.message(
      "ruby.test.runner.ui.tabs.statistics.columns.duration.no.tests");
  @NonNls private static final String DURATION_NOT_RUN = RBundle.message(
      "ruby.test.runner.ui.tabs.statistics.columns.duration.not.run");
  @NonNls private static final String DURATION_RUNNING_PREFIX = RBundle.message(
      "ruby.test.runner.ui.tabs.statistics.columns.duration.prefix.running");
  @NonNls private static final String DURATION_TERMINATED_PREFIX = RBundle.message(
      "ruby.test.runner.ui.tabs.statistics.columns.duration.prefix.terminated");
  @NonNls private static final String COLON = ": ";
  public static final SimpleTextAttributes PASSED_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, TestsUIUtil.PASSED_COLOR);
  public static final SimpleTextAttributes DEFFECT_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.RED);
  public static final SimpleTextAttributes TERMINATED_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.ORANGE);
  @NonNls private static final String RESULTS_NO_TESTS = RBundle.message("ruby.test.runner.ui.tabs.statistics.columns.results.no.tests");


  private TestsPresentationUtil() {
  }

  public static String getProgressStatus_Text(final long startTime,
                                              final long endTime,
                                              final int testsTotal,
                                              final int testsCount,
                                              final int failuresCount) {
    final StringBuilder sb = new StringBuilder();
    if (endTime == 0) {
      sb.append(RBundle.message("ruby.test.runner.ui.tests.tree.presentation.labels.running"));
    } else {
      sb.append(RBundle.message("ruby.test.runner.ui.tests.tree.presentation.labels.done"));
    }
    sb.append(' ').append(testsCount).append(' ');
    sb.append(RBundle.message("ruby.test.runner.ui.tests.tree.presentation.labels.of"));
    sb.append(' ').append(testsTotal);

    if (failuresCount > 0) {
      sb.append(DOUBLE_SPACE);
      sb.append(RBundle.message("ruby.test.runner.ui.tests.tree.presentation.labels.failed"));
      sb.append(' ').append(failuresCount);
    }
    if (endTime != 0) {
      final long time = endTime - startTime;
      sb.append(DOUBLE_SPACE);
      sb.append('(').append(time == 0 ? WORLD_CREATION_TIME : NumberFormat.getInstance().format((double)time/1000.0));
      sb.append(SECONDS_SUFFIX).append(')');
    }
    sb.append(DOUBLE_SPACE);

    return sb.toString();
  }

  public static void formatRootNodeWithChildren(final RTestUnitTestProxy testProxy,
                                          final RTestUnitTestTreeRenderer renderer) {
    renderer.setIcon(getIcon(testProxy, renderer.getConsoleProperties()));

    final TestStateInfo.Magnitude magnitude = testProxy.getMagnitudeInfo();

    final String text;
    if (magnitude == TestStateInfo.Magnitude.RUNNING_INDEX) {
      text = RBundle.message("ruby.test.runner.ui.tests.tree.presentation.labels.running.tests");
    } else if (magnitude == TestStateInfo.Magnitude.TERMINATED_INDEX) {
      text = RBundle.message("ruby.test.runner.ui.tests.tree.presentation.labels.was.terminated");
    } else {
      text = RBundle.message("ruby.test.runner.ui.tests.tree.presentation.labels.test.results");
    }
    renderer.append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public static void formatRootNodeWithoutChildren(final RTestUnitTestProxy testProxy,
                                                   final RTestUnitTestTreeRenderer renderer) {
    final TestStateInfo.Magnitude magnitude = testProxy.getMagnitudeInfo();
    if (magnitude == TestStateInfo.Magnitude.RUNNING_INDEX) {
      renderer.setIcon(getIcon(testProxy, renderer.getConsoleProperties()));
      renderer.append(RBundle.message("ruby.test.runner.ui.tests.tree.presentation.labels.instantiating.tests"),
                      SimpleTextAttributes.REGULAR_ATTRIBUTES);
    } else if (magnitude == TestStateInfo.Magnitude.NOT_RUN_INDEX) {
      renderer.setIcon(PoolOfTestIcons.NOT_RAN);
      renderer.append(RBundle.message("ruby.test.runner.ui.tests.tree.presentation.labels.not.test.results"),
                      SimpleTextAttributes.ERROR_ATTRIBUTES);
    } else if (magnitude == TestStateInfo.Magnitude.TERMINATED_INDEX) {
      renderer.setIcon(PoolOfTestIcons.TERMINATED_ICON);
      renderer.append(RBundle.message("ruby.test.runner.ui.tests.tree.presentation.labels.was.terminated"),
                      SimpleTextAttributes.REGULAR_ATTRIBUTES);
    } else {
      renderer.setIcon(PoolOfTestIcons.NOT_RAN);
      renderer.append(RBundle.message(
          "ruby.test.runner.ui.tests.tree.presentation.labels.no.tests.were.found"),
                      SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }

  public static void formatTestProxy(final RTestUnitTestProxy testProxy,
                                     final RTestUnitTestTreeRenderer renderer) {
    renderer.setIcon(getIcon(testProxy, renderer.getConsoleProperties()));
    renderer.append(testProxy.getPresentableName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  @NotNull
  public static String getPresentableName(final RTestUnitTestProxy testProxy) {
    final RTestUnitTestProxy parent = testProxy.getParent();
    final String name = testProxy.getName();

    if (parent != null) {
      final String parentName = parent.getName();
      if (name.startsWith(parentName)) {
        final String presentationCandidate = name.substring(parentName.length());
        if (presentationCandidate.startsWith(".")) {
          return presentationCandidate.substring(1).trim();
        }
        return presentationCandidate.trim();
      }
    }

    return name.trim();

  }

  private static Icon getIcon(final RTestUnitTestProxy testProxy,
                              final TestConsoleProperties consoleProperties) {
    final TestStateInfo.Magnitude magnitude = testProxy.getMagnitudeInfo();
    switch (magnitude) {
      case ERROR_INDEX:
        return PoolOfTestIcons.ERROR_ICON;
      case FAILED_INDEX:
        return PoolOfTestIcons.FAILED_ICON;
      case IGNORED_INDEX:
        return PoolOfTestIcons.IGNORED_ICON;
      case NOT_RUN_INDEX:
        return PoolOfTestIcons.NOT_RAN;
      case COMPLETE_INDEX:
      case PASSED_INDEX:
        return PoolOfTestIcons.PASSED_ICON;
      case RUNNING_INDEX:
        return !consoleProperties.isPaused()
               ? TestsProgressAnimator.getCurrentFrame()
               : TestsProgressAnimator.PAUSED_ICON;
      case SKIPPED_INDEX:
        return PoolOfTestIcons.SKIPPED_ICON;
      case TERMINATED_INDEX:
        return PoolOfTestIcons.TERMINATED_ICON;
    }
    return null;
  }

  @Nullable
  public static String getTestStatusPresentation(final RTestUnitTestProxy proxy) {
    return proxy.getMagnitudeInfo().getTitle();
  }

  public static void appendSuiteStatusColorPresentation(final RTestUnitTestProxy proxy,
                                                        final ColoredTableCellRenderer renderer) {
    int passedCount = 0;
    int errorsCount = 0;
    int failedCount = 0;
    int ignoredCount = 0;

    if (proxy.isLeaf()) {
      // If suite is empty, show <no tests> failure label and exit from method
      renderer.append(RESULTS_NO_TESTS, DEFFECT_ATTRIBUTES);
      return;
    }

    final List<RTestUnitTestProxy> allTestCases = proxy.getAllTests();
    for (RTestUnitTestProxy testOrSuite : allTestCases) {
      // we should ignore test suites
      if (testOrSuite.isSuite()) {
        continue;
      }
      // if test check it state
      switch (testOrSuite.getMagnitudeInfo()) {
        case COMPLETE_INDEX:
        case PASSED_INDEX:
          passedCount++;
          break;
        case ERROR_INDEX:
          errorsCount++;
          break;
        case FAILED_INDEX:
          failedCount++;
          break;
        case IGNORED_INDEX:
        case SKIPPED_INDEX:
          ignoredCount++;
          break;
        case NOT_RUN_INDEX:
        case TERMINATED_INDEX:
        case RUNNING_INDEX:
          //Do nothing
          break;
      }
    }

    final String separator = TextUtil.SPACE_STRING;

    if (failedCount > 0) {
      renderer.append(RBundle.message("ruby.test.runner.ui.tabs.statistics.columns.results.count.msg.failed",
                                      failedCount) + separator,
                      DEFFECT_ATTRIBUTES);
    }

    if (errorsCount > 0) {
      renderer.append(RBundle.message("ruby.test.runner.ui.tabs.statistics.columns.results.count.msg.errors",
                                      errorsCount) + separator,
                      DEFFECT_ATTRIBUTES);
    }

    if (ignoredCount > 0) {
      renderer.append(RBundle.message("ruby.test.runner.ui.tabs.statistics.columns.results.count.msg.ignored",
                                      ignoredCount) + separator,
                      SimpleTextAttributes.EXCLUDED_ATTRIBUTES);
    }

    if (passedCount > 0) {
      renderer.append(RBundle.message("ruby.test.runner.ui.tabs.statistics.columns.results.count.msg.passed",
                                      passedCount),
                      PASSED_ATTRIBUTES);
    }
  }

  /**
   * @param proxy Test or Suite
   * @return Duration presentation for given proxy
   */
  @Nullable
  public static String getDurationPresentation(final RTestUnitTestProxy proxy) {
    switch (proxy.getMagnitudeInfo()) {
      case COMPLETE_INDEX:
      case PASSED_INDEX:
      case FAILED_INDEX:
      case ERROR_INDEX:
        return getDurationTimePresentation(proxy);

      case IGNORED_INDEX:
      case SKIPPED_INDEX:
      case NOT_RUN_INDEX:
        return DURATION_NOT_RUN;

      case RUNNING_INDEX:
        return getDurationWithPrefixPresentation(proxy, DURATION_RUNNING_PREFIX);

      case TERMINATED_INDEX:
        return getDurationWithPrefixPresentation(proxy, DURATION_TERMINATED_PREFIX);

      default:
        return DURATION_UNKNOWN;
    }
  }

  private static String getDurationWithPrefixPresentation(final RTestUnitTestProxy proxy,
                                                          final String prefix) {
    // If duration is known
    if (proxy.getDuration() != null) {
      return prefix + COLON + getDurationTimePresentation(proxy);
    }

    return '<' + prefix + '>';
  }

  private static String getDurationTimePresentation(final RTestUnitTestProxy proxy) {
    final Integer duration = proxy.getDuration();

    if (duration == null) {
      // if suite without children
      return proxy.isSuite() && proxy.isLeaf()
             ? DURATION_NO_TESTS
             : DURATION_UNKNOWN;
    } else {
      return String.valueOf(convertToSeconds(duration)) + SECONDS_SUFFIX;
    }
  }

  /**
   * @param duration In milliseconds
   * @return Value in seconds
   */
  private static float convertToSeconds(@NotNull final Integer duration) {
    return duration.floatValue() / 1000;
  }

  public static void appendTestStatusColorPresentation(final RTestUnitTestProxy proxy,
                                                       final ColoredTableCellRenderer renderer) {
    final String title = getTestStatusPresentation(proxy);

    final TestStateInfo.Magnitude info = proxy.getMagnitudeInfo();
    switch (info) {
      case COMPLETE_INDEX:
      case PASSED_INDEX:
        renderer.append(title, PASSED_ATTRIBUTES);
        break;
      case RUNNING_INDEX:
        renderer.append(title, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        break;
      case NOT_RUN_INDEX:
        renderer.append(title, SimpleTextAttributes.GRAYED_ATTRIBUTES);
        break;
      case IGNORED_INDEX:
      case SKIPPED_INDEX:
        renderer.append(title, SimpleTextAttributes.EXCLUDED_ATTRIBUTES);
        break;
      case ERROR_INDEX:
      case FAILED_INDEX:
        renderer.append(title, DEFFECT_ATTRIBUTES);
        break;
      case TERMINATED_INDEX:
        renderer.append(title, TERMINATED_ATTRIBUTES);
        break;
    }
  }
}
