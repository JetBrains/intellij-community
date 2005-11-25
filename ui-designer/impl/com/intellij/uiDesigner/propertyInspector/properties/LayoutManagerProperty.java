package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.RadRootContainer;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.ComboBoxPropertyEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 25.11.2005
 * Time: 13:42:15
 * To change this template use File | Settings | File Templates.
 */
public class LayoutManagerProperty extends Property {
  private PropertyRenderer myRenderer = new LabelPropertyRenderer() {
    protected void customize(Object value) {
      setText((String) value);
    }
  };

  private static class LayoutManagerEditor extends ComboBoxPropertyEditor {
    public LayoutManagerEditor() {
      myCbx.setModel(new DefaultComboBoxModel(new String[] { "GridLayoutManager", "GridBagLayout" } ));
    }

    public JComponent getComponent(RadComponent component, Object value, boolean inplace) {
      myCbx.setSelectedItem(value);
      return myCbx;
    }
  }

  private PropertyEditor myEditor = new LayoutManagerEditor();

  public LayoutManagerProperty() {
    super(null, "Layout Manager");
  }

  public Object getValue(RadComponent component) {
    final String layoutManager = ((RadRootContainer)component).getLayoutManager();
    return layoutManager == null ? "GridLayoutManager" : layoutManager;
  }

  protected void setValueImpl(RadComponent component, Object value) throws Exception {
    ((RadRootContainer) component).setLayoutManager((String) value);
  }

  public Property[] getChildren() {
    return EMPTY_ARRAY;
  }

  public PropertyRenderer getRenderer() {
    return myRenderer;
  }

  public PropertyEditor getEditor() {
    return myEditor;
  }
}
