package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.util.Comparator;

/**
 * @author Roman Chernyatchik
*/
public class ColumnTest extends BaseColumn implements Comparator<RTestUnitTestProxy>{
  public ColumnTest() {
    super(RBundle.message("ruby.test.runner.ui.tabs.statistics.columns.test.title"));
  }

  @NotNull
  public String valueOf(final RTestUnitTestProxy testProxy) {
    return testProxy.getPresentableName();
  }

  @Nullable
  public Comparator<RTestUnitTestProxy> getComparator(){
    return this;
  }

  public int compare(final RTestUnitTestProxy proxy1, final RTestUnitTestProxy proxy2) {
    return proxy1.getName().compareTo(proxy2.getName());
  }

  @Override
  public TableCellRenderer getRenderer(final RTestUnitTestProxy proxy) {
    return new TestsCellRenderer(proxy);
  }

  public static class TestsCellRenderer extends ColoredTableCellRenderer implements ColoredRenderer {
    @NonNls private static final String TOTAL_TITLE = RBundle.message("ruby.test.runner.ui.tabs.statistics.columns.test.total.title");

    private final RTestUnitTestProxy myProxy;

    public TestsCellRenderer(final RTestUnitTestProxy proxy) {
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
      //Black bold for with caption "Total" for parent suite of items in statistics
      if (myProxy.isSuite() && isFirstLine(row)) {
        append(TOTAL_TITLE, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        return;
      }
      //Black, regular for other suites and tests
      append(title, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    public static boolean isFirstLine(final int row) {
      return row == 0;
    }
  }
}
