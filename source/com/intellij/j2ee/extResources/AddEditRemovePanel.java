package com.intellij.j2ee.extResources;

import com.intellij.ui.PanelWithButtons;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author mike
 */
public abstract class AddEditRemovePanel extends PanelWithButtons {
 JPanel myPanel;
  private JTable myTable;
  private JButton myAddButton;
  private JButton myEditButton;
  private JButton myRemoveButton;
  private TableModel myModel;
  private List myData;
  private AbstractTableModel myTableModel;

  public AddEditRemovePanel(String labelText, TableModel model, List data) {
    myModel = model;
    myData = data;

    initPanel();
    updateButtons();

    setBorder(BorderFactory.createTitledBorder(new EtchedBorder(),labelText));
  }

  protected String getLabelText(){
    return null;
  }

  protected JComponent createMainComponent(){
    myTableModel = new AbstractTableModel() {
      public int getRowCount(){
        return myData != null ? myData.size() : 0;
      }

      public int getColumnCount(){
        return myModel.getColumnCount();
      }

      public String getColumnName(int column){
        return myModel.getColumnName(column);
      }

      public Class getColumnClass(int columnIndex){
        return String.class;
      }

      public Object getValueAt(int rowIndex, int columnIndex){
        return myModel.getField(myData.get(rowIndex), columnIndex);
      }
    };

    myTable = new Table(myTableModel);
    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e){
        if (e.getClickCount() == 2) doEdit();
      }
    });

    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e){
        updateButtons();
      }
    });

    JScrollPane scrollpane = ScrollPaneFactory.createScrollPane(myTable);

    return scrollpane;
  }

  protected JButton[] createButtons(){
//    myAddPatternButton = new JButton("Add...");
    myAddButton.setMnemonic(getAddMnemonic());
    myAddButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e){
          doAdd();
        }
      }
    );

//    myEditButton = new JButton("Edit");
    myEditButton.setMnemonic(getEditMnemonic());
    myEditButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e){
          doEdit();
        }
      }
    );

//    myRemoveButton = new JButton("Remove");
    myRemoveButton.setMnemonic(getRemoveMnemonic());
    myRemoveButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e){
          doRemove();
        }
      }
    );

    return new JButton[]{myAddButton, myEditButton, myRemoveButton};
  }

  protected void doAdd() {
    Object o = addItem();
    if (o == null) return;

    myData.add(o);
    int index = myData.size() - 1;
    myTableModel.fireTableRowsInserted(index, index);
    myTable.setRowSelectionInterval(index, index);
  }

  protected abstract Object addItem();
  protected abstract boolean removeItem(Object o);
  protected abstract Object editItem(Object o);

  protected abstract char getRemoveMnemonic();
  protected abstract char getEditMnemonic();
  protected abstract char getAddMnemonic();

  protected void doEdit() {
    int selected = myTable.getSelectedRow();
    Object o = editItem(myData.get(selected));
    if (o != null) myData.set(selected, o);

    myTableModel.fireTableRowsUpdated(selected, selected);
  }

  protected void doRemove() {
    int selected = myTable.getSelectedRow();
    if (!removeItem(myData.get(selected))) return;


    myData.remove(selected);
    AbstractTableModel model = (AbstractTableModel)myTable.getModel();
    model.fireTableRowsDeleted(selected, selected);
    if (selected >= myData.size()) {
      selected--;
    }
    if (selected >= 0) {
      myTable.setRowSelectionInterval(selected, selected);
    }
  }

  public void setData(java.util.List data) {
    myData = data;
    myTableModel.fireTableDataChanged();
  }

  public void setRenderer(int index, TableCellRenderer renderer) {
      myTable.getColumn(myModel.getColumnName(index)).setCellRenderer(renderer);
  }

  private void updateButtons() {
    myEditButton.setEnabled(myTable.getSelectedRowCount() == 1);
    myRemoveButton.setEnabled(myTable.getSelectedRowCount() == 1);
  }

  public static interface TableModel {
    int getColumnCount();
    String getColumnName(int columnIndex);
    Object getField(Object o, int columnIndex);
   }
}
