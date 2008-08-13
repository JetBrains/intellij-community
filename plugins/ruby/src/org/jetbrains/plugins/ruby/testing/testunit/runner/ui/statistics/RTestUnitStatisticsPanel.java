package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.ui.table.TableView;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitEventsListener;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.TestProxyTreeSelectionListener;

import javax.swing.*;

/**
 * @author Roman Chernyatchik
 */
public class RTestUnitStatisticsPanel extends JPanel {
  public static final Icon STATISTICS_TAB_ICON = TestsUIUtil.loadIcon("testStatistics");

  private TableView<RTestUnitTestProxy> myStatisticsTableView;
  private JPanel myContentPane;

  private RTestUnitStatisticsTableModel myTableModel;

  public RTestUnitStatisticsPanel() {
    myTableModel = new RTestUnitStatisticsTableModel();
    myStatisticsTableView.setModel(myTableModel);
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

  private void createUIComponents() {
    myStatisticsTableView = new TableView<RTestUnitTestProxy>();
  }
}
