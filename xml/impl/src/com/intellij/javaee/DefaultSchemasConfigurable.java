// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.Html5SchemaProvider;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;

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

  private @NotNull String getDoctype() {
    if (myHtml4RadioButton.isSelected()) {
      return XmlUtil.XHTML4_SCHEMA_LOCATION;
    }
    if (myHtml5RadioButton.isSelected()) {
      return Html5SchemaProvider.getHtml5SchemaLocation();
    }
    return myDoctypeTextField.getText();
  }

  @Override
  public @Nls String getDisplayName() {
    return XmlBundle.message("configurable.DefaultSchemasConfigurable.display.name");
  }

  @Override
  public @Nullable String getHelpTopic() {
    return "reference.default.schemas";
  }

  @Override
  public @Nullable JComponent createComponent() {
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

    if (UIUtil.isUnderWin10LookAndFeel()) {
      myOtherRadioButton.setBorder(JBUI.Borders.empty());
    }

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
      UIUtil.invokeLaterIfNeeded(() -> {
        try {
          myDoctypeTextField.setText(doctype);
        }
        catch (Exception e) {
          throw new RuntimeException(e);
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
}
