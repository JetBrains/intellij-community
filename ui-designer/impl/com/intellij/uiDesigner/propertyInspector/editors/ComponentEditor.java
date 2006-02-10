package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.propertyInspector.renderers.ComponentRenderer;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.util.ArrayList;

/**
 * @author yole
 */
public class ComponentEditor extends ComboBoxPropertyEditor {
  private Class myPropertyType;
  private String myOldValue;

  public ComponentEditor(final Class propertyType) {
    myPropertyType = propertyType;
    myCbx.setRenderer(new ComponentRenderer());
  }

  public JComponent getComponent(RadComponent component, Object value, boolean inplace) {
    RadComponent[] components = collectFocusableComponents(component);
    // components [0] = null (<none>)
    myCbx.setModel(new DefaultComboBoxModel(components));
    myOldValue = (String) value;
    if (value == null || myOldValue.length() == 0) {
      myCbx.setSelectedIndex(0);
    }
    else {
      for(int i=1; i<components.length; i++) {
        if (components [i].getId().equals(value)) {
          myCbx.setSelectedIndex(i);
          break;
        }
      }
    }
    return myCbx;
  }

  private RadComponent[] collectFocusableComponents(final RadComponent component) {
    final ArrayList<RadComponent> result = new ArrayList<RadComponent>();
    result.add(null);

    RadContainer container = component.getParent();
    while(container.getParent() != null) {
      container = container.getParent();
    }

    FormEditingUtil.iterate(container, new FormEditingUtil.ComponentVisitor() {
      public boolean visit(final IComponent component) {
        RadComponent radComponent = (RadComponent) component;
        final JComponent delegee = radComponent.getDelegee();
        if (!myPropertyType.isInstance(delegee)) {
          return true;
        }
        if (delegee instanceof JTextComponent || delegee instanceof JComboBox || delegee instanceof JSpinner) {
          result.add(radComponent);
        }
        return true;
      }
    });

    return result.toArray(new RadComponent[result.size()]);
  }

  @Override
  public Object getValue() throws Exception {
    final RadComponent selection = (RadComponent)myCbx.getSelectedItem();
    if (selection == null) {
      return myOldValue == null ? null : "";
    }
    return selection.getId();
  }
}
