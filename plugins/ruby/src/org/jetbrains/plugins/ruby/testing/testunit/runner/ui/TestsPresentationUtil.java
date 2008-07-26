package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.execution.testframework.PoolOfTestIcons;
import com.intellij.execution.testframework.ui.TestsProgressAnimator;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitConsoleProperties;

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
    renderer.append(testProxy.isInProgress()
                      ? RBundle.message("ruby.test.runner.ui.tests.tree.presentation.labels.running.tests")
                      : RBundle.message("ruby.test.runner.ui.tests.tree.presentation.labels.test.results"),
                    SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public static void formatRootNodeWithoutChildren(final RTestUnitTestProxy testProxy,
                                                   final RTestUnitTestTreeRenderer renderer) {
    if (testProxy.isInProgress()) {
      renderer.setIcon(getIcon(testProxy, renderer.getConsoleProperties()));
      renderer.append(RBundle.message("ruby.test.runner.ui.tests.tree.presentation.labels.instantiating.tests"),
                      SimpleTextAttributes.REGULAR_ATTRIBUTES);
    } else if (!testProxy.wasLaunched()) {
      renderer.setIcon(PoolOfTestIcons.NOT_RAN);
      renderer.append(RBundle.message("ruby.test.runner.ui.tests.tree.presentation.labels.not.test.results"),
                      SimpleTextAttributes.ERROR_ATTRIBUTES);
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
    renderer.append(testProxy.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  private static Icon getIcon(final RTestUnitTestProxy testProxy,
                              final RTestUnitConsoleProperties consoleProperties) {
    if (testProxy.isInProgress()) {
      return !consoleProperties.isPaused()
             ? TestsProgressAnimator.getCurrentFrame()
             : TestsProgressAnimator.PAUSED_ICON;
    }

    if (!testProxy.wasLaunched()) {
      return PoolOfTestIcons.NOT_RAN;
    }

    if (testProxy.isDefect()) {
      //TODO[romeo] ignored tests support
      return PoolOfTestIcons.FAILED_ICON;
    }

    return PoolOfTestIcons.PASSED_ICON;
  }
}
