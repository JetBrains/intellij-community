package org.jetbrains.plugins.ruby.testing.sm.runner.ui.statistics;

import com.intellij.ui.ColoredTableCellRenderer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.testing.sm.runner.SMTestProxy;
import org.jetbrains.plugins.ruby.testing.sm.runner.ProxyFilters;
import org.jetbrains.plugins.ruby.testing.sm.runner.ui.TestsPresentationUtil;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.util.Comparator;

/**
 * @author Roman Chernyatchik
*/
public class ColumnResults extends BaseColumn implements Comparator<SMTestProxy> {
  @NonNls private static final String UNDERFINED = "<underfined>";

  public ColumnResults() {
    super(RBundle.message("sm.test.runner.ui.tabs.statistics.columns.results.title"));
  }

  @Override
  public Comparator<SMTestProxy> getComparator() {
    return this;
  }

  public int compare(final SMTestProxy proxy1, final SMTestProxy proxy2) {
    // Rule0. Test < Suite
    // Rule1. For tests: NotRun < Ignored, etc < Passed < Failure < Error < Progress < Terminated
    // Rule2. For suites: Checks count of passed, failures and errors tests: passed < failures < errors

    if (proxy1.isSuite()) {
      if (proxy2.isSuite()) {
        //proxy1 - suite
        //proxy2 - suite

        return compareSuites(proxy1,  proxy2);
      } else {
        //proxy1 - suite
        //proxy2 - test
        return +1;
      }
    } else {
      if (proxy2.isSuite()) {
        //proxy1 - test
        //proxy2 - suite
        return -1;
      } else {
        //proxy1 - test
        //proxy2 - test
        return compareTests(proxy1,  proxy2);
      }
    }
  }

  private int compareTests(final SMTestProxy test1, final SMTestProxy test2) {
    // Rule1. For tests: NotRun < Ignored, etc < Passed < Failure < Error < Progress < Terminated

    final int weitht1 = test1.getMagnitudeInfo().getSortWeitht();
    final int weitht2 = test2.getMagnitudeInfo().getSortWeitht();

    return compareInt(weitht1, weitht2); 
  }

  private int compareSuites(final SMTestProxy suite1,
                            final SMTestProxy suite2) {
    // Compare errors
    final int errors1 = suite1.getChildren(ProxyFilters.FILTER_ERRORS).size();
    final int errors2 = suite2.getChildren(ProxyFilters.FILTER_ERRORS).size();
    final int errorsComparison = compareInt(errors1, errors2);
    // If not equal return it
    if (errorsComparison != 0) {
      return errorsComparison;
    }

    // Compare failures
    final int failures1 = suite1.getChildren(ProxyFilters.FILTER_FAILURES).size();
    final int failures2 = suite2.getChildren(ProxyFilters.FILTER_FAILURES).size();
    final int failuresComparison = compareInt(failures1, failures2);
    // If not equal return it
    if (failuresComparison != 0) {
      return failuresComparison;
    }

    // otherwise check passed count
    final int passed1 = suite1.getChildren(ProxyFilters.FILTER_PASSED).size();
    final int passed2 = suite2.getChildren(ProxyFilters.FILTER_PASSED).size();

    return compareInt(passed1, passed2);
  }

  private int compareInt(final int first, final int second) {
    if (first < second) {
      return -1;
    } else if (first > second) {
      return +1;
    } else {
      return 0;
    }
  }

  public String valueOf(final SMTestProxy testProxy) {
    return UNDERFINED;
  }

  @Override
  public TableCellRenderer getRenderer(final SMTestProxy proxy) {
    return new ResultsCellRenderer(proxy);
  }

  public static class ResultsCellRenderer extends ColoredTableCellRenderer implements ColoredRenderer {
    private final SMTestProxy myProxy;

    public ResultsCellRenderer(final SMTestProxy proxy) {
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
