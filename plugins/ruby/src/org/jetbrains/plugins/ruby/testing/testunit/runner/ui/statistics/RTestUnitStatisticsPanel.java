package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.ui.table.TableView;
import com.intellij.ui.TableUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.support.UIUtil;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitEventsAdapter;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitEventsListener;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.RTestUnitTestProxySelectionChangedListener;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.SMTestRunnerResultsForm;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class RTestUnitStatisticsPanel extends JPanel {
  public static final Icon STATISTICS_TAB_ICON = TestsUIUtil.loadIcon("testStatistics");

  private TableView<RTestUnitTestProxy> myStatisticsTableView;
  private JPanel myContentPane;

  private RTestUnitStatisticsTableModel myTableModel;
  private final List<RTestUnitTestProxySelectionChangedListener> myChangeSelectionListeners = new ArrayList<RTestUnitTestProxySelectionChangedListener>();

  public RTestUnitStatisticsPanel() {
    myTableModel = new RTestUnitStatisticsTableModel();
    myStatisticsTableView.setModel(myTableModel);
    myStatisticsTableView.addMouseListener(new MouseAdapter(){
      @Override
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 2) {
          final Collection<RTestUnitTestProxy> proxies = myStatisticsTableView.getSelection();
          if (proxies.isEmpty()) {
            return;
          }

          fireOnSelectionChanged(proxies.iterator().next());
        }
      }
    });

    // Fire selection changed and move focus on SHIFT+ENTER
    final KeyStroke shiftEnterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK);
    UIUtil.registerAsAction(shiftEnterKey, "change-selection-on-test-proxy",
                            createChangeSelectionAction(),
                            myStatisticsTableView);

    // Expand selected or go to parent on ENTER
    final KeyStroke enterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
    UIUtil.registerAsAction(enterKey, "go-to-selected-suite-or-parent",
                            createGotoSuiteOrParentAction(),
                            myStatisticsTableView);
  }

  public JPanel getContentPane() {
    return myContentPane;
  }

  /**
   * Show and selects suite in table by event
   * @return
   */
  public SMTestRunnerResultsForm.FormSelectionListener createSelectionListener() {
    final SMTestRunnerResultsForm.FormSelectionListener modelSelectionListener =
        myTableModel.createSelectionListener();

    return new SMTestRunnerResultsForm.FormSelectionListener() {
      public void onSelectedRequest(@Nullable final RTestUnitTestProxy selectedTestProxy) {
        // Send event to model
        modelSelectionListener.onSelectedRequest(selectedTestProxy);

        // Now we want to select proxy in table (if it is possible)
        if (selectedTestProxy != null) {
          findAndSelectInTable(selectedTestProxy);
        }
      }
    };
  }

  public RTestUnitEventsListener createTestEventsListener() {
    return new RTestUnitEventsAdapter() {
      @Override
      public void onSuiteStarted(@NotNull final RTestUnitTestProxy suite) {
        if (myTableModel.shouldUpdateModelBySuite(suite)) {
          updateAndRestoreSelection();
        }
      }

      @Override
      public void onSuiteFinished(@NotNull final RTestUnitTestProxy suite) {
        if (myTableModel.shouldUpdateModelBySuite(suite)) {
          updateAndRestoreSelection();
        }
      }

      @Override
      public void onTestStarted(@NotNull final RTestUnitTestProxy test) {
        if (myTableModel.shouldUpdateModelByTest(test)) {
          updateAndRestoreSelection();
        }
      }

      @Override
      public void onTestFinished(@NotNull final RTestUnitTestProxy test) {
        if (myTableModel.shouldUpdateModelByTest(test)) {
          updateAndRestoreSelection();
        }
      }

      private void updateAndRestoreSelection() {
        UIUtil.addToInvokeLater(new Runnable() {
          public void run() {
            // statisticsTableView can be null in JUnit tests
            final RTestUnitTestProxy oldSelection = myStatisticsTableView.getSelectedObject();

            // update module
            myTableModel.updateModel();

            // restore selection if it is possible
            if (oldSelection != null) {
              final int newRow = myTableModel.getIndexOf(oldSelection);
              if (newRow > -1) {
                myStatisticsTableView.setRowSelectionInterval(newRow, newRow);
              }
            }
          }
        });
      }
    };
  }

  public void addChangeSelectionListener(final RTestUnitTestProxySelectionChangedListener listener) {
    myChangeSelectionListeners.add(listener);
  }

  protected Runnable createChangeSelectionAction() {
    // Change selection
    return new Runnable() {
      public void run() {
        final Collection<RTestUnitTestProxy> proxies = myStatisticsTableView.getSelection();
        if (proxies.isEmpty()) {
          return;
        }
        final RTestUnitTestProxy proxy = proxies.iterator().next();
        myStatisticsTableView.clearSelection();
        fireOnSelectionChanged(proxy);
      }
    };
  }

  protected Runnable createGotoSuiteOrParentAction() {
    final SMTestRunnerResultsForm.FormSelectionListener selectionListener = createSelectionListener();

    // Expand selected or go to parent
    return new Runnable() {
      public void run() {
        final RTestUnitTestProxy selectedProxy = getSelectedItem();
        if (selectedProxy == null) {
          return;
        }

        final int i = myStatisticsTableView.getSelectedRow();
        assert i >= 0; //because something is selected

        // If first line is selected we should go to parent suite
        if (ColumnTest.TestsCellRenderer.isFirstLine(i)) {
          final RTestUnitTestProxy parentSuite = selectedProxy.getParent();
          if (parentSuite != null) {
            // go to parent and current suit in it
            showInTableAndSelectRow(parentSuite, selectionListener, selectedProxy);
          }
        } else {
          // if selected element is suite - we should expand it
          if (selectedProxy.isSuite()) {
            // expand and select first (Total) row
            showInTableAndSelectRow(selectedProxy, selectionListener, selectedProxy);
          }
        }
      }
    };
  }

  /**
   * Selects row in table
   * @param rowIndex Row's index
   */
  protected void selectRow(final int rowIndex) {
    UIUtil.addToInvokeLater(new Runnable() {
      public void run() {
        // updates model
        myStatisticsTableView.setRowSelectionInterval(rowIndex, rowIndex);

        // Scroll to visible
        TableUtil.scrollSelectionToVisible(myStatisticsTableView);
      }
    });
  }

  /**
   * Selects row in table
   * @param rowIndex Row's index
   */
  protected void selectRowOf(final RTestUnitTestProxy proxy) {
    UIUtil.addToInvokeLater(new Runnable() {
      public void run() {
        final int rowIndex = myTableModel.getIndexOf(proxy);
        myStatisticsTableView.setRowSelectionInterval(rowIndex, rowIndex >= 0 ? rowIndex : 0);
        // Scroll to visible
        TableUtil.scrollSelectionToVisible(myStatisticsTableView);
      }
    });
  }

  @Nullable
  protected RTestUnitTestProxy getSelectedItem() {
    return myStatisticsTableView.getSelectedObject();
  }

  protected List<RTestUnitTestProxy> getTableItems() {
    return myTableModel.getItems();
  }

  private void findAndSelectInTable(final RTestUnitTestProxy proxy) {
    UIUtil.addToInvokeLater(new Runnable() {
      public void run() {
        final int rowIndex = myTableModel.getIndexOf(proxy);
        if (rowIndex >= 0) {
          myStatisticsTableView.setRowSelectionInterval(rowIndex, rowIndex);
        }
      }
    });
  }

  private void fireOnSelectionChanged(final RTestUnitTestProxy selectedTestProxy) {
    for (RTestUnitTestProxySelectionChangedListener listener : myChangeSelectionListeners) {
      listener.onChangeSelection(selectedTestProxy, true);
    }
  }

  private void createUIComponents() {
    myStatisticsTableView = new TableView<RTestUnitTestProxy>();
  }

  private void showInTableAndSelectRow(final RTestUnitTestProxy suite,
                                            final SMTestRunnerResultsForm.FormSelectionListener selectionListener,
                                            final RTestUnitTestProxy suiteProxy) {
    selectionListener.onSelectedRequest(suite);
    selectRowOf(suiteProxy);
  }

  /**
   * On event change selection and probably requests focus. Is used when we want
   * navigate from other component to this
   * @return Listener
   */
  public RTestUnitTestProxySelectionChangedListener createOnChangeSelectionListener() {
    final SMTestRunnerResultsForm.FormSelectionListener selectionListener = createSelectionListener();

    return new RTestUnitTestProxySelectionChangedListener() {
      public void onChangeSelection(@Nullable final RTestUnitTestProxy selectedTestProxy, final boolean requestFocus) {
        if (requestFocus) {
          selectionListener.onSelectedRequest(selectedTestProxy);
          UIUtil.addToInvokeLater(new Runnable() {
            public void run() {
              myStatisticsTableView.requestFocusInWindow();
            }
          });
        }
      }
    };
  }
}
