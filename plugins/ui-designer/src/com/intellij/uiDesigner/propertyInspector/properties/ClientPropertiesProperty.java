/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.components.ServiceManager;
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

/**
 * @author yole
 */
public class ClientPropertiesProperty extends ReadOnlyProperty {
  private final Project myProject;

  public static ClientPropertiesProperty getInstance(Project project) {
    return ServiceManager.getService(project, ClientPropertiesProperty.class);
  }

  private final PropertyRenderer myRenderer = new LabelPropertyRenderer(UIDesignerBundle.message("client.properties.configure"));

  private final PropertyEditor myEditor = new MyPropertyEditor();

  public ClientPropertiesProperty(Project project) {
    super(null, "Client Properties");
    myProject = project;
  }

  @Override
  @NotNull
  public PropertyRenderer getRenderer() {
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
