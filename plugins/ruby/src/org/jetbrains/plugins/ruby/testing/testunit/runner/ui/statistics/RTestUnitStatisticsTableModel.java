package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.execution.testframework.ui.PrintableTestProxy;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.support.UIUtil;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitEventsAdapter;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitEventsListener;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.TestProxyTreeSelectionListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class RTestUnitStatisticsTableModel extends ListTableModel<RTestUnitTestProxy> {
  private RTestUnitTestProxy myCurrentSuite;

  public RTestUnitStatisticsTableModel() {
    super(new ColumnTest(), new ColumnDuration(), new ColumnResults());
  }

  public TestProxyTreeSelectionListener createSelectionListener() {
    return new TestProxyTreeSelectionListener() {
      public void onSelected(@Nullable final PrintableTestProxy selectedTestProxy) {
        final RTestUnitTestProxy proxy;
        if (selectedTestProxy instanceof RTestUnitTestProxy) {
          proxy = (RTestUnitTestProxy)selectedTestProxy;
        } else {
          proxy = null;
        }

        myCurrentSuite = getCurrentSuiteFor(proxy);

        updateModel();
      }
    };
  }

  private void updateModel() {
    UIUtil.addToInvokeLater(new Runnable() {
      public void run() {
        // updates model
        setItems(getItemsForSuite(myCurrentSuite));
      }
    });
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
  
  public RTestUnitEventsListener createTestEventsListener() {
    return new RTestUnitEventsAdapter() {
      @Override
      public void onSuiteStarted(@NotNull final RTestUnitTestProxy suite) {
        if (shouldUpdateModelBySuite(suite)) {
          updateModel();
        }
      }

      @Override
      public void onSuiteFinished(@NotNull final RTestUnitTestProxy suite) {
        if (shouldUpdateModelBySuite(suite)) {
          updateModel();
        }
      }

      @Override
      public void onTestStarted(@NotNull final RTestUnitTestProxy test) {
        if (shouldUpdateModelByTest(test)) {
          updateModel();
        }
      }

      @Override
      public void onTestFinished(@NotNull final RTestUnitTestProxy test) {
        if (shouldUpdateModelByTest(test)) {
          updateModel();
        }
      }

      private boolean shouldUpdateModelByTest(final RTestUnitTestProxy test) {
        // if some suite in statistics is selected
        // and test is child of current suite
        return isSomeSuiteSelected() && (test.getParent() == myCurrentSuite);
      }

      private boolean shouldUpdateModelBySuite(final RTestUnitTestProxy suite) {
        // If some suite in statistics is selected
        // and suite is current suite in statistics tab or child of current suite
        return isSomeSuiteSelected() && (suite == myCurrentSuite || suite.getParent() == myCurrentSuite);
      }

      private boolean isSomeSuiteSelected() {
        return myCurrentSuite != null;
      }
    };
  }
}
