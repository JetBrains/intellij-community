/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.templateLanguages;

import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;

public class TemplateLanguagePanel extends JPanel {
  private JTextField myTemplatesFolder;
  private JPanel myMainPanel;
  private JLabel myTemplatesFolderLabel;
  private JComboBox myTemplateLanguage;
  private boolean myTemplateFolderModified = false;
  private static final String DEFAULT_TEMPLATES_FOLDER = "templates";

  public TemplateLanguagePanel() {
    super(new BorderLayout());
    add(myMainPanel, BorderLayout.CENTER);
    myTemplatesFolderLabel.setLabelFor(myTemplatesFolder);
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.withTreeRootVisible(true);
    descriptor.setShowFileSystemRoots(true);
    List<String> templateConfigurations = TemplatesService.getAllTemplateLanguages();
    for (String configuration : templateConfigurations) {
      if (!configuration.equals(TemplatesService.WEB2PY)) {
        myTemplateLanguage.addItem(configuration);
      }
    }
    myTemplatesFolder.setText(DEFAULT_TEMPLATES_FOLDER);
    myTemplatesFolder.addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        final int dot = myTemplatesFolder.getCaret().getDot();
        final int index = myTemplatesFolder.getText().indexOf(File.separator);
        if (index >= dot) {
          myTemplateFolderModified = true;
        }
      }
    });
  }

  public String getTemplatesFolder() {
    return myTemplatesFolder.getText();
  }

  @Nullable
  public String getTemplateLanguage() {
    final Object selectedItem = myTemplateLanguage.getSelectedItem();
    return selectedItem != null && !selectedItem.toString().equals(TemplatesService.NONE) ? (String)selectedItem : null;
  }

  public void setTemplateLanguage(String language) {
    myTemplateLanguage.setSelectedItem(language);
  }

  public void saveSettings(TemplateSettingsHolder holder) {
    holder.setTemplatesFolder(getTemplatesFolder());
    final Object templateLanguage = getTemplateLanguage();
    holder.setTemplateLanguage((String)templateLanguage);
  }

  public void setTemplatesFolder(@NotNull final String folder) {
    myTemplatesFolder.setText(folder);
  }

  public void locationChanged(@NotNull final String baseLocation) {
    final String templatesFolder = myTemplatesFolder.getText();
    final int index = templatesFolder.indexOf(File.separator);
    final String templateFolderName = index >= 0 ? templatesFolder.substring(index) : File.separator + "templates";
    final String oldBase = index >= 0 ? templatesFolder.substring(0, index) : "";
    if (oldBase.equals(baseLocation)) {
      myTemplateFolderModified = false;
    }
    if (!myTemplateFolderModified) {
      myTemplatesFolder.setText(baseLocation + templateFolderName);
    }
  }

  public Dimension getLabelSize() {
    return new JBLabel("Template language:").getPreferredSize();
  }

  public void registerValidators(final FacetValidatorsManager validatorsManager) {
    myTemplateLanguage.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        validatorsManager.validate();
      }
    });
  }
}
