package org.jetbrains.plugins.ruby.testing.sm.runner.ui.statistics;

import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.ui.table.TableView;
import com.intellij.ui.TableUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.support.UIUtil;
import org.jetbrains.plugins.ruby.testing.sm.runner.SMTRunnerEventsAdapter;
import org.jetbrains.plugins.ruby.testing.sm.runner.SMTRunnerEventsListener;
import org.jetbrains.plugins.ruby.testing.sm.runner.SMTestProxy;
import org.jetbrains.plugins.ruby.testing.sm.runner.ui.TestProxySelectionChangedListener;
import org.jetbrains.plugins.ruby.testing.sm.runner.ui.SMTestRunnerResultsForm;

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
public class StatisticsPanel extends JPanel {
  public static final Icon STATISTICS_TAB_ICON = TestsUIUtil.loadIcon("testStatistics");

  private TableView<SMTestProxy> myStatisticsTableView;
  private JPanel myContentPane;

  private StatisticsTableModel myTableModel;
  private final List<TestProxySelectionChangedListener> myChangeSelectionListeners = new ArrayList<TestProxySelectionChangedListener>();

  public StatisticsPanel() {
    myTableModel = new StatisticsTableModel();
    myStatisticsTableView.setModel(myTableModel);

    final Runnable gotoSuiteOrParentAction = createGotoSuiteOrParentAction();
    myStatisticsTableView.addMouseListener(new MouseAdapter(){
      @Override
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 2) {
          gotoSuiteOrParentAction.run();
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
                            gotoSuiteOrParentAction,
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
      public void onSelectedRequest(@Nullable final SMTestProxy selectedTestProxy) {
        // Send event to model
        modelSelectionListener.onSelectedRequest(selectedTestProxy);

        // Now we want to select proxy in table (if it is possible)
        if (selectedTestProxy != null) {
          findAndSelectInTable(selectedTestProxy);
        }
      }
    };
  }

  public SMTRunnerEventsListener createTestEventsListener() {
    return new SMTRunnerEventsAdapter() {
      @Override
      public void onSuiteStarted(@NotNull final SMTestProxy suite) {
        if (myTableModel.shouldUpdateModelBySuite(suite)) {
          updateAndRestoreSelection();
        }
      }

      @Override
      public void onSuiteFinished(@NotNull final SMTestProxy suite) {
        if (myTableModel.shouldUpdateModelBySuite(suite)) {
          updateAndRestoreSelection();
        }
      }

      @Override
      public void onTestStarted(@NotNull final SMTestProxy test) {
        if (myTableModel.shouldUpdateModelByTest(test)) {
          updateAndRestoreSelection();
        }
      }

      @Override
      public void onTestFinished(@NotNull final SMTestProxy test) {
        if (myTableModel.shouldUpdateModelByTest(test)) {
          updateAndRestoreSelection();
        }
      }

      private void updateAndRestoreSelection() {
        UIUtil.addToInvokeLater(new Runnable() {
          public void run() {
            // statisticsTableView can be null in JUnit tests
            final SMTestProxy oldSelection = myStatisticsTableView.getSelectedObject();

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

  public void addChangeSelectionListener(final TestProxySelectionChangedListener listener) {
    myChangeSelectionListeners.add(listener);
  }

  protected Runnable createChangeSelectionAction() {
    // Change selection
    return new Runnable() {
      public void run() {
        final Collection<SMTestProxy> proxies = myStatisticsTableView.getSelection();
        if (proxies.isEmpty()) {
          return;
        }
        final SMTestProxy proxy = proxies.iterator().next();
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
        final SMTestProxy selectedProxy = getSelectedItem();
        if (selectedProxy == null) {
          return;
        }

        final int i = myStatisticsTableView.getSelectedRow();
        assert i >= 0; //because something is selected

        // If first line is selected we should go to parent suite
        if (ColumnTest.TestsCellRenderer.isFirstLine(i)) {
          final SMTestProxy parentSuite = selectedProxy.getParent();
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
  protected void selectRowOf(final SMTestProxy proxy) {
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
  protected SMTestProxy getSelectedItem() {
    return myStatisticsTableView.getSelectedObject();
  }

  protected List<SMTestProxy> getTableItems() {
    return myTableModel.getItems();
  }

  private void findAndSelectInTable(final SMTestProxy proxy) {
    UIUtil.addToInvokeLater(new Runnable() {
      public void run() {
        final int rowIndex = myTableModel.getIndexOf(proxy);
        if (rowIndex >= 0) {
          myStatisticsTableView.setRowSelectionInterval(rowIndex, rowIndex);
        }
      }
    });
  }

  private void fireOnSelectionChanged(final SMTestProxy selectedTestProxy) {
    for (TestProxySelectionChangedListener listener : myChangeSelectionListeners) {
      listener.onChangeSelection(selectedTestProxy, true);
    }
  }

  private void createUIComponents() {
    myStatisticsTableView = new TableView<SMTestProxy>();
  }

  private void showInTableAndSelectRow(final SMTestProxy suite,
                                            final SMTestRunnerResultsForm.FormSelectionListener selectionListener,
                                            final SMTestProxy suiteProxy) {
    selectionListener.onSelectedRequest(suite);
    selectRowOf(suiteProxy);
  }

  /**
   * On event change selection and probably requests focus. Is used when we want
   * navigate from other component to this
   * @return Listener
   */
  public TestProxySelectionChangedListener createOnChangeSelectionListener() {
    final SMTestRunnerResultsForm.FormSelectionListener selectionListener = createSelectionListener();

    return new TestProxySelectionChangedListener() {
      public void onChangeSelection(@Nullable final SMTestProxy selectedTestProxy, final boolean requestFocus) {
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
