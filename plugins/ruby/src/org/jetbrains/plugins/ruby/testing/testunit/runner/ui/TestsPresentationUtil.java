package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.execution.testframework.PoolOfTestIcons;
import com.intellij.execution.testframework.ui.TestsProgressAnimator;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitConsoleProperties;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.states.TestStateInfo;

import javax.swing.*;
import java.text.NumberFormat;

/**
 * @author Roman Chernyatchik
 */
public class TestsPresentationUtil {
  @NonNls private static final String DOUBLE_SPACE = "  ";
  @NonNls private static final String WORLD_CREATION_TIME = "0.0";

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
      sb.append(' ').append(RBundle.message("ruby.test.runner.ui.tests.tree.presentation.labels.seconds")).append(')');
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
                              final RTestUnitConsoleProperties consoleProperties) {
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

  @Nullable
  public static String getSuiteStatusPresentation(final RTestUnitTestProxy proxy) {
    //TODO[romeo] improove
    return String.valueOf(proxy.getChildren().size());
  }

  /**
   * @param proxy Test or Suite
   * @return Duration presentation for given proxy
   */
  @Nullable
  public static String getDurationPresentation(final RTestUnitTestProxy proxy) {
    //TODO[romeo] implement
    return "<unknown>";
  }  
}
