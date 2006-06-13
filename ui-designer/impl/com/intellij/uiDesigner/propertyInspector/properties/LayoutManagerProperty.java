package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.ComboBoxPropertyEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadLayoutManager;
import com.intellij.uiDesigner.radComponents.LayoutManagerRegistry;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class LayoutManagerProperty extends Property<RadContainer, String> {
  private PropertyRenderer<String> myRenderer = new LabelPropertyRenderer<String>() {
    @Override protected void customize(final String value) {
      setText(LayoutManagerRegistry.getLayoutManagerDisplayName(value));
    }
  };

  private static class LayoutManagerEditor extends ComboBoxPropertyEditor<String> {
    public LayoutManagerEditor() {
      myCbx.setModel(new DefaultComboBoxModel(LayoutManagerRegistry.getLayoutManagerNames()));
      myCbx.setRenderer(new ColoredListCellRenderer() {
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          append(LayoutManagerRegistry.getLayoutManagerDisplayName((String) value), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      });
    }

    public JComponent getComponent(RadComponent component, String value, boolean inplace) {
      myCbx.setSelectedItem(value);
      return myCbx;
    }
  }

  private PropertyEditor<String> myEditor = new LayoutManagerEditor();

  public LayoutManagerProperty() {
    super(null, "Layout Manager");
  }

  public String getValue(RadContainer component) {
    RadContainer container = component;
    while(container != null) {
      final RadLayoutManager layoutManager = container.getLayoutManager();
      if (layoutManager != null) {
        return layoutManager.getName();
      }
      container = container.getParent();
    }
    return UIFormXmlConstants.LAYOUT_INTELLIJ;
  }

  protected void setValueImpl(RadContainer component, String value) throws Exception {
    final RadLayoutManager oldLayout = component.getLayoutManager();
    if (oldLayout != null && Comparing.equal(oldLayout.getName(), value)) {
      return;
    }

    RadLayoutManager newLayoutManager = LayoutManagerRegistry.createLayoutManager(value);
    newLayoutManager.changeContainerLayout(component);
  }

  @NotNull public PropertyRenderer<String> getRenderer() {
    return myRenderer;
  }

  public PropertyEditor<String> getEditor() {
    return myEditor;
  }

  @Override
  public boolean needRefreshPropertyList() {
    return true;
  }
}
