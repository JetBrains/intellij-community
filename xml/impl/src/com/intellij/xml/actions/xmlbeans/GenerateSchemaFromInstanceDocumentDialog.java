/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xml.actions.xmlbeans;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlBundle;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class GenerateSchemaFromInstanceDocumentDialog extends DialogWrapper {
  private JPanel panel;
  private TextFieldWithBrowseButton generateFromUrl;
  private JLabel status;
  private JLabel statusText;
  private JLabel generateFromUrlText;
  private JLabel designTypeText;
  private JTextField detectEnumerationsLimit;
  private ComboBox detectSimpleContentTypes;
  private ComboBox designType;
  private JLabel detectEnumerationsLimitText;
  private JLabel detectSimpleContentTypesText;
  private JLabel resultSchemaFileNameText;
  private JTextField resultSchemaFileName;

  static final String LOCAL_ELEMENTS_GLOBAL_COMPLEX_TYPES = XmlBundle.message("local.elements.global.complex.types.option.name");
  static final String LOCAL_ELEMENTS_TYPES = XmlBundle.message("local.elements.types.option.name");
  static final String GLOBAL_ELEMENTS_LOCAL_TYPES = XmlBundle.message("global.elements.local.types.option.name");

  private static final List<String> designTypes = Arrays.asList(
    LOCAL_ELEMENTS_GLOBAL_COMPLEX_TYPES,
    LOCAL_ELEMENTS_TYPES,
    GLOBAL_ELEMENTS_LOCAL_TYPES
  );

  static final String STRING_TYPE = "string";
  static final String SMART_TYPE = "smart";
  private static final List<String> simpleContentTypes = Arrays.asList(STRING_TYPE, SMART_TYPE);
  private Runnable myOkAction;

  public GenerateSchemaFromInstanceDocumentDialog(Project project, VirtualFile file) {
    super(project, true);

    setTitle(XmlBundle.message("generate.schema.from.instance.document.dialog.title"));

    doInitFor(designTypeText, designType);
    configureComboBox(designType,designTypes);

    doInitFor(detectSimpleContentTypesText, detectSimpleContentTypes);
    configureComboBox(detectSimpleContentTypes, simpleContentTypes);


    doInitFor(detectEnumerationsLimitText, detectEnumerationsLimit);
    detectEnumerationsLimit.setText("10");


    UIUtils.configureBrowseButton(project, generateFromUrl, new String[] {"xml"}, XmlBundle.message("select.xml.document.dialog.title"), false);
    doInitFor(generateFromUrlText, generateFromUrl.getTextField());

    doInitFor(resultSchemaFileNameText, resultSchemaFileName);

    init();
    
    generateFromUrl.setText(file.getPresentableUrl());
    resultSchemaFileName.setText(file.getNameWithoutExtension() + ".xsd");
  }

  private void validateData() {
    String msg = doValidateWithData();
    setOKActionEnabled(msg == null);
    status.setText(msg == null ? "" : msg);
    status.setForeground(Color.RED);
  }

  public static void configureComboBox(JComboBox combo, List<String> lastValues) {
    combo.setModel(new DefaultComboBoxModel(ArrayUtil.toStringArray(lastValues)));
    if (combo.getItemCount() != 0) {
      combo.setSelectedIndex(0);
      combo.getEditor().selectAll();
    }
  }

  public void setFileUrl(String url) {
    generateFromUrl.setText(url);
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    if (myOkAction != null) {
      myOkAction.run();
    }
  }

  public void setOkAction(Runnable okAction) {
    myOkAction = okAction;
  }

  public void doInitFor(JLabel textComponent, JComponent component) {
    textComponent.setLabelFor(component);

    if (component instanceof JTextField) {
      ((JTextField)component).getDocument().addDocumentListener(new DocumentListener() {
        public void insertUpdate(DocumentEvent e) {
          validateData();
        }

        public void removeUpdate(DocumentEvent e) {
          validateData();
        }

        public void changedUpdate(DocumentEvent e) {
          validateData();
        }
      });
    } else if (component instanceof JComboBox) {
      JComboBox jComboBox = ((JComboBox) component);

      jComboBox.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          validateData();
        }
      });

      if (jComboBox.isEditable()) {
        jComboBox.getEditor().getEditorComponent().addKeyListener(new KeyAdapter() {
          public void keyTyped(KeyEvent e) {
            validateData();
          }
        });
      }
    }
  }

  protected TextFieldWithBrowseButton getUrl() {
    return generateFromUrl;
  }

  protected JLabel getUrlText() {
    return generateFromUrlText;
  }

  protected JLabel getStatusTextField() {
    return statusText;
  }

  protected JLabel getStatusField() {
    return status;
  }

  protected JComponent createCenterPanel() {
    return panel;
  }

  String getDesignType() {
    return (String) designType.getSelectedItem();
  }

  String getSimpleContentType() {
    return (String) detectSimpleContentTypes.getSelectedItem();
  }

  String getEnumerationsLimit() {
    return detectEnumerationsLimit.getText();
  }

  public String getTargetSchemaName() {
    return resultSchemaFileName.getText();
  }

  protected String doValidateWithData() {
    if (! new File(generateFromUrl.getText()).exists()) {
      return XmlBundle.message("instance.document.file.is.not.exist");
    }

    try {
      int i = Integer.parseInt(getEnumerationsLimit());
      if (i < 0) return XmlBundle.message("negative.number.validation.problem");
    } catch(NumberFormatException ex) {
      return XmlBundle.message("invalid.number.validation.problem");
    }

    if (getTargetSchemaName() == null || getTargetSchemaName().length() == 0) {
      return XmlBundle.message("result.schema.file.name.is.empty.validation.problem");
    }
    return null;
  }
}
