package com.intellij.refactoring.rename;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.ui.EnableDisableAction;
import com.intellij.refactoring.ui.StringTableCellEditor;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

/**
 * @author dsl
 */
public class AutomaticRenamingDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.AutomaticRenamingDialog");
  private static final int CHECK_COLUMN = 0;
  private static final int OLD_NAME_COLUMN = 1;
  private static final int NEW_NAME_COLUMN = 2;
  private final AutomaticRenamer<?> myRenamer;
  private boolean[] myShouldRename;
  private String[] myOldNames;
  private String[] myNewNames;
  private MyTableModel myTableModel;
  private Table myTable;
  private final Project myProject;


  public AutomaticRenamingDialog(Project project, AutomaticRenamer<?> renamer) {
    super(project, true);
    myProject = project;
    myRenamer = renamer;
    populateData();
    setTitle(myRenamer.getDialogTitle());
    init();
  }

  private void populateData() {
    List<String> oldNames = new ArrayList<String>();
    final Map<String,String> renames = myRenamer.getRenames();
    for (Iterator<String> iterator = renames.keySet().iterator(); iterator.hasNext();) {
      final String oldName = iterator.next();
      if (renames.get(oldName) != null) {
       oldNames.add(oldName);
      }
    }
    myOldNames = (String[])oldNames.toArray(new String[oldNames.size()]);
    myShouldRename = new boolean[myOldNames.length];
    myNewNames = new String[myOldNames.length];
    Arrays.sort(myOldNames);
    for (int i = 0; i < myOldNames.length; i++) {
      final String oldName = myOldNames[i];
      myShouldRename[i] = true;
      myNewNames[i] = renames.get(oldName);
    }
  }

  protected JComponent createNorthPanel() {
    final Box box = Box.createHorizontalBox();
    box.add(new JLabel(myRenamer.getDialogDescription()));
    box.add(Box.createHorizontalGlue());
    return box;
  }

  protected void handleChanges() {
  }

  protected JComponent createCenterPanel() {
    final Box box = Box.createVerticalBox();
    myTableModel = new MyTableModel();
    myTable = new Table(myTableModel);
    myTableModel.getSpaceAction().register();
    myTableModel.addTableModelListener(new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        handleChanges();
      }
    });

    final TableColumnModel columnModel = myTable.getColumnModel();
    columnModel.getColumn(CHECK_COLUMN).setCellRenderer(new BooleanTableCellRenderer());
    final int checkBoxWidth = new JCheckBox().getPreferredSize().width;
    columnModel.getColumn(CHECK_COLUMN).setMaxWidth(checkBoxWidth);
    columnModel.getColumn(CHECK_COLUMN).setMinWidth(checkBoxWidth);

    columnModel.getColumn(NEW_NAME_COLUMN).setCellEditor(new StringTableCellEditor(myProject));
    final JScrollPane jScrollPane2 = ScrollPaneFactory.createScrollPane(myTable);
    box.add(jScrollPane2);
    final Box buttonBox = Box.createHorizontalBox();
    buttonBox.add(Box.createHorizontalGlue());
    final JButton selectAllButton = new JButton("Select all");
    buttonBox.add(selectAllButton);
    buttonBox.add(Box.createHorizontalStrut(4));
    final JButton deselectAllButton = new JButton("Unselect all");
    buttonBox.add(deselectAllButton);
    selectAllButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        for (int i = 0; i < myShouldRename.length; i++) {
          myShouldRename[i] = true;
        }
        myTableModel.fireTableDataChanged();
      }
    });
    selectAllButton.setMnemonic('S');

    deselectAllButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        for (int i = 0; i < myShouldRename.length; i++) {
          myShouldRename[i] = false;
        }
        myTableModel.fireTableDataChanged();
      }
    });
    deselectAllButton.setMnemonic('U');
    box.add(Box.createVerticalStrut(4));
    box.add(buttonBox);
    box.add(Box.createVerticalStrut(4));

    return box;
  }

  protected void doOKAction() {
    TableUtil.stopEditing(myTable);
    updateRenamer();
    super.doOKAction();
  }

  protected void updateRenamer() {
    for (int i = 0; i < myOldNames.length; i++) {
      String oldName = myOldNames[i];
      if (myShouldRename[i]) {
        myRenamer.setRename(oldName, myNewNames[i]);
      }
      else {
        myRenamer.doNotRename(oldName);
      }
    }
  }

  protected void setChecked(int rowIndex, boolean checked) {
    myTableModel.setValueAt(Boolean.valueOf(checked), rowIndex, CHECK_COLUMN);
  }

  protected String[] getNewNames() {
    return myNewNames;
  }

  protected String[] getOldNames() {
    return myOldNames;
  }

  private class MyTableModel extends AbstractTableModel {
    public int getColumnCount() {
      return 3;
    }

    public int getRowCount() {
      return myShouldRename.length;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      switch(columnIndex) {
        case CHECK_COLUMN:
          return Boolean.valueOf(myShouldRename[rowIndex]);
        case OLD_NAME_COLUMN:
          return myOldNames[rowIndex];
        case NEW_NAME_COLUMN:
          return myNewNames[rowIndex];
        default:
          LOG.assertTrue(false);
          return null;
      }
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      switch(columnIndex) {
        case CHECK_COLUMN:
          myShouldRename[rowIndex] = ((Boolean)aValue).booleanValue();
          break;
        case NEW_NAME_COLUMN:
          myNewNames[rowIndex] = (String) aValue;
          break;
        default:
          LOG.assertTrue(false);
      }
      handleChanges();
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex != OLD_NAME_COLUMN;
    }

    public Class getColumnClass(int columnIndex) {
      switch(columnIndex) {
        case CHECK_COLUMN: return Boolean.class;
        case OLD_NAME_COLUMN: return String.class;
        case NEW_NAME_COLUMN: return String.class;
        default: return null;
      }
    }

    public String getColumnName(int column) {
      switch(column) {
        case OLD_NAME_COLUMN:
          return myRenamer.entityName() + " name";
        case NEW_NAME_COLUMN:
          return "Rename To";
        default:
          return " ";
      }
    }

    private MyEnableDisable getSpaceAction() {
      return this.new MyEnableDisable();
    }

    private class MyEnableDisable extends EnableDisableAction {
      protected JTable getTable() {
        return myTable;
      }

      protected boolean isRowChecked(int row) {
        return myShouldRename[row];
      }

      protected void applyValue(int[] rows, boolean valueToBeSet) {
        for (int i = 0; i < rows.length; i++) {
          final int row = rows[i];
          myShouldRename[row] = valueToBeSet;
        }
        fireTableDataChanged();
      }
    }
  }

}
