// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.propertyInspector.InplaceContext;
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


public class ButtonGroupProperty extends Property<RadComponent, RadButtonGroup> {
  private final LabelPropertyRenderer<RadButtonGroup> myRenderer = new LabelPropertyRenderer<>() {
    @Override
    protected void customize(final @NotNull RadButtonGroup value) {
      setText(value.getName());
    }
  };

  private final ComboBoxPropertyEditor<RadButtonGroup> myEditor = new MyPropertyEditor();

  public ButtonGroupProperty() {
    super(null, "Button Group");
  }

  @Override
  public RadButtonGroup getValue(RadComponent component) {
    final RadRootContainer rootContainer = (RadRootContainer) FormEditingUtil.getRoot(component);
    return rootContainer == null ? null : (RadButtonGroup) FormEditingUtil.findGroupForComponent(rootContainer, component);
  }

  @Override
  protected void setValueImpl(RadComponent component, RadButtonGroup value) throws Exception {
    final RadRootContainer radRootContainer = (RadRootContainer) FormEditingUtil.getRoot(component);
    assert radRootContainer != null;
    radRootContainer.setGroupForComponent(component, value);
  }

  @Override
  public @NotNull PropertyRenderer<RadButtonGroup> getRenderer() {
    return myRenderer;
  }

  @Override
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

    MyPropertyEditor() {
      myCbx.setRenderer(SimpleListCellRenderer.create(UIDesignerBundle.message("button.group.none"), value ->
        value == RadButtonGroup.NEW_GROUP ? UIDesignerBundle.message("button.group.new") : value.getName()));
      myCbx.addItemListener(new ItemListener() {
        @Override
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


    @Override
    public JComponent getComponent(RadComponent component, RadButtonGroup value, InplaceContext inplaceContext) {
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
      myCbx.setModel(new DefaultComboBoxModel<>(allGroups));
      myCbx.setSelectedItem(FormEditingUtil.findGroupForComponent(myRootContainer, myComponent));
    }
  }
}
