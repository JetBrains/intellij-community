package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.AbstractTestProxy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.states.TestStateInfo;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.TestsPresentationUtil;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.util.Comparator;

/**
 * @author Roman Chernyatchik
*/
public class ColumnResults extends BaseColumn implements Comparator<RTestUnitTestProxy> {
  @NonNls private static final String UNDERFINED = "<underfined>";

  private static final Filter FILTER_PASSED = new Filter() {
    public boolean shouldAccept(final AbstractTestProxy test) {
      return ((RTestUnitTestProxy)test).getMagnitudeInfo() == TestStateInfo.Magnitude.PASSED_INDEX;
    }
  };

  private static final Filter FILTER_ERRORS = new Filter() {
    public boolean shouldAccept(final AbstractTestProxy test) {
      return ((RTestUnitTestProxy)test).getMagnitudeInfo() == TestStateInfo.Magnitude.ERROR_INDEX;
    }
  };

  private static final Filter FILTER_FAILURES = new Filter() {
    public boolean shouldAccept(final AbstractTestProxy test) {
      return ((RTestUnitTestProxy)test).getMagnitudeInfo() == TestStateInfo.Magnitude.FAILED_INDEX;
    }
  };

  public ColumnResults() {
    super(RBundle.message("ruby.test.runner.ui.tabs.statistics.columns.results.title"));
  }

  @Override
  public Comparator<RTestUnitTestProxy> getComparator() {
    return this;
  }

  public int compare(final RTestUnitTestProxy proxy1, final RTestUnitTestProxy proxy2) {
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

  private int compareTests(final RTestUnitTestProxy test1, final RTestUnitTestProxy test2) {
    // Rule1. For tests: NotRun < Ignored, etc < Passed < Failure < Error < Progress < Terminated

    final int weitht1 = test1.getMagnitudeInfo().getSortWeitht();
    final int weitht2 = test2.getMagnitudeInfo().getSortWeitht();

    return compareInt(weitht1, weitht2); 
  }

  private int compareSuites(final RTestUnitTestProxy suite1,
                            final RTestUnitTestProxy suite2) {
    // Compare errors
    final int errors1 = suite1.getChildren(FILTER_ERRORS).size();
    final int errors2 = suite2.getChildren(FILTER_ERRORS).size();
    final int errorsComparison = compareInt(errors1, errors2);
    // If not equal return it
    if (errorsComparison != 0) {
      return errorsComparison;
    }

    // Compare failures
    final int failures1 = suite1.getChildren(FILTER_FAILURES).size();
    final int failures2 = suite2.getChildren(FILTER_FAILURES).size();
    final int failuresComparison = compareInt(failures1, failures2);
    // If not equal return it
    if (failuresComparison != 0) {
      return failuresComparison;
    }

    // otherwise check passed count
    final int passed1 = suite1.getChildren(FILTER_PASSED).size();
    final int passed2 = suite2.getChildren(FILTER_PASSED).size();

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
