// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.JBColor;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.clientProperties.ClientPropertiesManager;
import com.intellij.uiDesigner.clientProperties.ConfigureClientPropertiesDialog;
import com.intellij.uiDesigner.propertyInspector.*;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;


public class ClientPropertiesProperty extends ReadOnlyProperty {
  private final Project myProject;

  public static ClientPropertiesProperty getInstance(Project project) {
    return project.getService(ClientPropertiesProperty.class);
  }

  private final PropertyRenderer myRenderer = new LabelPropertyRenderer(UIDesignerBundle.message("client.properties.configure"));

  private final PropertyEditor myEditor = new MyPropertyEditor();

  public ClientPropertiesProperty(Project project) {
    super(null, "Client Properties");
    myProject = project;
  }

  @Override
  public @NotNull PropertyRenderer getRenderer() {
    return myRenderer;
  }

  @Override
  public PropertyEditor getEditor() {
    return myEditor;
  }

  @Override
  public Property @NotNull [] getChildren(final RadComponent component) {
    if (component == null) {
      return EMPTY_ARRAY;
    }
    ClientPropertiesManager manager = ClientPropertiesManager.getInstance(component.getProject());
    List<ClientPropertiesManager.ClientProperty> props = manager.getClientProperties(component.getComponentClass());
    Property[] result = new Property[props.size()];
    for (int i = 0; i < props.size(); i++) {
      result[i] = new ClientPropertyProperty(this, props.get(i).getName(), props.get(i).getValueClass());
    }
    return result;
  }

  private class MyPropertyEditor extends PropertyEditor {
    private final TextFieldWithBrowseButton myTf = new TextFieldWithBrowseButton();

    MyPropertyEditor() {
      myTf.setText(UIDesignerBundle.message("client.properties.configure"));
      myTf.getTextField().setEditable(false);
      myTf.getTextField().setBorder(null);
      myTf.getTextField().setForeground(JBColor.foreground());
      myTf.addActionListener(new ActionListener() {
        @Override
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

    @Override
    public Object getValue() throws Exception {
      return null;
    }

    @Override
    public JComponent getComponent(final RadComponent component, final Object value, final InplaceContext inplaceContext) {
      return myTf;
    }

    @Override
    public void updateUI() {
      SwingUtilities.updateComponentTreeUI(myTf);
    }
  }

  @Override
  public boolean needRefreshPropertyList() {
    return true;
  }
}
