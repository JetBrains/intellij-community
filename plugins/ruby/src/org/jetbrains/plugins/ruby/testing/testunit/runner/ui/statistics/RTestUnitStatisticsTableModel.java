package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.NullableFunction;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.support.UIUtil;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.SMTestRunnerResultsForm;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class RTestUnitStatisticsTableModel extends ListTableModel<RTestUnitTestProxy> {
  private static final Logger LOG = Logger.getInstance(RTestUnitStatisticsTableModel.class.getName());

  private RTestUnitTestProxy myCurrentSuite;

  private NullableFunction<List<RTestUnitTestProxy>, Object> oldReverseModelItemsFun =
      new NullableFunction<List<RTestUnitTestProxy>, Object>() {
        @Nullable
        public Object fun(final List<RTestUnitTestProxy> proxies) {
          RTestUnitStatisticsTableModel.super.reverseModelItems(proxies);

          return null;
        }
      };

  public RTestUnitStatisticsTableModel() {
    super(new ColumnTest(), new ColumnDuration(), new ColumnResults());
  }

  public SMTestRunnerResultsForm.FormSelectionListener createSelectionListener() {
    return new SMTestRunnerResultsForm.FormSelectionListener() {
      public void onSelectedRequest(@Nullable final RTestUnitTestProxy proxy) {

        final RTestUnitTestProxy newCurrentSuite = getCurrentSuiteFor(proxy);
        // If new suite differs from old suite we should reload table
        if (myCurrentSuite != newCurrentSuite) {
          myCurrentSuite = newCurrentSuite;
          UIUtil.addToInvokeLater(new Runnable() {
            public void run() {
              updateModel();
            }
          });
        }
       }
    };
  }

   @Nullable
   public RTestUnitTestProxy getTestAt(final int rowIndex) {
    if (rowIndex < 0 || rowIndex > getItems().size()) {
      return null;
    }
    return getItems().get(rowIndex);
  }


  /**
   * Searches index of given test or suite. If finds nothing will retun -1
   * @param test Test or suite
   * @return Proxy's index or -1
   */
  public int getIndexOf(final RTestUnitTestProxy test) {
    for (int i = 0; i < getItems().size(); i++) {
      final RTestUnitTestProxy child = getItems().get(i);
      if (child == test) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Update module in EDT
   */
  protected void updateModel() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());

    // updates model
    setItems(getItemsForSuite(myCurrentSuite));
  }

  @NotNull
  private List<RTestUnitTestProxy> getItemsForSuite(@Nullable final RTestUnitTestProxy suite) {
    if (suite == null) {
      return Collections.emptyList();
    }

    final List<RTestUnitTestProxy> list = new ArrayList<RTestUnitTestProxy>();
    // suite's total statistics
    list.add(suite);
    // chiled's statistics
    list.addAll(suite.getChildren());

    return list;
  }

  @Override
  protected void reverseModelItems(final List<RTestUnitTestProxy> rTestUnitTestProxies) {
    //Invariant: comparator should left Total(initally at row = 0) row as uppermost element!
    applySortOperation(rTestUnitTestProxies, oldReverseModelItemsFun);
  }

  /**
   * This function allow sort operation to all except first element(e.g. Total row)
   * @param proxies Tests or suites
   * @param sortOperation Closure
   */
  protected static void applySortOperation(final List<RTestUnitTestProxy> proxies,
                                           final NullableFunction<List<RTestUnitTestProxy>, Object> sortOperation) {

    //Invariant: comparator should left Total(initally at row = 0) row as uppermost element!
    final int size = proxies.size();
    if (size > 1) {
      sortOperation.fun(proxies.subList(1, size));
    }
  }

  public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
    // Setting value is prevented!
    LOG.assertTrue(false, "value: " + aValue + " row: " + rowIndex + " column: " + columnIndex);
  }

  @Nullable
  private RTestUnitTestProxy getCurrentSuiteFor(@Nullable final RTestUnitTestProxy proxy) {
    if (proxy == null) {
      return null;
    }

    // If proxy is suite, returns it
    final RTestUnitTestProxy suite;
    if (proxy.isSuite()) {
      suite = proxy;
    }
    else {
      // If proxy is tests returns test's suite
      suite = proxy.getParent();
    }
    return suite;
  }


  protected boolean shouldUpdateModelByTest(final RTestUnitTestProxy test) {
    // if some suite in statistics is selected
    // and test is child of current suite
    return isSomeSuiteSelected() && (test.getParent() == myCurrentSuite);
  }

  protected boolean shouldUpdateModelBySuite(final RTestUnitTestProxy suite) {
    // If some suite in statistics is selected
    // and suite is current suite in statistics tab or child of current suite
    return isSomeSuiteSelected() && (suite == myCurrentSuite || suite.getParent() == myCurrentSuite);
  }

  private boolean isSomeSuiteSelected() {
    return myCurrentSuite != null;
  }
}
