package com.intellij.refactoring.rename;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.ui.EnableDisableAction;
import com.intellij.refactoring.ui.StringTableCellEditor;
import com.intellij.refactoring.RefactoringBundle;
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
  private final AutomaticRenamer myRenamer;
  private boolean[] myShouldRename;
  private String[] myNewNames;
  PsiNamedElement myRenames[];
  private MyTableModel myTableModel;
  private Table myTable;
  private final Project myProject;


  public AutomaticRenamingDialog(Project project, AutomaticRenamer renamer) {
    super(project, true);
    myProject = project;
    myRenamer = renamer;
    populateData();
    setTitle(myRenamer.getDialogTitle());
    init();
  }

  private void populateData() {
    final Map<PsiNamedElement, String> renames = myRenamer.getRenames();

    List<PsiNamedElement> temp = new ArrayList<PsiNamedElement>();
    for (final PsiNamedElement namedElement : renames.keySet()) {
      final String newName = renames.get(namedElement);
      if (newName != null) temp.add(namedElement);
    }

    myRenames = temp.toArray(new PsiNamedElement[temp.size()]);
    Arrays.sort(myRenames, new Comparator<PsiNamedElement>() {
      public int compare(final PsiNamedElement e1, final PsiNamedElement e2) {
        return e1.getName().compareTo(e2.getName());
      }
    });

    myNewNames = new String[myRenames.length];
    for (int i = 0; i < myNewNames.length; i++) {
      myNewNames[i] = renames.get(myRenames[i]);
    }

    myShouldRename = new boolean[myRenames.length];
    if (myRenamer.isSelectedByDefault()) {
      for(int i=0; i<myShouldRename.length; i++) {
        myShouldRename [i] = true;
      }
    }
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.rename.AutomaticRenamingDialog";
  }

  protected JComponent createNorthPanel() {
    final Box box = Box.createHorizontalBox();
    box.add(new JLabel(myRenamer.getDialogDescription()));
    box.add(Box.createHorizontalGlue());
    return box;
  }

  public void show() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    super.show();
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
    final JButton selectAllButton = new JButton();
    selectAllButton.setText(RefactoringBundle.message("select.all.button"));
    buttonBox.add(selectAllButton);
    buttonBox.add(Box.createHorizontalStrut(4));
    final JButton deselectAllButton = new JButton();
    deselectAllButton.setText(RefactoringBundle.message("unselect.all.button"));
    buttonBox.add(deselectAllButton);
    selectAllButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        for (int i = 0; i < myShouldRename.length; i++) {
          myShouldRename[i] = true;
        }
        myTableModel.fireTableDataChanged();
      }
    });

    deselectAllButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        for (int i = 0; i < myShouldRename.length; i++) {
          myShouldRename[i] = false;
        }
        myTableModel.fireTableDataChanged();
      }
    });
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
    for (int i = 0; i < myRenames.length; i++) {
      PsiNamedElement element = myRenames[i];
      if (myShouldRename[i]) {
        myRenamer.setRename(element, myNewNames[i]);
      }
      else {
        myRenamer.doNotRename(element);
      }
    }
  }

  protected void setChecked(int rowIndex, boolean checked) {
    myTableModel.setValueAt(Boolean.valueOf(checked), rowIndex, CHECK_COLUMN);
  }

  protected String[] getNewNames() {
    return myNewNames;
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
          return myRenames[rowIndex].getName();
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
          return RefactoringBundle.message("automatic.renamer.enity.name.column", myRenamer.entityName());
        case NEW_NAME_COLUMN:
          return RefactoringBundle.message("automatic.renamer.rename.to.column");
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
        for (final int row : rows) {
          myShouldRename[row] = valueToBeSet;
        }
        fireTableDataChanged();
      }
    }
  }

}
