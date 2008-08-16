package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.TestsPresentationUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.util.Comparator;

/**
 * @author Roman Chernyatchik
*/
public class ColumnDuration extends BaseColumn implements Comparator<RTestUnitTestProxy> {
  public ColumnDuration() {
    super(RBundle.message("ruby.test.runner.ui.tabs.statistics.columns.duration.title"));
  }

  public String valueOf(final RTestUnitTestProxy testProxy) {    
    return TestsPresentationUtil.getDurationPresentation(testProxy);
  }

  @Nullable
  public Comparator<RTestUnitTestProxy> getComparator(){
    return this;
  }

  public int compare(final RTestUnitTestProxy proxy1, final RTestUnitTestProxy proxy2) {
    final Integer duration1 = proxy1.getDuration();
    final Integer duration2 = proxy2.getDuration();

    if (duration1 == null) {
      return duration2 == null ? 0 : -1;
    }
    if (duration2 == null) {
      return +1;
    }
    return duration1.compareTo(duration2);
  }


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
