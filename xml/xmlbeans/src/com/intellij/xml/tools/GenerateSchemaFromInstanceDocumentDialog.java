// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.tools;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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
final class GenerateSchemaFromInstanceDocumentDialog extends DialogWrapper {
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

  private static final List<String> designTypes = Arrays.asList(
    getLocalElementsGlobalComplexTypes(),
    getLocalElementsTypes(),
    getGlobalElementsLocalTypes()
  );

  static final String STRING_TYPE = "string";
  static final String SMART_TYPE = "smart";
  private static final List<String> simpleContentTypes = Arrays.asList(STRING_TYPE, SMART_TYPE);
  private Runnable myOkAction;

  GenerateSchemaFromInstanceDocumentDialog(Project project, VirtualFile file) {
    super(project, true);

    setTitle(XmlBeansBundle.message("generate.schema.from.instance.document.dialog.title"));

    doInitFor(designTypeText, designType);
    configureComboBox(designType,designTypes);

    doInitFor(detectSimpleContentTypesText, detectSimpleContentTypes);
    configureComboBox(detectSimpleContentTypes, simpleContentTypes);


    doInitFor(detectEnumerationsLimitText, detectEnumerationsLimit);
    detectEnumerationsLimit.setText("10");


    UIUtils.configureBrowseButton(project, generateFromUrl, "xml", XmlBeansBundle.message("select.xml.document.dialog.title"), false);
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
    status.setForeground(JBColor.RED);
  }

  public static void configureComboBox(JComboBox combo, List<String> lastValues) {
    combo.setModel(new DefaultComboBoxModel(ArrayUtilRt.toStringArray(lastValues)));
    if (combo.getItemCount() != 0) {
      combo.setSelectedIndex(0);
      combo.getEditor().selectAll();
    }
  }

  public void setFileUrl(@NlsSafe String url) {
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
        @Override
        public void insertUpdate(DocumentEvent e) {
          validateData();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
          validateData();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
          validateData();
        }
      });
    } else if (component instanceof JComboBox jComboBox) {

      jComboBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          validateData();
        }
      });

      if (jComboBox.isEditable()) {
        jComboBox.getEditor().getEditorComponent().addKeyListener(new KeyAdapter() {
          @Override
          public void keyTyped(KeyEvent e) {
            validateData();
          }
        });
      }
    }
  }

  TextFieldWithBrowseButton getUrl() {
    return generateFromUrl;
  }

  private JLabel getUrlText() {
    return generateFromUrlText;
  }

  private JLabel getStatusTextField() {
    return statusText;
  }

  private JLabel getStatusField() {
    return status;
  }

  @Override
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

  private @InspectionMessage String doValidateWithData() {
    if (! new File(generateFromUrl.getText()).exists()) {
      return XmlBeansBundle.message("instance.document.file.is.not.exist");
    }

    if (!generateFromUrl.getText().endsWith(".xml")) {
      return XmlBeansBundle.message("instance.document.file.should.have.xml.extension");
    }

    try {
      int i = Integer.parseInt(getEnumerationsLimit());
      if (i < 0) return XmlBeansBundle.message("negative.number.validation.problem");
    } catch(NumberFormatException ex) {
      return XmlBeansBundle.message("invalid.number.validation.problem");
    }

    if (getTargetSchemaName() == null || getTargetSchemaName().isEmpty()) {
      return XmlBeansBundle.message("result.schema.file.name.is.empty.validation.problem");
    }
    return null;
  }

  @Override
  protected @NotNull String getHelpId() {
    return "webservices.GenerateSchemaFromInstanceDocument";
  }

  static String getLocalElementsGlobalComplexTypes() {
    return XmlBeansBundle.message("local.elements.global.complex.types.option.name");
  }

  static String getLocalElementsTypes() {
    return XmlBeansBundle.message("local.elements.types.option.name");
  }

  static String getGlobalElementsLocalTypes() {
    return XmlBeansBundle.message("global.elements.local.types.option.name");
  }
}
