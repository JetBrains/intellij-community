package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.ComboBoxPropertyEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadButtonGroup;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author yole
 */
public class ButtonGroupProperty extends Property<RadComponent, RadButtonGroup> {
  private LabelPropertyRenderer<RadButtonGroup> myRenderer = new LabelPropertyRenderer<RadButtonGroup>() {
    @Override protected void customize(@NotNull final RadButtonGroup value) {
      setText(value.getName());
    }
  };

  private ComboBoxPropertyEditor<RadButtonGroup> myEditor = new MyPropertyEditor();

  public ButtonGroupProperty() {
    super(null, "Button Group");
  }

  public RadButtonGroup getValue(RadComponent component) {
    final RadRootContainer rootContainer = (RadRootContainer) FormEditingUtil.getRoot(component);
    return rootContainer == null ? null : (RadButtonGroup) FormEditingUtil.findGroupForComponent(rootContainer, component);
  }

  protected void setValueImpl(RadComponent component, RadButtonGroup value) throws Exception {
    final RadRootContainer radRootContainer = (RadRootContainer) FormEditingUtil.getRoot(component);
    assert radRootContainer != null;
    radRootContainer.setGroupForComponent(component, value);
  }

  @NotNull public PropertyRenderer<RadButtonGroup> getRenderer() {
    return myRenderer;
  }

  public PropertyEditor<RadButtonGroup> getEditor() {
    return myEditor;
  }

  @Override public boolean isModified(final RadComponent component) {
    return getValue(component) != null;
  }

  @Override public void resetValue(RadComponent component) throws Exception {
    setValueImpl(component, null);
  }

  private static class MyPropertyEditor extends ComboBoxPropertyEditor<RadButtonGroup> {
    private RadRootContainer myRootContainer;
    private RadComponent myComponent;

    public MyPropertyEditor() {
      myCbx.setRenderer(new ColoredListCellRenderer() {
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          RadButtonGroup group = (RadButtonGroup) value;
          if (value == null) {
            append(UIDesignerBundle.message("button.group.none"), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
          else if (group == RadButtonGroup.NEW_GROUP) {
            append(UIDesignerBundle.message("button.group.new"), SimpleTextAttributes.REGULAR_ATTRIBUTES);
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
            newGroupName = (String)JOptionPane.showInputDialog(myCbx,
                                                               UIDesignerBundle.message("button.group.name.prompt"),
                                                               UIDesignerBundle.message("button.group.name.title"),
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


    public JComponent getComponent(RadComponent component, RadButtonGroup value, boolean inplace) {
      myComponent = component;
      myRootContainer = (RadRootContainer) FormEditingUtil.getRoot(myComponent);
      updateModel();
      return myCbx;
    }

    private void updateModel() {
      RadButtonGroup[] groups = myRootContainer.getButtonGroups();
      RadButtonGroup[] allGroups = new RadButtonGroup[groups.length+2];
      System.arraycopy(groups, 0, allGroups, 1, groups.length);
      allGroups [allGroups.length-1] = RadButtonGroup.NEW_GROUP;
      myCbx.setModel(new DefaultComboBoxModel(allGroups));
      myCbx.setSelectedItem(FormEditingUtil.findGroupForComponent(myRootContainer, myComponent));
    }
  }
}
