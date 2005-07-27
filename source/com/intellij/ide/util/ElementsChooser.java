package com.intellij.ide.util;

import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ElementsChooser<T> extends JPanel {
  private JTable myTable = null;
  private MyTableModel myTableModel = null;
  private boolean myColorUnmarkedElements = true;
  private List<ElementsMarkListener<T>> myListeners = new ArrayList<ElementsMarkListener<T>>();
  private Map<T,ElementProperties> myElementToPropertiesMap = new HashMap<T, ElementProperties>();

  public ElementsChooser() {
    this(null, false);
  }

  public void refresh() {
    myTableModel.fireTableDataChanged();
  }
  public void refresh(T element) {
    final int row = myTableModel.getElementRow(element);
    if (row >= 0) {
      myTableModel.fireTableRowsUpdated(row, row);
    }
  }

  public static interface ElementsMarkListener<T> {
    void elementMarkChanged(T element, boolean isMarked);
  }

  public ElementsChooser(List<T> elements, boolean marked) {
    super(new BorderLayout());

    myTableModel = new MyTableModel();
    myTable = new Table(myTableModel);
    myTable.setShowGrid(false);
    myTable.setIntercellSpacing(new Dimension(0, 0));
    myTable.setTableHeader(null);
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    myTable.setColumnSelectionAllowed(false);
    JScrollPane pane = ScrollPaneFactory.createScrollPane(myTable);
    pane.setPreferredSize(new Dimension(100, 155));
    int width = new JCheckBox().getPreferredSize().width;
    TableColumnModel columnModel = myTable.getColumnModel();

    TableColumn checkMarkColumn = columnModel.getColumn(MyTableModel.CHECK_MARK);
    checkMarkColumn.setPreferredWidth(width);
    checkMarkColumn.setMaxWidth(width);
    checkMarkColumn.setCellRenderer(new CheckMarkColumnCellRenderer(myTable.getDefaultRenderer(Boolean.class)));
    columnModel.getColumn(MyTableModel.ELEMENT).setCellRenderer(new MyElementColumnCellRenderer());

    add(pane, BorderLayout.CENTER);
    myTable.registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          final int[] selectedRows = myTable.getSelectedRows();
          boolean currentlyMarked = true;
          for (int idx = 0; idx < selectedRows.length; idx++) {
            currentlyMarked = myTableModel.isElementMarked(selectedRows[idx]);
            if (!currentlyMarked) {
              break;
            }
          }
          myTableModel.setMarked(selectedRows, !currentlyMarked);
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
      JComponent.WHEN_FOCUSED
    );

    setElements(elements, marked);
  }

  private int[] mySavedSelection = null;
  public void saveSelection() {
    mySavedSelection = myTable.getSelectedRows();
  }

  public void restoreSelection() {
    if (mySavedSelection != null) {
      TableUtil.selectRows(myTable, mySavedSelection);
      mySavedSelection = null;
    }
  }

  public boolean isColorUnmarkedElements() {
    return myColorUnmarkedElements;
  }

  public void setColorUnmarkedElements(boolean colorUnmarkedElements) {
    myColorUnmarkedElements = colorUnmarkedElements;
  }

  public void addElementsMarkListener(ElementsMarkListener<T> listener) {
    myListeners.add(listener);
  }

  public void removeElementsMarkListener(ElementsMarkListener<T> listener) {
    myListeners.remove(listener);
  }

  public void addListSelectionListener(ListSelectionListener listener) {
    myTable.getSelectionModel().addListSelectionListener(listener);
  }
  public void removeListSelectionListener(ListSelectionListener listener) {
    myTable.getSelectionModel().removeListSelectionListener(listener);
  }

  public void addElement(T element, final boolean isMarked) {
    addElement(element, isMarked, null);
  }

  public void removeElement(T element) {
    final int elementRow = myTableModel.getElementRow(element);
    if (elementRow < 0) {
      return; // no such element
    }
    final boolean wasSelected = myTable.getSelectionModel().isSelectedIndex(elementRow);

    myTableModel.removeElement(element);
    myElementToPropertiesMap.remove(element);

    if (wasSelected) {
      final int rowCount = myTableModel.getRowCount();
      if (rowCount > 0) {
        selectRow((elementRow + 1) % rowCount);
      }
      else {
        myTable.getSelectionModel().clearSelection();
      }
    }
    myTable.requestFocus();
  }

  public void removeAllElements() {
    myTableModel.removeAllElements();
    myTable.getSelectionModel().clearSelection();
  }

  private void selectRow(final int row) {
    myTable.getSelectionModel().setSelectionInterval(row, row);
    myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));
  }

  public void moveElement(T element, int newRow) {
    final int elementRow = myTableModel.getElementRow(element);
    if (elementRow < 0 || elementRow == newRow || newRow < 0 || newRow >= myTableModel.getRowCount()) {
      return;
    }
    final boolean wasSelected = myTable.getSelectionModel().isSelectedIndex(elementRow);
    myTableModel.changeElementRow(element, newRow);
    if (wasSelected) {
      selectRow(newRow);
    }
  }

  public static interface ElementProperties {
    Icon getIcon();
    Color getColor();
  }
  public void addElement(T element, final boolean isMarked, ElementProperties elementProperties) {
    myTableModel.addElement(element, isMarked);
    myElementToPropertiesMap.put(element, elementProperties);
    selectRow(myTableModel.getRowCount() - 1);
    myTable.requestFocus();
  }

  public void setElements(List<T> elements, boolean marked) {
    myTableModel.clear();
    myTableModel.addElements(elements, marked);
  }

  public T getSelectedElement() {
    final int selectedRow = getSelectedElementRow();
    return selectedRow < 0? null : myTableModel.getElementAt(selectedRow);
  }

  public int getSelectedElementRow() {
    return myTable.getSelectedRow();
  }

  public List<T> getSelectedElements() {
    final List<T> elements = new ArrayList<T>();
    final int[] selectedRows = myTable.getSelectedRows();
    for (int selectedRow : selectedRows) {
      if (selectedRow < 0) {
        continue;
      }
      elements.add(myTableModel.getElementAt(selectedRow));
    }
    return elements;
  }

  public void selectElements(List<T> elements) {
    if (elements.size() == 0) {
      myTable.clearSelection();
      return;
    }
    final int[] rows = new int[elements.size()];
    int index = 0;
    for (final T element : elements) {
      rows[index++] = myTableModel.getElementRow(element);
    }
    TableUtil.selectRows(myTable, rows);
    myTable.requestFocus();
  }

  public List<T> getMarkedElements() {
    final int count = myTableModel.getRowCount();
    List<T> elements = new ArrayList<T>();
    for (int idx = 0; idx < count; idx++) {
      final T element = myTableModel.getElementAt(idx);
      if (myTableModel.isElementMarked(idx)) {
        elements.add(element);
      }
    }
    return elements;
  }

  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myTable.setRowSelectionAllowed(enabled);
    myTableModel.fireTableDataChanged();
  }

  public void stopEditing() {
    TableCellEditor editor = myTable.getCellEditor();
    if (editor != null) {
      editor.stopCellEditing();
    }
  }

  public JComponent getComponent() {
    return myTable;
  }

  public void setAllElementsMarked(boolean marked) {
    final int[] rows = new int[myTableModel.getRowCount()];
    for (int idx = 0; idx < rows.length; idx++) {
      rows[idx] = idx;
    }
    myTableModel.setMarked(rows, marked);
  }

  private void notifyElementMarked(T element, boolean isMarked) {
    final Object[] listeners = myListeners.toArray();
    for (Object listener1 : listeners) {
      ElementsMarkListener<T> listener = (ElementsMarkListener<T>)listener1;
      listener.elementMarkChanged(element, isMarked);
    }
  }

  public void clear() {
    myTableModel.clear();
    myElementToPropertiesMap.clear();
  }

  public int getElementCount() {
    return myTableModel.getRowCount();
  }

  public T getElementAt(int row) {
    return myTableModel.getElementAt(row);
  }

  private final class MyTableModel extends AbstractTableModel {
    private final List<T> myElements = new ArrayList<T>();
    private final Map<T, Boolean> myMarkedMap = new HashMap<T, Boolean>();
    public static final int CHECK_MARK = 0;
    public static final int ELEMENT = 1;

    public T getElementAt(int index) {
      return myElements.get(index);
    }

    public boolean isElementMarked(int index) {
      final T element = myElements.get(index);
      final Boolean isMarked = myMarkedMap.get(element);
      return isMarked.booleanValue();
    }

    protected void addElement(T element, boolean isMarked) {
      myElements.add(element);
      myMarkedMap.put(element, isMarked? Boolean.TRUE : Boolean.FALSE);
      int row = myElements.size() - 1;
      fireTableRowsInserted(row, row);
    }

    protected void addElements(List<T> elements, boolean isMarked) {
      if (elements == null || elements.size() == 0) {
        return;
      }
      for (final T element : elements) {
        myElements.add(element);
        myMarkedMap.put(element, isMarked ? Boolean.TRUE : Boolean.FALSE);
      }
      fireTableRowsInserted(myElements.size() - elements.size(), myElements.size() - 1);
    }

    public void removeElement(T element) {
      final boolean reallyRemoved = myElements.remove(element);
      if (reallyRemoved) {
        myMarkedMap.remove(element);
        fireTableDataChanged();
      }
    }

    public void changeElementRow(T element, int row) {
      final boolean reallyRemoved = myElements.remove(element);
      if (reallyRemoved) {
        myElements.add(row, element);
        fireTableDataChanged();
      }
    }

    public int getElementRow(T element) {
      return myElements.indexOf(element);
    }

    public void removeAllElements() {
      myElements.clear();
      fireTableDataChanged();
    }

    public void removeRows(int[] rows) {
      final List<T> toRemove = new ArrayList<T>();
      for (int row : rows) {
        final T element = myElements.get(row);
        toRemove.add(element);
        myMarkedMap.remove(element);
      }
      myElements.removeAll(toRemove);
      fireTableDataChanged();
    }

    public int getRowCount() {
      return myElements.size();
    }

    public int getColumnCount() {
      return 2;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      T element = myElements.get(rowIndex);
      if (columnIndex == ELEMENT) {
        return element;
      }
      if (columnIndex == CHECK_MARK) {
        return myMarkedMap.get(element);
      }
      return null;
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (columnIndex == CHECK_MARK) {
        setMarked(rowIndex, ((Boolean)aValue).booleanValue());
      }
    }

    private void setMarked(int rowIndex, final boolean marked) {
      final T element = myElements.get(rowIndex);
      final Boolean newValue = marked? Boolean.TRUE : Boolean.FALSE;
      final Boolean prevValue = myMarkedMap.put(element, newValue);
      fireTableRowsUpdated(rowIndex, rowIndex);
      if (!newValue.equals(prevValue)) {
        notifyElementMarked(element, marked);
      }
    }

    private void setMarked(int[] rows, final boolean marked) {
      if (rows == null || rows.length == 0) {
        return;
      }
      int firstRow = Integer.MAX_VALUE;
      int lastRow = Integer.MIN_VALUE;
      final Boolean newValue = marked? Boolean.TRUE : Boolean.FALSE;
      for (final int row : rows) {
        final T element = myElements.get(row);
        final Boolean prevValue = myMarkedMap.put(element, newValue);
        if (!newValue.equals(prevValue)) {
          notifyElementMarked(element, newValue.booleanValue());
        }
        firstRow = Math.min(firstRow, row);
        lastRow = Math.max(lastRow, row);
      }
      fireTableRowsUpdated(firstRow, lastRow);
    }

    public Class getColumnClass(int columnIndex) {
      if (columnIndex == CHECK_MARK) {
        return Boolean.class;
      }
      return super.getColumnClass(columnIndex);
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      if (ElementsChooser.this.isEnabled()) {
        return columnIndex == CHECK_MARK;
      }
      return false;
    }

    public void clear() {
      myElements.clear();
      myMarkedMap.clear();
      fireTableDataChanged();
    }
  }

  protected String getItemText(T value) {
    return value != null ? value.toString() : "";
  }

  private class MyElementColumnCellRenderer extends DefaultTableCellRenderer {
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      final Color color = UIManager.getColor("Table.focusCellBackground");
      Component component;
      T t = (T)value;
      try {
        UIManager.put("Table.focusCellBackground", table.getSelectionBackground());
        component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setText(getItemText(t));
        if (component instanceof JLabel) {
          ((JLabel)component).setBorder(noFocusBorder);
        }
      }
      finally {
        UIManager.put("Table.focusCellBackground", color);
      }
      final MyTableModel model = (MyTableModel)table.getModel();
      component.setEnabled(ElementsChooser.this.isEnabled() && (myColorUnmarkedElements? model.isElementMarked(row) : true));
      final ElementProperties properties = myElementToPropertiesMap.get(t);
      if (component instanceof JLabel) {
        ((JLabel)component).setIcon(properties != null? properties.getIcon() : null);
      }
      component.setForeground(properties != null && properties.getColor() != null ?
                              properties.getColor() :
                              (isSelected ? table.getSelectionForeground() : table.getForeground()));
      return component;
    }
  }

  private class CheckMarkColumnCellRenderer implements TableCellRenderer {
    private TableCellRenderer myDelegate;

    public CheckMarkColumnCellRenderer(TableCellRenderer delegate) {
      myDelegate = delegate;
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component component = myDelegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      component.setEnabled(ElementsChooser.this.isEnabled());
      if (component instanceof JComponent) {
        ((JComponent)component).setBorder(null);
      }
      return component;
    }
  }
}
