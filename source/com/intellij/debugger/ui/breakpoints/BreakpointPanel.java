package com.intellij.debugger.ui.breakpoints;

import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * @author Jeka
 */
public class BreakpointPanel {
  private final BreakpointTableModel myTableModel;
  private final BreakpointPropertiesPanel myPropertiesPanel;
  private Breakpoint myCurrentViewableBreakpoint;

  private JPanel myPanel;
  private JPanel myBreakPointsPanel;
  private JPanel myTablePlace;
  private JPanel myPropertiesPanelPlace;
  private Table myTable;
  private JButton myAddBreakpointButton;
  private JButton myRemoveBreakpointButton;
  private JButton myGotoSourceButton;
  private JButton myViewSourceButton;


  public BreakpointPanel(BreakpointTableModel tableModel, BreakpointPropertiesPanel propertiesPanel) {
    myTableModel = tableModel;
    myPropertiesPanel = propertiesPanel;
    myTable = new Table(myTableModel);
    myTable.setColumnSelectionAllowed(false);
    InputMap inputMap = myTable.getInputMap();
    ActionMap actionMap = myTable.getActionMap();
    Object o = inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0));
    if (o == null) {
      o = "enable_disable";
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), o);
    }
    actionMap.put(o, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (myTable.isEditing()) return;
        int[] indices = myTable.getSelectedRows();
        boolean currentlyMarked = true;
        for (int i = 0; i < indices.length; i++) {
          final Boolean isMarked = (Boolean)myTable.getValueAt(indices[i], BreakpointTableModel.ENABLED_STATE);
          currentlyMarked = isMarked != null? isMarked.booleanValue() : false;
          if (!currentlyMarked) {
            break;
          }
        }
        final Boolean valueToSet = currentlyMarked ? Boolean.FALSE : Boolean.TRUE;
        for (int i = 0; i < indices.length; i++) {
          myTable.setValueAt(valueToSet, indices[i], BreakpointTableModel.ENABLED_STATE);
        }
      }
    });

    myTable.setShowGrid(false);
    myTable.setIntercellSpacing(new Dimension(0, 0));
    myTable.setTableHeader(null);
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    myTable.setColumnSelectionAllowed(false);
    JScrollPane pane = ScrollPaneFactory.createScrollPane(myTable);
    int width = new JCheckBox().getPreferredSize().width;
    TableColumnModel columnModel = myTable.getColumnModel();

    TableColumn column = columnModel.getColumn(BreakpointTableModel.ENABLED_STATE);
    column.setPreferredWidth(width);
    column.setMaxWidth(width);
    columnModel.getColumn(BreakpointTableModel.NAME).setCellRenderer(new BreakpointNameCellRenderer());

    myTablePlace.setLayout(new BorderLayout());
    myTablePlace.add(pane, BorderLayout.CENTER);

    addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        refreshUI();
      }
    });
    myTableModel.addTableModelListener(new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        if (e.getType() == TableModelEvent.UPDATE) {
          refreshUI();
        }
      }
    });
    myTable.requestFocus();

    myStubPanel = new JPanel();
    myStubPanel.setMinimumSize(myPropertiesPanel.getPanel().getMinimumSize());

    myPropertiesPanelPlace.setLayout(new BorderLayout());
    myBreakPointsPanel.setBorder(IdeBorderFactory.createEmptyBorder(6, 6, 0, 6));
    refreshUI();
  }

  public void saveChanges() {
    if (myCurrentViewableBreakpoint != null) {
      myPropertiesPanel.saveTo(myCurrentViewableBreakpoint, new Runnable() {
        public void run() {
          myTable.repaint();
        }
      });
    }
  }

  public void updateButtons() {
    myRemoveBreakpointButton.setEnabled(getSelectedBreakpoints().length > 0);
    myGotoSourceButton.setEnabled(myCurrentViewableBreakpoint != null);
    myViewSourceButton.setEnabled(myCurrentViewableBreakpoint != null);
    if (!myGotoSourceButton.isEnabled() && myGotoSourceButton.hasFocus()) myGotoSourceButton.transferFocus();
    if (!myViewSourceButton.isEnabled() && myViewSourceButton.hasFocus()) myViewSourceButton.transferFocus();
    if (!myRemoveBreakpointButton.isEnabled() && myRemoveBreakpointButton.hasFocus()) myRemoveBreakpointButton.transferFocus();
  }

  public JTable getTable() {
    return myTable;
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public JButton getAddBreakpointButton() {
    return myAddBreakpointButton;
  }

  public JButton getGotoSourceButton() {
    return myGotoSourceButton;
  }

  public JButton getViewSourceButton() {
    return myViewSourceButton;
  }

  public JButton getRemoveBreakpointButton() {
    return myRemoveBreakpointButton;
  }

  public void selectBreakpoint(Breakpoint breakpoint) {
    int index = myTableModel.getBreakpointIndex(breakpoint);
    myTable.clearSelection();
    myTable.getSelectionModel().addSelectionInterval(index, index);
    myPropertiesPanel.getControl(BreakpointPropertiesPanel.CONTROL_LOG_MESSAGE);
  }

  public void setBreakpoints(Breakpoint[] breakpoints) {
    myTableModel.setBreakpoints(breakpoints);
    if (breakpoints != null && breakpoints.length > 0) {
      myTable.getSelectionModel().addSelectionInterval(0, 0);
    }
  }

  public Breakpoint[] getSelectedBreakpoints() {
    if (myTable.getRowCount() == 0) {
      return new Breakpoint[0];
    }

    int[] rows = myTable.getSelectedRows();
    if (rows.length == 0) {
      return new Breakpoint[0];
    }
    Breakpoint[] rv = new Breakpoint[rows.length];
    for (int idx = 0; idx < rows.length; idx++) {
      rv[idx] = myTableModel.getBreakpoint(rows[idx]);
    }
    return rv;
  }

  public void removeSelectedBreakpoints() {
    TableUtil.removeSelectedItems(myTable);
    myCurrentViewableBreakpoint = null;
    refreshUI();
  }

  public void insertBreakpointAt(Breakpoint breakpoint, int index) {
    myTableModel.insertBreakpointAt(breakpoint, index);
    ListSelectionModel model = myTable.getSelectionModel();
    model.clearSelection();
    model.addSelectionInterval(index, index);
  }

  public void addBreakpoint(Breakpoint breakpoint) {
    myTableModel.addBreakpoint(breakpoint);
    int index = myTable.getRowCount() - 1;
    ListSelectionModel model = myTable.getSelectionModel();
    model.clearSelection();
    model.addSelectionInterval(index, index);
  }

  public void addListSelectionListener(ListSelectionListener listener) {
    myTable.getSelectionModel().addListSelectionListener(listener);
  }

  public void removeListSelectionListener(ListSelectionListener listener) {
    myTable.getSelectionModel().removeListSelectionListener(listener);
  }

  private JPanel myStubPanel;

  private void refreshUI() {
    if (myCurrentViewableBreakpoint != null) {
      myPropertiesPanel.saveTo(myCurrentViewableBreakpoint, new Runnable() {
        public void run() {
          myTable.repaint();
        }
      });
    }
    Breakpoint[] breakpoints = getSelectedBreakpoints();
    Breakpoint oldBreakpoint = myCurrentViewableBreakpoint;
    myCurrentViewableBreakpoint = (breakpoints != null && breakpoints.length == 1) ? breakpoints[0] : null;
    if (myCurrentViewableBreakpoint != null) {
      if (oldBreakpoint == null) {
        myPropertiesPanelPlace.remove(myStubPanel);
        myPropertiesPanelPlace.add(myPropertiesPanel.getPanel());
        myPropertiesPanelPlace.repaint();
      }
      myPropertiesPanel.initFrom(myCurrentViewableBreakpoint);
    }
    else {
      myPropertiesPanelPlace.remove(myPropertiesPanel.getPanel());
      myPropertiesPanelPlace.add(myStubPanel);
      myPropertiesPanelPlace.repaint();
    }
    updateButtons();
  }

  public JComponent getControl(String control) {
    return myPropertiesPanel.getControl(control);
  }

  public int getBreakpointCount() {
    return myTable.getRowCount();
  }

  public Breakpoint getBreakpointAt(final int idx) {
    return ((BreakpointTableModel)myTable.getModel()).getBreakpoint(idx);
  }

  public boolean isBreakpointEnabled(final int idx) {
    return ((BreakpointTableModel)myTable.getModel()).isBreakpointEnabled(idx);
  }


}