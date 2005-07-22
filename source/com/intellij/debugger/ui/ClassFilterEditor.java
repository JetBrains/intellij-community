/*
 * Class ClassFilterEditor
 * @author Jeka
 */
package com.intellij.debugger.ui;

import com.intellij.debugger.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class ClassFilterEditor extends JPanel {
  protected JTable myTable = null;
  protected FilterTableModel myTableModel = null;
  private JButton myAddClassButton;
  protected JButton myAddPatternButton;
  private JButton myRemoveButton;
  protected Project myProject;
  private TreeClassChooser.ClassFilter myChooserFilter;

  public ClassFilterEditor(Project project) {
    this (project, null);
  }

  public ClassFilterEditor(Project project, TreeClassChooser.ClassFilter classFilter) {
    super(new GridBagLayout());
    myAddClassButton = new JButton("Add Class...");
    myAddPatternButton = new JButton("Add Pattern...");
    myRemoveButton = new JButton("Remove");
    myTable = new Table();
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);

    add(scrollPane, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 3, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(4, 4, 4, 6), 0, 0));
    add(myAddClassButton, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 4), 0, 0));
    add(myAddPatternButton, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 4), 0, 0));
    add(myRemoveButton, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 4), 0, 0));

    myChooserFilter = classFilter;
    myProject = project;
    myAddClassButton.setDefaultCapable(false);
    myAddPatternButton.setDefaultCapable(false);
    myRemoveButton.setDefaultCapable(false);

    myTableModel = new FilterTableModel();
    myTable.setModel(myTableModel);
    myTable.setShowGrid(false);
    myTable.setIntercellSpacing(new Dimension(0, 0));
    myTable.setTableHeader(null);
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    myTable.setColumnSelectionAllowed(false);
    myTable.setPreferredScrollableViewportSize(new Dimension(200, 100));

    TableColumnModel columnModel = myTable.getColumnModel();
    TableColumn column = columnModel.getColumn(myTableModel.CHECK_MARK);
    int width = new JCheckBox().getPreferredSize().width;
    column.setPreferredWidth(width);
    column.setMaxWidth(width);
    column.setCellRenderer(new EnabledCellRenderer(myTable.getDefaultRenderer(Boolean.class)));
    columnModel.getColumn(myTableModel.FILTER).setCellRenderer(new FilterCellRenderer());

    myTable.registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          myAddClassButton.doClick();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0),
      JComponent.WHEN_FOCUSED
    );
    myTable.registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          myRemoveButton.doClick();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
      JComponent.WHEN_FOCUSED
    );
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        myRemoveButton.setEnabled(myTable.getSelectedRow() > -1);
      }
    });

    myAddPatternButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        addPatternFilter();
      }
    });
    myAddClassButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        addClassFilter();
      }
    });

    myRemoveButton.addActionListener(new RemoveAction());
    myRemoveButton.setEnabled(false);
  }

  public void setFilters(ClassFilter[] filters) {
    myTableModel.setFilters(filters);
  }

  public ClassFilter[] getFilters() {
    return myTableModel.getFilters();
  }

  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);

    myAddPatternButton.setEnabled(enabled);
    myAddClassButton.setEnabled(enabled);
    myRemoveButton.setEnabled((myTable.getSelectedRow() > -1) && enabled);
    myTable.setRowSelectionAllowed(enabled);
    myTableModel.fireTableDataChanged();
  }

  public void stopEditing() {
    TableCellEditor editor = myTable.getCellEditor();
    if (editor != null) {
      editor.stopCellEditing();
    }
  }

  protected final class FilterTableModel extends AbstractTableModel {
    private List myFilters = new LinkedList();
    public final int CHECK_MARK = 0;
    public final int FILTER = 1;

    public FilterTableModel() {
    }

    public final void setFilters(ClassFilter[] filters) {
      myFilters.clear();
      if (filters != null) {
        for (int idx = 0; idx < filters.length; idx++) {
          myFilters.add(filters[idx]);
        }
      }
      fireTableDataChanged();
    }

    public ClassFilter[] getFilters() {
      for (Iterator it = myFilters.iterator(); it.hasNext();) {
        ClassFilter filter = (ClassFilter)it.next();
        String pattern = filter.getPattern();
        if (pattern == null || "".equals(pattern)) {
          it.remove();
        }
      }
      return (ClassFilter[])myFilters.toArray(new ClassFilter[myFilters.size()]);
    }

    public ClassFilter getFilterAt(int index) {
      return (ClassFilter)myFilters.get(index);
    }

    public int getFilterIndex(ClassFilter filter) {
      return myFilters.indexOf(filter);
    }

    protected void addRow(ClassFilter filter) {
      myFilters.add(filter);
      int row = myFilters.size() - 1;
      fireTableRowsInserted(row, row);
    }

    public void removeRows(int[] rows) {
      List toRemove = new LinkedList();
      for (int idx = 0; idx < rows.length; idx++) {
        toRemove.add(myFilters.get(rows[idx]));
      }
      myFilters.removeAll(toRemove);
      toRemove.clear();
      fireTableDataChanged();
    }

    public int getRowCount() {
      return myFilters.size();
    }

    public int getColumnCount() {
      return 2;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      ClassFilter filter = (ClassFilter)myFilters.get(rowIndex);
      if (columnIndex == FILTER) {
        return filter;
      }
      if (columnIndex == CHECK_MARK) {
        return filter.isEnabled()? Boolean.TRUE : Boolean.FALSE;
      }
      return null;
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      ClassFilter filter = (ClassFilter)myFilters.get(rowIndex);
      if (columnIndex == FILTER) {
        filter.setPattern(aValue != null? aValue.toString() : "");
      }
      else if (columnIndex == CHECK_MARK) {
        filter.setEnabled(aValue != null? ((Boolean)aValue).booleanValue() : true);
      }
//      fireTableCellUpdated(rowIndex, columnIndex);
      fireTableRowsUpdated(rowIndex, rowIndex);
    }

    public Class getColumnClass(int columnIndex) {
      if (columnIndex == CHECK_MARK) {
        return Boolean.class;
      }
      return super.getColumnClass(columnIndex);
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      if (ClassFilterEditor.this.isEnabled()) {
        return (columnIndex == CHECK_MARK);
      }
      return false;
    }
  }

  private class FilterCellRenderer extends DefaultTableCellRenderer {
    public Component getTableCellRendererComponent(JTable table, Object value,
      boolean isSelected, boolean hasFocus, int row, int column) {
      Color color = UIManager.getColor("Table.focusCellBackground");
      UIManager.put("Table.focusCellBackground", table.getSelectionBackground());
      Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (component instanceof JLabel) {
        ((JLabel)component).setBorder(noFocusBorder);
      }
      UIManager.put("Table.focusCellBackground", color);
      ClassFilter filter = (ClassFilter)table.getValueAt(row, myTableModel.FILTER);
      component.setEnabled(ClassFilterEditor.this.isEnabled() && filter.isEnabled());
      return component;
    }
  }

  private class EnabledCellRenderer extends DefaultTableCellRenderer {
    private TableCellRenderer myDelegate;

    public EnabledCellRenderer(TableCellRenderer delegate) {
      myDelegate = delegate;
    }

    public Component getTableCellRendererComponent(JTable table, Object value,
      boolean isSelected, boolean hasFocus, int row, int column) {
      Component component = myDelegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      component.setEnabled(ClassFilterEditor.this.isEnabled());
      return component;
    }
  }

  protected ClassFilter createFilter(String pattern){
    return new ClassFilter(pattern);
  }

  protected void addPatternFilter() {
    ClassFilterEditorAddDialog dialog = new ClassFilterEditorAddDialog(myProject);
    dialog.show();
    if (dialog.isOK()) {
      String pattern = dialog.getPattern();
      if (pattern != null) {
        ClassFilter filter = createFilter(pattern);
        if(filter != null){
          myTableModel.addRow(filter);
          int row = myTableModel.getRowCount() - 1;
          myTable.getSelectionModel().setSelectionInterval(row, row);
          myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));

        }
        myTable.requestFocus();
      }
    }
  }

  protected void addClassFilter() {
    TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createNoInnerClassesScopeChooser("Choose Class", GlobalSearchScope.allScope(myProject), myChooserFilter, null);
    chooser.showDialog();
    PsiClass selectedClass = chooser.getSelectedClass();
    if (selectedClass != null) {
      ClassFilter filter = createFilter(selectedClass.getQualifiedName());
      if(filter != null){
        myTableModel.addRow(filter);
        int row = myTableModel.getRowCount() - 1;
        myTable.getSelectionModel().setSelectionInterval(row, row);
        myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));

      }
      myTable.requestFocus();
    }
  }

  private final class RemoveAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      if(myTable.getRowCount() == 0) return;
      int[] rows = myTable.getSelectedRows();
      stopEditing();
      if (rows.length > 0) {
        int newRow = rows[0] - 1;
        ClassFilter filter = (newRow >= 0 && newRow < myTableModel.getRowCount())? myTableModel.getFilterAt(newRow) : null;
        myTableModel.removeRows(rows);
        int indexToSelect = 0;
        if (filter != null) {
          indexToSelect = myTableModel.getFilterIndex(filter);
          if (indexToSelect < 0) {
            indexToSelect = 0;
          }
        }
        if (myTableModel.getRowCount() > 0) {
          myTable.getSelectionModel().setSelectionInterval(indexToSelect, indexToSelect);
        }
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            myTable.requestFocus();
          }
        });
      }
    }
  }
}