package com.intellij.debugger.ui;

import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ui.OptionsDialog;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.*;

/**
 * User: lex
 * Date: Oct 6, 2003
 * Time: 5:58:17 PM
 */



public class RunHotswapDialog extends OptionsDialog {
  private final java.util.Set <DebuggerSession>  mySessionsToReload;
  private final java.util.List<DebuggerSession>  mySessions;
  private JPanel myPanel;
  private JTable mySessionsTable;

  public RunHotswapDialog(Project project, java.util.List<DebuggerSession> sessions) {
    super(project);
    if(sessions.size() == 1) {
      setTitle("Reload Changed Classes for " + sessions.get(0).getSessionName());
      myPanel.setVisible(false);
    }
    else {
      setTitle("Reload Changed Classes");
    }
    setButtonsAlignment(SwingUtilities.CENTER);
    mySessionsToReload = new HashSet<DebuggerSession>(sessions);
    mySessions = new ArrayList<DebuggerSession>(sessions.size());
    mySessions.addAll(sessions);
    Collections.sort(mySessions, new Comparator<DebuggerSession>() {
      public int compare(DebuggerSession debuggerSession, DebuggerSession debuggerSession1) {
        return debuggerSession.getSessionName().compareTo(debuggerSession1.getSessionName());
      }
    });

    mySessionsTable.setModel(new AbstractTableModel() {
      private static final int CHECKBOX_COLUMN = 0;
      private static final int SESSION_COLUMN  = 1;

      public String getColumnName(int column) {
        return column == SESSION_COLUMN ? "Session" : "";
      }

      public Class getColumnClass(int columnIndex) {
        if (columnIndex == CHECKBOX_COLUMN) {
          return Boolean.class;
        }
        return super.getColumnClass(columnIndex);
      }

      public int getColumnCount() {
        return 2;
      }

      public int getRowCount() {
        return mySessions.size();
      }

      public Object getValueAt(int rowIndex, int columnIndex) {
        if(columnIndex == CHECKBOX_COLUMN) {
          return new Boolean(mySessionsToReload.contains(mySessions.get(rowIndex)));
        }
        else {
          return mySessions.get(rowIndex).getSessionName();
        }
      }

      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return rowIndex == CHECKBOX_COLUMN;
      }

      public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if(rowIndex == CHECKBOX_COLUMN) {
          if(Boolean.TRUE.equals(aValue)) {
            mySessionsToReload.add(mySessions.get(columnIndex));
          }
          else {
            mySessionsToReload.remove(mySessions.get(columnIndex));
          }
        }
      }
    });

    final TableColumn checkboxColumn = mySessionsTable.getTableHeader().getColumnModel().getColumn(0);
    int width = new JCheckBox().getMinimumSize().width;
    checkboxColumn.setWidth(width);
    checkboxColumn.setPreferredWidth(width);
    checkboxColumn.setMaxWidth(width);
    this.init();
  }

  protected boolean isToBeShown() {
    return DebuggerSettings.RUN_HOTSWAP_ASK.equals(DebuggerSettings.getInstance().RUN_HOTSWAP_AFTER_COMPILE);
  }

  protected void setToBeShown(boolean value, boolean onOk) {
    if (value) {
      DebuggerSettings.getInstance().RUN_HOTSWAP_AFTER_COMPILE = DebuggerSettings.RUN_HOTSWAP_ASK;
    }
    else {
      if (onOk) {
        DebuggerSettings.getInstance().RUN_HOTSWAP_AFTER_COMPILE = DebuggerSettings.RUN_HOTSWAP_ALWAYS;
      }
      else {
        DebuggerSettings.getInstance().RUN_HOTSWAP_AFTER_COMPILE = DebuggerSettings.RUN_HOTSWAP_NEVER;
      }
    }
  }

  protected boolean shouldSaveOptionsOnCancel() {
    return true;
  }

  protected Action[] createActions(){
    setOKButtonText("Yes");
    setCancelButtonText("No");

    return new Action[]{getOKAction(), getCancelAction()};
  }

  protected JComponent createNorthPanel() {
    JLabel label = new JLabel("Some classes have been changed. Reload changed classes now?");
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(label, BorderLayout.CENTER);
    Icon icon = UIManager.getIcon("OptionPane.questionIcon");
    if (icon != null) {
      label.setIcon(icon);
      label.setIconTextGap(7);
    }
    return panel;
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public Collection<DebuggerSession> getSessionsToReload() {
    ArrayList<DebuggerSession> result = new ArrayList<DebuggerSession>();

    for (Iterator<DebuggerSession> iterator = mySessions.iterator(); iterator.hasNext();) {
      DebuggerSession debuggerSession = iterator.next();
      if(mySessionsToReload.contains(debuggerSession)) {
        result.add(debuggerSession);
      }
    }

    return result;
  }
}
