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
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.Gray;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.IndentedIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
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

  private final TableCellRenderer myCellRenderer = new PropertyTableCellRenderer();

  @Nullable private EditableArea myArea;

  private boolean myShowExpert;

  public PropertyTable() {
    setModel(myModel);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setEnableAntialiasing(true);
  }

  public void setArea(@Nullable EditableArea area) {
    if (myArea != null) {
      myArea.removeSelectionListener(this);
    }

    myArea = area;

    if (myArea != null) {
      myArea.addSelectionListener(this);
    }

    selectionChanged(area);
  }

  @Override
  public void selectionChanged(EditableArea area) {
    if (isEditing()) {
      cellEditor.stopCellEditing();
    }

    if (myArea == null) {
      myComponents = Collections.emptyList();
      myProperties = Collections.emptyList();
      myModel.fireTableDataChanged();
    }
    else {
      myComponents = new ArrayList<RadComponent>(myArea.getSelection());
      fillProperties();
      myModel.fireTableDataChanged();
    }
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
            final Property property = I.next();
            Property testProperty = ContainerUtil.find(properties, new Condition<Property>() {
              @Override
              public boolean value(Property next) {
                return property.getName().equals(next.getName());
              }
            });

            if (testProperty == null || !property.getClass().equals(testProperty.getClass())) {
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

  public boolean isShowExpert() {
    return myShowExpert;
  }

  public void setShowExpert(boolean showExpert) {
    myShowExpert = showExpert;
    selectionChanged(myArea);
  }

  public TableCellRenderer getCellRenderer(int row, int column) {
    return myCellRenderer;
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

  @Override
  public Object getData(@NonNls String dataId) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
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

    public boolean isCellEditable(final int row, final int column) {
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
  }

  private class PropertyTableCellRenderer implements TableCellRenderer {
    private final ColoredTableCellRenderer myPropertyNameRenderer;
    private final ColoredTableCellRenderer myErrorRenderer;
    private final Icon myExpandIcon;
    private final Icon myCollapseIcon;
    private final Icon myIndentedExpandIcon;
    private final Icon myIndentedCollapseIcon;
    private final Icon[] myIndentIcons = new Icon[3];

    private PropertyTableCellRenderer() {
      myPropertyNameRenderer = new ColoredTableCellRenderer() {
        protected void customizeCellRenderer(
          final JTable table,
          final Object value,
          final boolean selected,
          final boolean hasFocus,
          final int row,
          final int column
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