/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.propertyTable;

import com.intellij.designer.DesignerBundle;
import com.intellij.designer.designSurface.ComponentSelectionListener;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.IndentedIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.plaf.TableUI;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public final class PropertyTable extends JBTable implements ComponentSelectionListener, DataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.designer.propertyTable");

  private final AbstractTableModel myModel = new PropertyTableModel();
  private List<RadComponent> myComponents = Collections.emptyList();
  private List<Property> myProperties = Collections.emptyList();
  private final Set<String> myExpandedProperties = new HashSet<String>();

  private boolean myStoppingEditing;
  private final PropertyCellEditor myCellEditor = new PropertyCellEditor();
  private final PropertyEditorListener myPropertyEditorListener = new PropertyCellEditorListener();

  private final TableCellRenderer myCellRenderer = new PropertyCellRenderer();

  private boolean mySkipUpdate;
  @Nullable private EditableArea myArea;
  @Nullable private DesignerEditorPanel myDesigner;

  private boolean myShowExpert;

  public PropertyTable() {
    setModel(myModel);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    addMouseListener(new MouseTableListener());

    // TODO: ShowJavadocAction

    // TODO: Popup menu

    // TODO: Updates UI after LAF updated
  }

  public void setUI(TableUI ui) {
    super.setUI(ui);

    // Customize action and input maps
    ActionMap actionMap = getActionMap();
    InputMap focusedInputMap = getInputMap(JComponent.WHEN_FOCUSED);
    InputMap ancestorInputMap = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    actionMap.put("selectPreviousRow", new MySelectPreviousRowAction());

    actionMap.put("selectNextRow", new MySelectNextRowAction());

    actionMap.put("startEditing", new MyStartEditingAction());
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "startEditing");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));

    actionMap.put("smartEnter", new MyEnterAction());
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "smartEnter");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));

    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
    ancestorInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");

    actionMap.put("restoreDefault", new MyRestoreDefaultAction());
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "restoreDefault");
    ancestorInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "restoreDefault");

    actionMap.put("expandCurrent", new MyExpandCurrentAction(true));
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0), "expandCurrent");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0));

    actionMap.put("collapseCurrent", new MyExpandCurrentAction(false));
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0), "collapseCurrent");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0));
  }

  public TableCellRenderer getCellRenderer(int row, int column) {
    return myCellRenderer;
  }

  @Override
  public Object getData(@NonNls String dataId) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public void setArea(@Nullable DesignerEditorPanel designer, @Nullable EditableArea area) {
    myDesigner = designer;

    finishEditing();

    if (myArea != null) {
      myArea.removeSelectionListener(this);
    }

    myArea = area;

    if (myArea != null) {
      myArea.addSelectionListener(this);
    }

    updateProperties();
  }

  @Override
  public void selectionChanged(EditableArea area) {
    updateProperties();
  }

  public boolean isShowExpert() {
    return myShowExpert;
  }

  public void setShowExpert(boolean showExpert) {
    myShowExpert = showExpert;
    updateProperties();
  }

  public void restoreDefaultValue() {
    final Property property = getSelectionProperty();
    if (property != null) {
      if (isEditing()) {
        cellEditor.stopCellEditing();
      }

      CommandProcessor.getInstance().executeCommand(myDesigner.getProject(), new Runnable() {
        public void run() {
          myDesigner.getToolProvider().execute(new ThrowableRunnable<Exception>() {
            @Override
            public void run() throws Exception {
              for (RadComponent component : myComponents) {
                if (!property.isDefaultValue(component)) {
                  property.setDefaultValue(component);
                }
              }
            }
          });
        }
      }, DesignerBundle.message("designer.properties.restore_default"), null);

      repaint();
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private void updateProperties() {
    if (mySkipUpdate) {
      return;
    }
    mySkipUpdate = true;

    try {
      if (isEditing()) {
        cellEditor.stopCellEditing();
      }

      if (myArea == null) {
        myComponents = Collections.emptyList();
        myProperties = Collections.emptyList();
        myModel.fireTableDataChanged();
      }
      else {
        Property selection = getSelectionProperty();

        myComponents = new ArrayList<RadComponent>(myArea.getSelection());
        fillProperties();
        myModel.fireTableDataChanged();

        restoreSelection(selection);
      }
    }
    finally {
      mySkipUpdate = false;
    }
  }

  private void restoreSelection(Property selection) {
    List<Property> propertyPath = new ArrayList<Property>(2);
    while (selection != null) {
      propertyPath.add(0, selection);
      selection = selection.getParent();
    }

    int indexToSelect = -1;
    int size = propertyPath.size();
    for (int i = 0; i < size; i++) {
      int index = findProperty(myProperties, propertyPath.get(i));
      if (index == -1) {
        break;
      }
      if (i == size - 1) {
        indexToSelect = index;
      }
      else {
        expand(index);
      }
    }

    if (indexToSelect != -1) {
      getSelectionModel().setSelectionInterval(indexToSelect, indexToSelect);
    }
    else if (getRowCount() > 0) {
      getSelectionModel().setSelectionInterval(0, 0);
    }
    TableUtil.scrollSelectionToVisible(this);
  }

  private void fillProperties() {
    myProperties = new ArrayList<Property>();
    int size = myComponents.size();

    if (size > 0) {
      fillProperties(myComponents.get(0), myProperties);

      if (size > 1) {
        for (Iterator<Property> I = myProperties.iterator(); I.hasNext(); ) {
          if (!I.next().availableFor(myComponents)) {
            I.remove();
          }
        }

        for (int i = 1; i < size; i++) {
          List<Property> properties = new ArrayList<Property>();
          fillProperties(myComponents.get(i), properties);

          for (Iterator<Property> I = myProperties.iterator(); I.hasNext(); ) {
            Property property = I.next();

            int index = findProperty(properties, property);
            if (index == -1) {
              I.remove();
              continue;
            }

            Property testProperty = properties.get(index);
            if (!property.getClass().equals(testProperty.getClass())) {
              I.remove();
              continue;
            }

            List<Property> children = getChildren(property);
            List<Property> testChildren = getChildren(testProperty);
            int pSize = children.size();

            if (pSize != testChildren.size()) {
              I.remove();
              continue;
            }

            for (int j = 0; j < pSize; j++) {
              if (!children.get(j).getName().equals(testChildren.get(j).getName())) {
                I.remove();
                break;
              }
            }
          }
        }
      }
    }
  }

  private void fillProperties(RadComponent component, List<Property> properties) {
    for (Property property : component.getProperties()) {
      addProperty(property, properties);
    }
  }

  private void addProperty(Property property, List<Property> properties) {
    if (property.isExpert() && !myShowExpert) {
      return;
    }

    properties.add(property);

    if (isExpanded(property)) {
      for (Property child : getChildren(property)) {
        addProperty(child, properties);
      }
    }
  }

  public static int findProperty(List<Property> properties, Property property) {
    String name = property.getName();
    int size = properties.size();

    for (int i = 0; i < size; i++) {
      if (name.equals(properties.get(i).getName())) {
        return i;
      }
    }

    return -1;
  }

  @Nullable
  private Property getSelectionProperty() {
    int selectedRow = getSelectedRow();
    if (selectedRow >= 0 && selectedRow < myProperties.size()) {
      return myProperties.get(selectedRow);
    }
    return null;
  }

  @Nullable
  private RadComponent getCurrentComponent() {
    return myComponents.size() == 1 ? myComponents.get(0) : null;
  }

  private List<Property> getChildren(Property property) {
    return property.getChildren(getCurrentComponent());
  }

  private boolean isDefault(Property property) throws Exception {
    for (RadComponent component : myComponents) {
      if (!property.isDefaultValue(component)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  private Object getValue(Property property) throws Exception {
    int size = myComponents.size();
    if (size == 0) {
      return null;
    }

    Object value = property.getValue(myComponents.get(0));
    for (int i = 1; i < size; i++) {
      if (!Comparing.equal(value, property.getValue(myComponents.get(i)))) {
        return null;
      }
    }

    return value;
  }

  private boolean isExpanded(Property property) {
    return myExpandedProperties.contains(property.getPath());
  }

  private void collapse(int rowIndex) {
    int selectedRow = getSelectedRow();
    Property property = myProperties.get(rowIndex);

    LOG.assertTrue(myExpandedProperties.remove(property.getPath()));
    int size = getChildren(property).size();
    for (int i = 0; i < size; i++) {
      myProperties.remove(rowIndex + 1);
    }
    myModel.fireTableDataChanged();

    if (selectedRow != -1) {
      if (selectedRow > rowIndex) {
        selectedRow -= size;
      }

      getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
    }
  }

  private void expand(int rowIndex) {
    int selectedRow = getSelectedRow();
    Property property = myProperties.get(rowIndex);
    String path = property.getPath();

    if (myExpandedProperties.contains(path)) {
      return;
    }

    myExpandedProperties.add(path);
    List<Property> properties = getChildren(property);
    myProperties.addAll(rowIndex + 1, properties);
    myModel.fireTableDataChanged();

    if (selectedRow != -1) {
      if (selectedRow > rowIndex) {
        selectedRow += properties.size();
      }

      getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public void setValueAt(Object aValue, int row, int column) {
    Property property = myProperties.get(row);
    super.setValueAt(aValue, row, column);

    if (property.needRefreshPropertyList()) {
      updateProperties();
    }

    repaint();
  }

  @Override
  public TableCellEditor getCellEditor(int row, int column) {
    PropertyEditor editor = myProperties.get(row).getEditor();
    editor.removePropertyEditorListener(myPropertyEditorListener); // reorder listener (first)
    editor.addPropertyEditorListener(myPropertyEditorListener);
    myCellEditor.setEditor(editor);
    return myCellEditor;
  }

  /*
  * This method is overriden due to bug in the JTree. The problem is that
  * JTree does not properly repaint edited cell if the editor is opaque or
  * has opaque child components.
  */
  public boolean editCellAt(int row, int column, EventObject e) {
    boolean result = super.editCellAt(row, column, e);
    repaint(getCellRect(row, column, true));
    return result;
  }

  private void startEditing(int index) {
    PropertyEditor editor = myProperties.get(index).getEditor();
    if (editor == null) {
      return;
    }

    editCellAt(index, convertColumnIndexToView(1));
    LOG.assertTrue(editorComp != null);

    JComponent preferredComponent = editor.getPreferredFocusedComponent((JComponent)editorComp);
    if (preferredComponent == null) {
      preferredComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent((JComponent)editorComp);
    }
    if (preferredComponent != null) {
      preferredComponent.requestFocusInWindow();
    }
  }

  private void finishEditing() {
    if (editingRow != -1) {
      editingStopped(null);
    }
  }

  public void editingStopped(@Nullable ChangeEvent event) {
    if (myStoppingEditing) {
      return;
    }
    myStoppingEditing = true;

    LOG.assertTrue(isEditing());
    LOG.assertTrue(editingRow != -1);

    PropertyEditor editor = myProperties.get(editingRow).getEditor();
    editor.removePropertyEditorListener(myPropertyEditorListener);

    try {
      setValueAt(editor.getValue(), editingRow, editingColumn);
    }
    catch (Exception e) {
      showInvalidInput(e);
    }
    finally {
      removeEditor();
      myStoppingEditing = false;
    }
  }

  private boolean setValueAtRow(int row, final Object newValue) {
    final Property property = myProperties.get(row);

    boolean isNewValue;
    try {
      isNewValue = !Comparing.equal(getValue(property), newValue);
    }
    catch (Throwable e) {
      isNewValue = true;
    }

    final boolean[] isSet = {true};

    if (isNewValue) {
      CommandProcessor.getInstance().executeCommand(myDesigner.getProject(), new Runnable() {
        public void run() {
          isSet[0] = myDesigner.getToolProvider().execute(new ThrowableRunnable<Exception>() {
            @Override
            public void run() throws Exception {
              for (RadComponent component : myComponents) {
                property.setValue(component, newValue);
              }
            }
          });
        }
      }, DesignerBundle.message("command.set.property.value"), null);
    }

    if (property.needRefreshPropertyList() && isSet[0]) {
      updateProperties();
    }

    return isSet[0];
  }

  private static void showInvalidInput(Exception e) {
    Throwable cause = e.getCause();
    String message = cause == null ? e.getMessage() : cause.getMessage();

    if (cause == null) {
      e.printStackTrace();
    }
    else {
      cause.printStackTrace();
    }

    if (message == null || message.length() == 0) {
      message = DesignerBundle.message("designer.properties.no_message.error");
    }

    Messages.showMessageDialog(DesignerBundle.message("designer.properties.setting.error", message),
                               DesignerBundle.message("designer.properties.invalid_input"),
                               Messages.getErrorIcon());
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Reimplementation of LookAndFeel's SelectPreviousRowAction action.
   * Standard implementation isn't smart enough.
   *
   * @see javax.swing.plaf.basic.BasicTableUI
   */
  private class MySelectPreviousRowAction extends AbstractAction {
    public void actionPerformed(ActionEvent e) {
      int rowCount = getRowCount();
      LOG.assertTrue(rowCount > 0);
      int selectedRow = getSelectedRow();
      if (selectedRow != -1) {
        selectedRow -= 1;
      }
      selectedRow = (selectedRow + rowCount) % rowCount;
      if (isEditing()) {
        finishEditing();
        getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
        startEditing(selectedRow);
      }
      else {
        getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
      }
    }
  }

  /**
   * Reimplementation of LookAndFeel's SelectNextRowAction action.
   * Standard implementation isn't smart enough.
   *
   * @see javax.swing.plaf.basic.BasicTableUI
   */
  private class MySelectNextRowAction extends AbstractAction {
    public void actionPerformed(ActionEvent e) {
      int rowCount = getRowCount();
      LOG.assertTrue(rowCount > 0);
      int selectedRow = (getSelectedRow() + 1) % rowCount;
      if (isEditing()) {
        finishEditing();
        getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
        startEditing(selectedRow);
      }
      else {
        getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
      }
    }
  }

  /**
   * Reimplementation of LookAndFeel's StartEditingAction action.
   * Standard implementation isn't smart enough.
   *
   * @see javax.swing.plaf.basic.BasicTableUI
   */
  private class MyStartEditingAction extends AbstractAction {
    public void actionPerformed(ActionEvent e) {
      int selectedRow = getSelectedRow();
      if (selectedRow == -1 || isEditing()) {
        return;
      }

      startEditing(selectedRow);
    }
  }

  private class MyEnterAction extends AbstractAction {
    public void actionPerformed(ActionEvent e) {
      int selectedRow = getSelectedRow();
      if (isEditing() || selectedRow == -1) {
        return;
      }

      Property property = myProperties.get(selectedRow);
      if (!getChildren(property).isEmpty()) {
        if (isExpanded(property)) {
          collapse(selectedRow);
        }
        else {
          expand(selectedRow);
        }
      }
      else {
        startEditing(selectedRow);
      }
    }
  }

  private class MyExpandCurrentAction extends AbstractAction {
    private final boolean myExpand;

    public MyExpandCurrentAction(boolean expand) {
      myExpand = expand;
    }

    public void actionPerformed(ActionEvent e) {
      int selectedRow = getSelectedRow();
      if (isEditing() || selectedRow == -1) {
        return;
      }

      Property property = myProperties.get(selectedRow);
      if (!getChildren(property).isEmpty()) {
        if (myExpand) {
          if (!isExpanded(property)) {
            expand(selectedRow);
          }
        }
        else {
          if (isExpanded(property)) {
            collapse(selectedRow);
          }
        }
      }
    }
  }

  private class MyRestoreDefaultAction extends AbstractAction {
    @Override
    public void actionPerformed(ActionEvent e) {
      restoreDefaultValue();
    }
  }

  private class MouseTableListener extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent e) {
      int row = rowAtPoint(e.getPoint());
      if (row == -1) {
        return;
      }

      Property property = myProperties.get(row);

      Rectangle rect = getCellRect(row, convertColumnIndexToView(0), false);
      int indent = property.getIndent() * 11;
      if (e.getX() < rect.x + indent || e.getX() > rect.x + 9 + indent || e.getY() < rect.y || e.getY() > rect.y + rect.height) {
        return;
      }

      if (!getChildren(property).isEmpty()) {
        // TODO: disallow selection for this row
        if (isExpanded(property)) {
          collapse(row);
        }
        else {
          expand(row);
        }
      }
    }
  }

  private class PropertyTableModel extends AbstractTableModel {
    private final String[] myColumnNames =
      {DesignerBundle.message("designer.properties.column1"), DesignerBundle.message("designer.properties.column2")};

    @Override
    public int getColumnCount() {
      return myColumnNames.length;
    }

    @Override
    public String getColumnName(int column) {
      return myColumnNames[column];
    }

    public boolean isCellEditable(int row, int column) {
      return column == 1 && myProperties.get(row).getEditor() != null;
    }

    @Override
    public int getRowCount() {
      return myProperties.size();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      return myProperties.get(rowIndex);
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      setValueAtRow(rowIndex, aValue);
    }
  }

  private class PropertyCellEditorListener implements PropertyEditorListener {
    @Override
    public void valueCommitted(PropertyEditor source, boolean continueEditing, boolean closeEditorOnError) {
      if (isEditing()) {
        Object value;
        TableCellEditor tableCellEditor = cellEditor;

        try {
          value = tableCellEditor.getCellEditorValue();
        }
        catch (Exception e) {
          showInvalidInput(e);
          return;
        }

        if (setValueAtRow(editingRow, value)) {
          if (!continueEditing) {
            tableCellEditor.stopCellEditing();
          }
        }
        else if (closeEditorOnError) {
          tableCellEditor.cancelCellEditing();
        }
      }
    }

    @Override
    public void editingCanceled(PropertyEditor source) {
      if (isEditing()) {
        cellEditor.cancelCellEditing();
      }
    }

    @Override
    public void preferredSizeChanged(PropertyEditor source) {
    }
  }

  private class PropertyCellEditor extends AbstractCellEditor implements TableCellEditor {
    private PropertyEditor myEditor;

    public void setEditor(PropertyEditor editor) {
      myEditor = editor;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      try {
        JComponent component = myEditor.getComponent(getCurrentComponent(), getValue((Property)value));

        if (component instanceof JComboBox) {
          component.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);
          component.putClientProperty("tableCellEditor", this);
        }
        else if (component instanceof JCheckBox) {
          component.putClientProperty("JComponent.sizeVariant", UIUtil.isUnderAquaLookAndFeel() ? "small" : null);
        }

        return component;
      }
      catch (Throwable e) {
        LOG.debug(e);
        SimpleColoredComponent errComponent = new SimpleColoredComponent();
        errComponent
          .append(DesignerBundle.message("designer.properties.getting.error", e.getMessage()), SimpleTextAttributes.ERROR_ATTRIBUTES);
        return errComponent;
      }
    }

    @Override
    public Object getCellEditorValue() {
      try {
        return myEditor.getValue();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  private class PropertyCellRenderer implements TableCellRenderer {
    private final ColoredTableCellRenderer myPropertyNameRenderer;
    private final ColoredTableCellRenderer myErrorRenderer;
    private final Icon myExpandIcon;
    private final Icon myCollapseIcon;
    private final Icon myIndentedExpandIcon;
    private final Icon myIndentedCollapseIcon;
    private final Icon[] myIndentIcons = new Icon[3];

    private PropertyCellRenderer() {
      myPropertyNameRenderer = new ColoredTableCellRenderer() {
        protected void customizeCellRenderer(
          JTable table,
          Object value,
          boolean selected,
          boolean hasFocus,
          int row,
          int column
        ) {
          setPaintFocusBorder(false);
          setFocusBorderAroundIcon(true);
        }
      };

      myErrorRenderer = new ColoredTableCellRenderer() {
        protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
          setPaintFocusBorder(false);
        }
      };

      myExpandIcon = IconLoader.getIcon("/com/intellij/uiDesigner/icons/expandNode.png");
      myCollapseIcon = IconLoader.getIcon("/com/intellij/uiDesigner/icons/collapseNode.png");
      for (int i = 0; i < myIndentIcons.length; i++) {
        myIndentIcons[i] = new EmptyIcon(9 + 11 * i, 9);
      }
      myIndentedExpandIcon = new IndentedIcon(myExpandIcon, 11);
      myIndentedCollapseIcon = new IndentedIcon(myCollapseIcon, 11);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      myPropertyNameRenderer.getTableCellRendererComponent(table, value, selected, hasFocus, row, column);

      column = table.convertColumnIndexToModel(column);
      Property property = (Property)value;
      Color background = table.getBackground();

      try {
        if (isDefault(property)) {
          background = Gray._240;
        }
      }
      catch (Throwable e) {
        LOG.debug(e);
      }

      if (!selected) {
        myPropertyNameRenderer.setBackground(background);
      }

      if (column == 0) {
        SimpleTextAttributes attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
        if (property.isImportant()) {
          attributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
        }
        else if (property.isExpert()) {
          attributes = SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES;
        }
        if (property.isDeprecated()) {
          attributes = new SimpleTextAttributes(attributes.getBgColor(), attributes.getFgColor(), attributes.getWaveColor(),
                                                attributes.getStyle() | SimpleTextAttributes.STYLE_STRIKEOUT);
        }

        myPropertyNameRenderer.append(property.getName(), attributes);

        if (!getChildren(property).isEmpty()) {
          if (property.getParent() == null) {
            if (isExpanded(property)) {
              myPropertyNameRenderer.setIcon(myCollapseIcon);
            }
            else {
              myPropertyNameRenderer.setIcon(myExpandIcon);
            }
          }
          else {
            if (isExpanded(property)) {
              myPropertyNameRenderer.setIcon(myIndentedCollapseIcon);
            }
            else {
              myPropertyNameRenderer.setIcon(myIndentedExpandIcon);
            }
          }
        }
        else {
          myPropertyNameRenderer.setIcon(myIndentIcons[property.getIndent()]);
        }

        if (!selected) {
          myPropertyNameRenderer.setForeground(property.isExpert() ? Color.LIGHT_GRAY : table.getForeground());
        }
      }
      else {
        try {
          PropertyRenderer renderer = property.getRenderer();
          JComponent component = renderer.getComponent(getCurrentComponent(), getValue(property), selected, hasFocus);

          if (!selected) {
            component.setBackground(background);
          }

          component.setFont(table.getFont());

          if (component instanceof JCheckBox) {
            component.putClientProperty("JComponent.sizeVariant", UIUtil.isUnderAquaLookAndFeel() ? "small" : null);
          }

          return component;
        }
        catch (Throwable e) {
          LOG.debug(e);
          myErrorRenderer.clear();
          myErrorRenderer
            .append(DesignerBundle.message("designer.properties.getting.error", e.getMessage()), SimpleTextAttributes.ERROR_ATTRIBUTES);
          return myErrorRenderer;
        }
      }

      return myPropertyNameRenderer;
    }
  }
}