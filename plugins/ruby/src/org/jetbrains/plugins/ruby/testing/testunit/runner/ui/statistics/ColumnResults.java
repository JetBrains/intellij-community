package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.TestsPresentationUtil;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;

/**
 * @author Roman Chernyatchik
*/
public class ColumnResults extends ColumnInfo<RTestUnitTestProxy, String> {
  @NonNls private static final String UNDERFINED = "<underfined>";

  ////TODO sort
  // @Nullable
  //public Comparator<RTestUnitTestProxy> getComparator(){
  //  return new Comparator<RTestUnitTestProxy>() {
  //    public int compare(final RTestUnitTestProxy o1, final RTestUnitTestProxy o2) {
  //    }
  //  };
  //}

  public ColumnResults() {
    super(RBundle.message("ruby.test.runner.ui.tabs.statistics.columns.results.title"));
  }

  public String valueOf(final RTestUnitTestProxy testProxy) {
    return UNDERFINED;
  }

  @Override
  public TableCellRenderer getRenderer(final RTestUnitTestProxy proxy) {
    return new ResultsCellRenderer(proxy);
  }

  public static class ResultsCellRenderer extends ColoredTableCellRenderer implements ColoredRenderer {
    private final RTestUnitTestProxy myProxy;

    public ResultsCellRenderer(final RTestUnitTestProxy proxy) {
      myProxy = proxy;
    }

    public void customizeCellRenderer(final JTable table,
                                         final Object value,
                                         final boolean selected,
                                         final boolean hasFocus,
                                         final int row,
                                         final int column) {
      if (myProxy.isSuite()) {
        // for suite returns brief statistics
        TestsPresentationUtil.appendSuiteStatusColorPresentation(myProxy, this);
      } else {
        // for test returns test status string
        TestsPresentationUtil.appendTestStatusColorPresentation(myProxy, this);
      }
    }
  }
}
