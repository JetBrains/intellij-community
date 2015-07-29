/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.javaee;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.Html5SchemaProvider;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;

/**
 * @author Eugene.Kudelevsky
 */
public class DefaultSchemasConfigurable implements Configurable {
  private final Project myProject;
  private JRadioButton myHtml4RadioButton;
  private JRadioButton myHtml5RadioButton;
  private JRadioButton myOtherRadioButton;
  private JPanel myContentPanel;
  private JPanel myOtherDoctypeWrapper;
  private JBRadioButton myXMLSchema10JBRadioButton;
  private JBRadioButton myXMLSchema11JBRadioButton;
  private TextFieldWithAutoCompletion myDoctypeTextField;

  public DefaultSchemasConfigurable(final Project project) {
    myProject = project;
  }

  @NotNull
  private String getDoctype() {
    if (myHtml4RadioButton.isSelected()) {
      return XmlUtil.XHTML4_SCHEMA_LOCATION;
    }
    if (myHtml5RadioButton.isSelected()) {
      return Html5SchemaProvider.getHtml5SchemaLocation();
    }
    return myDoctypeTextField.getText();
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Default XML Schemas";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return "reference.default.schemas";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    final String[] urls = ExternalResourceManager.getInstance().getResourceUrls(null, true);
    myDoctypeTextField = TextFieldWithAutoCompletion.create(myProject, Arrays.asList(urls), null, true, null);
    myOtherDoctypeWrapper.add(myDoctypeTextField);
    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myDoctypeTextField.setEnabled(myOtherRadioButton.isSelected());
      }
    };
    myHtml4RadioButton.addActionListener(listener);
    myHtml5RadioButton.addActionListener(listener);
    myOtherRadioButton.addActionListener(listener);
    return myContentPanel;
  }

  @Override
  public boolean isModified() {
    return !ExternalResourceManagerEx.getInstanceEx().getDefaultHtmlDoctype(myProject).equals(getDoctype()) ||
           ExternalResourceManagerEx.getInstanceEx().getXmlSchemaVersion(myProject) != getSchemaVersion();
  }

  @Override
  public void apply() throws ConfigurationException {
    ExternalResourceManagerEx.getInstanceEx().setDefaultHtmlDoctype(getDoctype(), myProject);
    ExternalResourceManagerEx.getInstanceEx().setXmlSchemaVersion(getSchemaVersion(), myProject);
  }

  private ExternalResourceManagerEx.XMLSchemaVersion getSchemaVersion() {
    return myXMLSchema10JBRadioButton.isSelected()
           ? ExternalResourceManagerEx.XMLSchemaVersion.XMLSchema_1_0
           : ExternalResourceManagerEx.XMLSchemaVersion.XMLSchema_1_1;
  }

  @Override
  public void reset() {
    final String doctype = ExternalResourceManagerEx.getInstanceEx().getDefaultHtmlDoctype(myProject);
    if (doctype.isEmpty() || doctype.equals(XmlUtil.XHTML4_SCHEMA_LOCATION)) {
      myHtml4RadioButton.setSelected(true);
      myDoctypeTextField.setEnabled(false);
    }
    else if (doctype.equals(Html5SchemaProvider.getHtml5SchemaLocation())) {
      myHtml5RadioButton.setSelected(true);
      myDoctypeTextField.setEnabled(false);
    }
    else {
      myOtherRadioButton.setSelected(true);
      myDoctypeTextField.setEnabled(true);
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          try {
            myDoctypeTextField.setText(doctype);
          }
          catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      });
    }
    if (ExternalResourceManagerEx.getInstanceEx().getXmlSchemaVersion(myProject) == ExternalResourceManagerEx.XMLSchemaVersion.XMLSchema_1_0) {
      myXMLSchema10JBRadioButton.setSelected(true);
    }
    else {
      myXMLSchema11JBRadioButton.setSelected(true);
    }
  }

  @Override
  public void disposeUIResources() {
  }
}
