// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.tools;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.JBColor;
import com.intellij.util.ArrayUtilRt;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public final class GenerateInstanceDocumentFromSchemaDialog extends DialogWrapper {
  private JPanel panel;
  private TextFieldWithBrowseButton generateFromUrl;
  private JLabel status;
  private JLabel statusText;
  private JLabel generateFromUrlText;
  private JComboBox rootElementChooser;
  private JLabel rootElementChooserText;
  private JCheckBox enableRestrictionCheck;
  private JCheckBox enableUniqueCheck;
  private JTextField outputFileName;
  private JLabel outputFileNameText;
  private String previousUri;
  private Runnable myOkAction;
  private final Project myProject;

  @VisibleForTesting
  public GenerateInstanceDocumentFromSchemaDialog(Project project, VirtualFile file) {
    super(project, true);
    myProject = project;

    UIUtils.configureBrowseButton(project, generateFromUrl, "xsd", XmlBundle.message("select.xsd.schema.dialog.title"), false);

    doInitFor(rootElementChooserText, rootElementChooser);
    doInitFor(generateFromUrlText, generateFromUrl.getTextField());
    doInitFor(outputFileNameText, outputFileName);
    generateFromUrl.setText(file.getPresentableUrl());
    updateFile();

    setTitle(XmlBundle.message("generate.instance.document.from.schema.dialog.title"));

    init();

    outputFileName.setText(file.getName() + ".xml");
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
    }
    else if (component instanceof JComboBox jComboBox) {

      jComboBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          validateData();
        }
      });

      ((JTextField)jComboBox.getEditor().getEditorComponent()).getDocument().addDocumentListener(new DocumentListener() {
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

  private void validateData() {
    String msg = doValidateWithData();
    setOKActionEnabled(msg == null);
    status.setText(msg == null ? "" : msg);
    status.setForeground(JBColor.RED);
  }

  public static void configureComboBox(JComboBox combo, List<String> lastValues) {  // without -editor.selectAll- no focus
    combo.setModel(new DefaultComboBoxModel(ArrayUtilRt.toStringArray(lastValues)));
  }

  private void updateFile() {
    String uri = generateFromUrl.getText();
    boolean hasPrevious = (previousUri != null && previousUri.equals(uri));
    final PsiFile psifile = findFile(uri);
    List<String> myRootValues;

    if (psifile == null) {
      configureComboBox(rootElementChooser, Collections.emptyList());
      return;
    }

    final XmlTag rootTag = getRootTag(psifile);

    if (rootTag == null) {
      configureComboBox(rootElementChooser, Collections.emptyList());
      rootElementChooser.setSelectedIndex(-1);
      previousUri = uri;
      return;
    }

    myRootValues = Xsd2InstanceUtils.addVariantsFromRootTag(rootTag);

    Object selectedItem = rootElementChooser.getSelectedItem();
    configureComboBox(rootElementChooser, myRootValues);

    if (hasPrevious) {
      rootElementChooser.setSelectedItem(selectedItem);
    }
    else {
      rootElementChooser.setSelectedIndex(!myRootValues.isEmpty() ? 0 : -1);
    }
    previousUri = uri;
  }

  private static @Nullable XmlTag getRootTag(PsiFile psifile) {
    XmlFile xmlFile = null;
    if (psifile instanceof XmlFile) {
      xmlFile = (XmlFile)psifile;
    }
    else if (psifile.getViewProvider() instanceof TemplateLanguageFileViewProvider viewProvider) {
      if (viewProvider.getPsi(viewProvider.getTemplateDataLanguage()) instanceof XmlFile) {
        xmlFile = (XmlFile)viewProvider.getPsi(viewProvider.getTemplateDataLanguage());
      }
    }

    if (xmlFile != null) {
      return xmlFile.getDocument().getRootTag();
    }
    else {
      return null;
    }
  }

  private @Nullable PsiFile findFile(String uri) {
    final VirtualFile file =
      uri != null ? VfsUtilCore.findRelativeFile(ExternalResourceManager.getInstance().getResourceLocation(uri), null) : null;
    return file != null ? PsiManager.getInstance(myProject).findFile(file) : null;
  }

  public String getOutputFileName() {
    return outputFileName.getText();
  }

  public Boolean areCurrentParametersStillValid() {
    updateFile();
    return rootElementChooser.getSelectedItem() != null;
  }

  private @Nullable @InspectionMessage String doValidateWithData() {
    String rootElementName = getElementName();
    if (rootElementName == null || rootElementName.isEmpty()) {
      return XmlBundle.message("schema2.instance.no.valid.root.element.name.validation.error");
    }

    final PsiFile psiFile = findFile(getUrl().getText());
    if (psiFile instanceof XmlFile) {
      final XmlTag tag = getRootTag(psiFile);
      if (tag != null) {
        final XmlElementDescriptor descriptor = Xsd2InstanceUtils.getDescriptor(tag, rootElementName);

        if (descriptor == null) {
          return XmlBundle.message("schema2.instance.no.valid.root.element.name.validation.error");
        }
      }
    }

    final String fileName = getOutputFileName();
    if (fileName == null || fileName.isEmpty()) {
      return XmlBundle.message("schema2.instance.output.file.name.is.empty.validation.problem");
    }
    return null;

  }

  private static boolean isAcceptableFile(VirtualFile virtualFile) {
    return GenerateInstanceDocumentFromSchemaAction.isAcceptableFileForGenerateSchemaFromInstanceDocument(virtualFile);
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

  boolean enableUniquenessCheck() {
    return enableUniqueCheck.isSelected();
  }

  boolean enableRestrictionCheck() {
    return enableRestrictionCheck.isSelected();
  }

  String getElementName() {
    return (String)rootElementChooser.getSelectedItem();
  }

  public void setOkAction(Runnable runnable) {
    myOkAction = runnable;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    if (myOkAction != null) {
      myOkAction.run();
    }
  }

  @Override
  protected @NotNull String getHelpId() {
    return "webservices.GenerateInstanceDocumentFromSchema";
  }
}
