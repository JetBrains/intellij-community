/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.uiDesigner.clientProperties.ClientPropertiesManager;
import com.intellij.uiDesigner.clientProperties.ConfigureClientPropertiesDialog;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.ReadOnlyProperty;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author yole
 */
public class ClientPropertiesProperty extends ReadOnlyProperty {
  private Project myProject;

  public static ClientPropertiesProperty getInstance(Project project) {
    return project.getComponent(ClientPropertiesProperty.class);
  }

  private PropertyRenderer myRenderer = new LabelPropertyRenderer(UIDesignerBundle.message("client.properties.configure"));

  private PropertyEditor myEditor = new MyPropertyEditor();

  public ClientPropertiesProperty(Project project) {
    super(null, "Client Properties");
    myProject = project;
  }

  @NotNull
  public PropertyRenderer getRenderer() {
    return myRenderer;
  }

  public PropertyEditor getEditor() {
    return myEditor;
  }

  @NotNull @Override
  public Property[] getChildren(final RadComponent component) {
    ClientPropertiesManager manager = ClientPropertiesManager.getInstance(component.getProject());
    ClientPropertiesManager.ClientProperty[] props = manager.getClientProperties(component.getComponentClass());
    Property[] result = new Property[props.length];
    for(int i=0; i<props.length; i++) {
      result [i] = new ClientPropertyProperty(this, props [i].getName(), props [i].getValueClass());
    }
    return result;
  }

  private class MyPropertyEditor extends PropertyEditor {
    private TextFieldWithBrowseButton myTf = new TextFieldWithBrowseButton();

    public MyPropertyEditor() {
      myTf.setText(UIDesignerBundle.message("client.properties.configure"));
      myTf.getTextField().setEditable(false);
      myTf.getTextField().setBorder(null);
      myTf.getTextField().setForeground(Color.BLACK);
      myTf.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          showClientPropertiesDialog();
        }
      });
    }

    private void showClientPropertiesDialog() {
      ConfigureClientPropertiesDialog dlg = new ConfigureClientPropertiesDialog(myProject);
      dlg.show();
      if (dlg.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
        dlg.save();
        fireValueCommitted(true, false);
      }
    }

    public Object getValue() throws Exception {
      return null;
    }

    public JComponent getComponent(final RadComponent component, final Object value, final boolean inplace) {
      return myTf;
    }

    public void updateUI() {
      SwingUtilities.updateComponentTreeUI(myTf);
    }
  }

  @Override
  public boolean needRefreshPropertyList() {
    return true;
  }
}
