package com.intellij.uiDesigner.propertyInspector.editors.string;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class KeyChooserDialog extends DialogWrapper{
  private final String myBundleName;
  /** List of bundle's pairs*/
  private final ArrayList<Pair<String, String>> myPairs;
  private final JComponent myCenterPanel;
  /** Table with key/value pairs */
  private final Table myTable;

  /**
   * @param bundle resource bundle to be shown.
   *
   * @param bundleName name of the resource bundle to be shown. We need this
   * name to create StringDescriptor in {@link #getDescriptor()} method.
   *
   * @param keyToPreselect describes row that should be selected in the
   * table with key/value pairs.
   */
  public KeyChooserDialog(
    final Component parent, 
    final ResourceBundle bundle,
    final String bundleName,
    final String keyToPreselect
  ) {
    super(parent, true);

    // Check args
    if(bundle == null){
      throw new IllegalArgumentException();
    }
    if(bundleName == null){
      throw new IllegalArgumentException();
    }

    myBundleName = bundleName;

    setTitle("Chooser Value");

    // Read key/value pairs from resource bundle
    myPairs = new ArrayList<Pair<String, String>>();

    for(Enumeration keys = bundle.getKeys(); keys.hasMoreElements();){
      final String key = (String)keys.nextElement();
      final String value = bundle.getString(key);
      myPairs.add(new Pair<String, String>(key, value));
    }
    Collections.sort(myPairs, new MyPairComparator());

    // Create UI
    final MyTableModel model = new MyTableModel();
    myTable = new Table(model);
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myCenterPanel = ScrollPaneFactory.createScrollPane(myTable);

    // Calculate width for "Key" columns
    final FontMetrics metrics = myTable.getFontMetrics(myTable.getFont());
    int width = 0;
    for(int i = myPairs.size() - 1; i >= 0; i--){
      final Pair<String, String> pair = myPairs.get(i);
      width = Math.max(width, metrics.stringWidth(pair.getFirst()));
    }
    width += 30;
    width = Math.max(width, metrics.stringWidth(model.getColumnName(0)));
    final TableColumn keyColumn = myTable.getColumnModel().getColumn(0);
    keyColumn.setMaxWidth(width);
    keyColumn.setMinWidth(width);

    // Preselect proper row
    int indexToPreselect = -1;
    for(int i = myPairs.size() - 1; i >= 0; i--){
      final Pair<String, String> pair = myPairs.get(i);
      if(pair.getFirst().equals(keyToPreselect)){
        indexToPreselect = i;
        break;
      }
    }
    if(indexToPreselect != -1){
      myTable.getSelectionModel().setSelectionInterval(indexToPreselect, indexToPreselect);
      myTable.scrollRectToVisible(myTable.getCellRect(indexToPreselect, 0, true));
    }

    init();
  }

  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }

  /**
   * @return resolved string descriptor. If user chose nothing then the
   * method returns <code>null</code>.
   */
  StringDescriptor getDescriptor(){
    final int selectedRow = myTable.getSelectedRow();
    if(selectedRow < 0 || selectedRow >= myTable.getRowCount()){
      return null;
    }
    else{
      final Pair<String, String> pair = myPairs.get(selectedRow);
      final StringDescriptor descriptor = new StringDescriptor(myBundleName, pair.getFirst());
      descriptor.setResolvedValue(pair.getSecond());
      return descriptor;
    }
  }

  protected JComponent createCenterPanel() {
    return myCenterPanel;
  }

  private static final class MyPairComparator implements Comparator<Pair<String, String>>{
    public int compare(final Pair<String, String> p1, final Pair<String, String> p2) {
      return p1.getFirst().compareToIgnoreCase(p2.getSecond());
    }
  }

  private final class MyTableModel extends AbstractTableModel{
    public int getColumnCount() {
      return 2;
    }

    public String getColumnName(final int column) {
      if(column == 0){
        return "Key";
      }
      else if(column == 1){
        return "Value";
      }
      else{
        throw new IllegalArgumentException("unknown column: " + column);
      }
    }

    public Class getColumnClass(final int column) {
      if(column == 0){
        return String.class;
      }
      else if(column == 1){
        return String.class;
      }
      else{
        throw new IllegalArgumentException("unknown column: " + column);
      }
    }

    public Object getValueAt(final int row, final int column) {
      if(column == 0){
        return myPairs.get(row).getFirst();
      }
      else if(column == 1){
        return myPairs.get(row).getSecond();
      }
      else{
        throw new IllegalArgumentException("unknown column: " + column);
      }
    }

    public int getRowCount() {
      return myPairs.size();
    }
  }
}
