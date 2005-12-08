package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.RadButtonGroup;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.RadContainer;
import com.intellij.uiDesigner.RadRootContainer;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.ComboBoxPropertyEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;

/**
 * @author yole
 */
public class ButtonGroupProperty extends Property {
  private LabelPropertyRenderer myRenderer = new LabelPropertyRenderer() {
    protected void customize(Object value) {
      RadButtonGroup group = (RadButtonGroup) value;
      setText(group == null ? "" : group.getName());
    }
  };

  private ComboBoxPropertyEditor myEditor = new MyPropertyEditor();

  public ButtonGroupProperty() {
    super(null, "Button Group");
  }

  public Object getValue(RadComponent component) {
    final RadRootContainer rootContainer = getRootContainer(component);
    return rootContainer == null ? null : rootContainer.findGroupForComponent(component);
  }

  @Nullable private static RadRootContainer getRootContainer(final RadComponent component) {
    RadContainer container = component.getParent();
    while(container != null && !(container instanceof RadRootContainer)) {
      container = container.getParent();
    }
    return (RadRootContainer) container;
  }

  protected void setValueImpl(RadComponent component, Object value) throws Exception {
    getRootContainer(component).setGroupForComponent(component, (RadButtonGroup) value);
  }

  @NotNull public PropertyRenderer getRenderer() {
    return myRenderer;
  }

  public PropertyEditor getEditor() {
    return myEditor;
  }

  private static class MyPropertyEditor extends ComboBoxPropertyEditor {
    private RadRootContainer myRootContainer;
    private RadComponent myComponent;

    public MyPropertyEditor() {
      myCbx.setRenderer(new ColoredListCellRenderer() {
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          RadButtonGroup group = (RadButtonGroup) value;
          if (value == null) {
            append("<None>", SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
          else if (group == RadButtonGroup.NEW_GROUP) {
            append("New...", SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
          else {
            append(group.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
        }
      });

      myCbx.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          if (e.getStateChange() == ItemEvent.SELECTED && e.getItem() == RadButtonGroup.NEW_GROUP) {
            String newGroupName = myRootContainer.suggestGroupName();
            JOptionPane.showInputDialog(myCbx, "Enter the name for the group:", "Create Button Group",
                                        JOptionPane.QUESTION_MESSAGE, null, null, newGroupName);
            if (newGroupName != null) {
              RadButtonGroup group = myRootContainer.createGroup(newGroupName);
              myRootContainer.setGroupForComponent(myComponent, group);
              updateModel();
            }
          }
        }
      });
    }


    public JComponent getComponent(RadComponent component, Object value, boolean inplace) {
      myComponent = component;
      myRootContainer = getRootContainer(myComponent);
      updateModel();
      return myCbx;
    }

    private void updateModel() {
      RadButtonGroup[] groups = myRootContainer.getAllGroups();
      RadButtonGroup[] allGroups = new RadButtonGroup[groups.length+2];
      System.arraycopy(groups, 0, allGroups, 1, groups.length);
      allGroups [allGroups.length-1] = RadButtonGroup.NEW_GROUP;
      myCbx.setModel(new DefaultComboBoxModel(allGroups));
      myCbx.setSelectedItem(myRootContainer.findGroupForComponent(myComponent));
    }
  }
}
