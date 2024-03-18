// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.editors.string;

import com.intellij.ide.DataManager;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.DimensionService;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.table.JBTable;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.StringDescriptor;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class KeyChooserDialog extends DialogWrapper{
  private final PropertiesFile myBundle;
  private final String myBundleName;
  /** List of bundle's pairs*/
  private ArrayList<Couple<String>> myPairs;
  private final JComponent myCenterPanel;
  /** Table with key/value pairs */
  private final JTable myTable;
  private static final @NonNls String NULL = "null";
  private final MyTableModel myModel;
  private final GuiEditor myEditor;

  private static final String OK_ACTION = "OkAction";

  /**
   * @param bundle resource bundle to be shown.
   * @param bundleName name of the resource bundle to be shown. We need this
   * name to create StringDescriptor in {@link #getDescriptor()} method.
   * @param keyToPreselect describes row that should be selected in the
   * @param parent the parent component for the dialog.
   */
  public KeyChooserDialog(
    final Component parent,
    final @NotNull PropertiesFile bundle,
    final @NotNull String bundleName,
    final String keyToPreselect,
    final GuiEditor editor
  ) {
    super(parent, true);
    myEditor = editor;
    myBundle = bundle;

    myBundleName = bundleName;

    setTitle(UIDesignerBundle.message("title.chooser.value"));

    // Read key/value pairs from resource bundle
    fillPropertyList();

    // Create UI
    myModel = new MyTableModel();
    myTable = new JBTable(myModel);
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    MySpeedSearch search = new MySpeedSearch(myTable);
    search.setupListeners();
    myCenterPanel = ScrollPaneFactory.createScrollPane(myTable);

    myTable.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0), OK_ACTION);
    myTable.getActionMap().put(OK_ACTION, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        getOKAction().actionPerformed(e);
      }
    });

    // Calculate width for "Key" columns
    final Project projectGuess = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parent));
    final Dimension size = DimensionService.getInstance().getSize(getDimensionServiceKey(), projectGuess);
    final FontMetrics metrics = myTable.getFontMetrics(myTable.getFont());
    int minWidth = 200;
    int maxWidth = size != null ? size.width / 2 : Integer.MAX_VALUE;
    if (minWidth > maxWidth) {
      minWidth = maxWidth;
    }
    int width = minWidth;
    for(int i = myPairs.size() - 1; i >= 0; i--){
      final Couple<String> pair = myPairs.get(i);
      width = Math.max(width, metrics.stringWidth(pair.getFirst()));
    }
    width += 20;
    width = Math.max(width, metrics.stringWidth(myModel.getColumnName(0)));
    width = Math.max(width, minWidth);
    width = Math.min(width, maxWidth);
    final TableColumnModel columnModel = myTable.getColumnModel();
    final TableColumn keyColumn = columnModel.getColumn(0);
    keyColumn.setMaxWidth(width);
    keyColumn.setMinWidth(width);
    final TableCellRenderer defaultRenderer = myTable.getDefaultRenderer(String.class);
    if (defaultRenderer instanceof JComponent component) {
      component.putClientProperty("html.disable", Boolean.TRUE);
    }
    selectKey(keyToPreselect);

    init();
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        doOKAction();
        return true;
      }
    }.installOn(myTable);
  }

  private void fillPropertyList() {
    myPairs = new ArrayList<>();

    final List<IProperty> properties = myBundle.getProperties();
    for (IProperty property : properties) {
      final String key = property.getUnescapedKey();
      final String value = property.getValue();
      if (key != null) {
        myPairs.add(Couple.of(key, value != null ? value : NULL));
      }
    }
    myPairs.sort(new MyPairComparator());
  }

  private void selectKey(final String keyToPreselect) {
    // Preselect proper row
    int indexToPreselect = -1;
    for(int i = myPairs.size() - 1; i >= 0; i--){
      final Couple<String> pair = myPairs.get(i);
      if(pair.getFirst().equals(keyToPreselect)){
        indexToPreselect = i;
        break;
      }
    }
    if(indexToPreselect != -1){
      selectElementAt(indexToPreselect);
    }
  }

  @Override protected Action @NotNull [] createLeftSideActions() {
    return new Action[] { new NewKeyValueAction() };
  }

  private void selectElementAt(final int index) {
    myTable.getSelectionModel().setSelectionInterval(index, index);
    myTable.scrollRectToVisible(myTable.getCellRect(index, 0, true));
  }

  @Override
  protected @NotNull String getDimensionServiceKey() {
    return getClass().getName();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }

  /**
   * @return resolved string descriptor. If user chose nothing then the
   * method returns {@code null}.
   */
  @Nullable StringDescriptor getDescriptor() {
    final int selectedRow = myTable.getSelectedRow();
    if(selectedRow < 0 || selectedRow >= myTable.getRowCount()){
      return null;
    }
    else{
      final Couple<String> pair = myPairs.get(selectedRow);
      final StringDescriptor descriptor = new StringDescriptor(myBundleName, pair.getFirst());
      descriptor.setResolvedValue(pair.getSecond());
      return descriptor;
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    return myCenterPanel;
  }

  private static final class MyPairComparator implements Comparator<Couple<String>>{
    @Override
    public int compare(final Couple<String> p1, final Couple<String> p2) {
      return p1.getFirst().compareToIgnoreCase(p2.getFirst());
    }
  }

  private final class MyTableModel extends AbstractTableModel{
    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public String getColumnName(final int column) {
      if(column == 0){
        return UIDesignerBundle.message("column.key");
      }
      else if(column == 1){
        return UIDesignerBundle.message("column.value");
      }
      else{
        throw new IllegalArgumentException("unknown column: " + column);
      }
    }

    @Override
    public Class<?> getColumnClass(final int column) {
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

    @Override
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

    @Override
    public int getRowCount() {
      return myPairs.size();
    }

    public void update() {
      fireTableDataChanged();
    }
  }

  private final class MySpeedSearch extends SpeedSearchBase<JTable> {
    private Object2IntMap<Object> myElements;
    private Object[] myElementsArray;

    private MySpeedSearch(final JTable component) {
      super(component, null);
    }

    @Override
    protected int convertIndexToModel(int viewIndex) {
      return getComponent().convertRowIndexToModel(viewIndex);
    }

    @Override
    public int getSelectedIndex() {
      return myComponent.getSelectedRow();
    }

    @Override
    public Object @NotNull [] getAllElements() {
      if (myElements == null) {
        myElements = new Object2IntOpenHashMap<>();
        myElementsArray = myPairs.toArray();
        for (int idx = 0; idx < myElementsArray.length; idx++) {
          Object element = myElementsArray[idx];
          myElements.put(element, idx);
        }
      }
      return myElementsArray;
    }

    @Override
    public String getElementText(final Object element) {
      //noinspection unchecked
      return ((Couple<String>)element).getFirst();
    }

    @Override
    public void selectElement(final Object element, final String selectedText) {
      final int index = myElements.getInt(element);
      selectElementAt(getComponent().convertRowIndexToView(index));
    }
  }

  private class NewKeyValueAction extends AbstractAction {
    NewKeyValueAction() {
      putValue(Action.NAME, UIDesignerBundle.message("key.chooser.new.property"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      NewKeyDialog dlg = new NewKeyDialog(getWindow());
      if (dlg.showAndGet()) {
        if (!StringEditorDialog.saveCreatedProperty(myBundle, dlg.getName(), dlg.getValue(), myEditor.getPsiFile())) return;

        fillPropertyList();
        myModel.update();
        selectKey(dlg.getName());
      }
    }
  }
}
