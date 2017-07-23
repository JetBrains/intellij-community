/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import gnu.trove.TObjectIntHashMap;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class KeyChooserDialog extends DialogWrapper{
  private final PropertiesFile myBundle;
  private final String myBundleName;
  /** List of bundle's pairs*/
  private ArrayList<Couple<String>> myPairs;
  private final JComponent myCenterPanel;
  /** Table with key/value pairs */
  private final JTable myTable;
  @NonNls private static final String NULL = "null";
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
    @NotNull final PropertiesFile bundle,
    @NotNull final String bundleName,
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
    new MySpeedSearch(myTable);
    myCenterPanel = ScrollPaneFactory.createScrollPane(myTable);

    myTable.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0), OK_ACTION);
    myTable.getActionMap().put(OK_ACTION, new AbstractAction() {
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
    if (defaultRenderer instanceof JComponent) {
      final JComponent component = (JComponent)defaultRenderer;
      component.putClientProperty("html.disable", Boolean.TRUE);
    }
    selectKey(keyToPreselect);

    init();
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
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
    Collections.sort(myPairs, new MyPairComparator());
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

  @NotNull
  @Override protected Action[] createLeftSideActions() {
    return new Action[] { new NewKeyValueAction() };
  }

  private void selectElementAt(final int index) {
    myTable.getSelectionModel().setSelectionInterval(index, index);
    myTable.scrollRectToVisible(myTable.getCellRect(index, 0, true));
  }

  @NotNull
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

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

  protected JComponent createCenterPanel() {
    return myCenterPanel;
  }

  private static final class MyPairComparator implements Comparator<Couple<String>>{
    public int compare(final Couple<String> p1, final Couple<String> p2) {
      return p1.getFirst().compareToIgnoreCase(p2.getFirst());
    }
  }

  private final class MyTableModel extends AbstractTableModel{
    public int getColumnCount() {
      return 2;
    }

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

    public void update() {
      fireTableDataChanged();
    }
  }

  private class MySpeedSearch extends SpeedSearchBase<JTable> {
    private TObjectIntHashMap<Object> myElements;
    private Object[] myElementsArray;

    public MySpeedSearch(final JTable component) {
      super(component);
    }

    @Override
    protected int convertIndexToModel(int viewIndex) {
      return getComponent().convertRowIndexToModel(viewIndex);
    }

    public int getSelectedIndex() {
      return myComponent.getSelectedRow();
    }

    public Object[] getAllElements() {
      if (myElements == null) {
        myElements = new TObjectIntHashMap<>();
        myElementsArray = myPairs.toArray();
        for (int idx = 0; idx < myElementsArray.length; idx++) {
          Object element = myElementsArray[idx];
          myElements.put(element, idx);
        }
      }
      return myElementsArray;
    }

    public String getElementText(final Object element) {
      //noinspection unchecked
      return ((Couple<String>)element).getFirst();
    }

    public void selectElement(final Object element, final String selectedText) {
      final int index = myElements.get(element);
      selectElementAt(getComponent().convertRowIndexToView(index));
    }
  }

  private class NewKeyValueAction extends AbstractAction {
    public NewKeyValueAction() {
      putValue(Action.NAME, UIDesignerBundle.message("key.chooser.new.property"));
    }

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
