package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.ui.table.TableView;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitEventsListener;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.RTestUnitTestProxySelectionListener;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.TestProxyTreeSelectionListener;
import org.jetbrains.plugins.ruby.support.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.*;
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
  private final List<RTestUnitTestProxySelectionListener> mySelectionListeners = new ArrayList<RTestUnitTestProxySelectionListener>();

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
    registerAsAction(shiftEnterKey, "change-selection-on-test-proxy", createChangeSelectionAction());

    // Expand selected or go to parent on ENTER
    final KeyStroke enterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
    registerAsAction(enterKey, "go-to-selected-suite-or-parent", createGotoSuiteOrParentAction());
  }

  public JPanel getContentPane() {
    return myContentPane;
  }

  public TestProxyTreeSelectionListener createSelectionListener() {
    return myTableModel.createSelectionListener();
  }

  public RTestUnitEventsListener createTestEventsListener() {
    return myTableModel.createTestEventsListener();
  }

  public void addSelectionListener(final RTestUnitTestProxySelectionListener listener) {
    mySelectionListeners.add(listener);
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
    final TestProxyTreeSelectionListener selectionListener = createSelectionListener();

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
    for (RTestUnitTestProxySelectionListener listener : mySelectionListeners) {
      listener.onSelected(selectedTestProxy, true);
    }
  }

  private void createUIComponents() {
    myStatisticsTableView = new TableView<RTestUnitTestProxy>();
  }

  private void registerAsAction(final KeyStroke keyStroke,
                                final String actionKey,
                                final Runnable action) {
    final InputMap inputMap = myStatisticsTableView.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    inputMap.put(keyStroke, actionKey);
    myStatisticsTableView.getActionMap().put(inputMap.get(keyStroke), new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
        action.run();
      }
    });
  }

  private void showInTableAndSelectFirstRow(final RTestUnitTestProxy suite,
                                              final TestProxyTreeSelectionListener selectionListener) {
    selectionListener.onSelected(suite);
    selectRow(0);
  }
}
