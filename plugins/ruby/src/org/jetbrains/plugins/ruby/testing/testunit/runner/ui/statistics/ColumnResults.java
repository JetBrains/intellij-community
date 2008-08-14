package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.TestsPresentationUtil;
import org.jetbrains.plugins.ruby.testing.testunit.runner.states.TestStateInfo;
import org.jetbrains.plugins.ruby.RBundle;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.execution.testframework.TestsUIUtil;

import javax.swing.table.TableCellRenderer;
import javax.swing.*;
import java.awt.*;

/**
 * @author Roman Chernyatchik
*/
public class ColumnResults extends ColumnInfo<RTestUnitTestProxy, String> {
  public static final SimpleTextAttributes PASSED_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, TestsUIUtil.PASSED_COLOR);
  public static final SimpleTextAttributes DEFFECT_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.RED);
  public static final SimpleTextAttributes TERMINATED_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.ORANGE);

  //TODO sort

  public ColumnResults() {
    super(RBundle.message("ruby.test.runner.ui.tabs.statistics.columns.results.title"));
  }

  public String valueOf(final RTestUnitTestProxy testProxy) {
    if (testProxy.isSuite()) {
      // for suite returns brief statistics
      return TestsPresentationUtil.getSuiteStatusPresentation(testProxy);
    }
    // for test returns test status string
    return TestsPresentationUtil.getTestStatusPresentation(testProxy);
  }

  @Override
  public TableCellRenderer getRenderer(final RTestUnitTestProxy proxy) {
    return new ResultsCellRenderer(proxy);
  }

  public static class ResultsCellRenderer extends ColoredTableCellRenderer {
    private final RTestUnitTestProxy myProxy;

    public ResultsCellRenderer(final RTestUnitTestProxy proxy) {
      myProxy = proxy;
    }

    protected void customizeCellRenderer(final JTable table,
                                         final Object value,
                                         final boolean selected,
                                         final boolean hasFocus,
                                         final int row,
                                         final int column) {
      final String title = value.toString();

      final TestStateInfo.Magnitude info = myProxy.getMagnitudeInfo();
      switch (info) {
        case COMPLETE_INDEX:
        case PASSED_INDEX:
          append(title, PASSED_ATTRIBUTES);
          break;
        case RUNNING_INDEX:
          append(title, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          break;
        case NOT_RUN_INDEX:
          append(title, SimpleTextAttributes.GRAYED_ATTRIBUTES);
          break;
        case IGNORED_INDEX:
        case SKIPPED_INDEX:
          append(title, SimpleTextAttributes.EXCLUDED_ATTRIBUTES);
          break;
        case ERROR_INDEX:
        case FAILED_INDEX:
          append(title, DEFFECT_ATTRIBUTES);
          break;
        case TERMINATED_INDEX:
          append(title, TERMINATED_ATTRIBUTES);
          break;
      }
    }
  }
}
