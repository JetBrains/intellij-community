package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.ui.table.TableView;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.support.UIUtil;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitEventsListener;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.RTestUnitResultsForm;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.RTestUnitTestProxySelectionChangedListener;

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
  public RTestUnitResultsForm.FormSelectionListener createSelectionListener() {
    return myTableModel.createSelectionListener(myStatisticsTableView);
  }

  public RTestUnitEventsListener createTestEventsListener() {
    return myTableModel.createTestEventsListener();
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
    final RTestUnitResultsForm.FormSelectionListener selectionListener = createSelectionListener();

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
            showInTableAndSelectFirstRow(parentSuite, selectionListener);
          }
        } else {
          // if selected element is suite - we should expand it
          if (selectedProxy.isSuite()) {
            showInTableAndSelectFirstRow(selectedProxy, selectionListener);
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
      }
    });
  }

  @Nullable
  protected RTestUnitTestProxy getSelectedItem() {
    return myStatisticsTableView.getSelectedObject();
  }

  private void fireOnSelectionChanged(final RTestUnitTestProxy selectedTestProxy) {
    for (RTestUnitTestProxySelectionChangedListener listener : myChangeSelectionListeners) {
      listener.onChangeSelection(selectedTestProxy, true);
    }
  }

  private void createUIComponents() {
    myStatisticsTableView = new TableView<RTestUnitTestProxy>();
  }

  private void showInTableAndSelectFirstRow(final RTestUnitTestProxy suite,
                                            final RTestUnitResultsForm.FormSelectionListener selectionListener) {
    selectionListener.onSelectedRequest(suite);
    selectRow(0);
  }

  /**
   * On event change selection and probably requests focus. Is used when we want
   * navigate from other component to this
   * @return Listener
   */
  public RTestUnitTestProxySelectionChangedListener createOnChangeSelectionListener() {
    final RTestUnitResultsForm.FormSelectionListener selectionListener = createSelectionListener();

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
