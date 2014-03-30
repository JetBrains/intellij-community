/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
    private final TextFieldWithBrowseButton myTf = new TextFieldWithBrowseButton();

    public MyPropertyEditor() {
      myTf.setText(UIDesignerBundle.message("client.properties.configure"));
      myTf.getTextField().setEditable(false);
      myTf.getTextField().setBorder(null);
      myTf.getTextField().setForeground(JBColor.foreground());
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

    public JComponent getComponent(final RadComponent component, final Object value, final InplaceContext inplaceContext) {
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
