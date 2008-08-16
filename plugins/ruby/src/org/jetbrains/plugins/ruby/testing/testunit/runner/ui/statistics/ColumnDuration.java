package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.TestsPresentationUtil;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;

/**
 * @author Roman Chernyatchik
*/
public class ColumnDuration extends BaseColumn {
  public ColumnDuration() {
    super(RBundle.message("ruby.test.runner.ui.tabs.statistics.columns.duration.title"));
  }

  public String valueOf(final RTestUnitTestProxy testProxy) {    
    return TestsPresentationUtil.getDurationPresentation(testProxy);
  }

  //@Nullable
  //public Comparator<RTestUnitTestProxy> getComparator(){
  //  return new Comparator<RTestUnitTestProxy>() {
  //    public int compare(final RTestUnitTestProxy o1, final RTestUnitTestProxy o2) {
  //      //Invariant: comparator should left Total row as uppermost element!
  //
  //    }
  //  };
  //}


  @Override
  public TableCellRenderer getRenderer(final RTestUnitTestProxy proxy) {
    return new DurationCellRenderer(proxy);
  }

  public static class DurationCellRenderer extends ColoredTableCellRenderer implements ColoredRenderer {
    private final RTestUnitTestProxy myProxy;

    public DurationCellRenderer(final RTestUnitTestProxy proxy) {
      myProxy = proxy;
    }

    public void customizeCellRenderer(final JTable table,
                                         final Object value,
                                         final boolean selected,
                                         final boolean hasFocus,
                                         final int row,
                                         final int column) {
      assert value != null;

      final String title = value.toString();

      final SimpleTextAttributes attributes;
      if (myProxy.isSuite() && ColumnTest.TestsCellRenderer.isFirstLine(row)) {
        //Black bold for parent suite of items in statistics
        attributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
      } else {
        //Black, regular for other suites and tests
        attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
      }
      append(title, attributes);
    }
  }
}
