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
package com.jetbrains.python.buildout;

import com.intellij.facet.impl.ui.FacetErrorPanel;
import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * Panel to choose target buildout script
 * User: dcheryasov
 */
public class BuildoutConfigPanel extends JPanel {
  private ComboboxWithBrowseButton myScript;
  private JPanel myPanel;
  private JTextArea myNoticeTextArea;
  private JPanel myErrorPanel;
  private final Module myModule;
  private boolean myFacetEnabled = true;
  private BuildoutFacetConfiguration myConfiguration;

  public BuildoutConfigPanel(Module module, BuildoutFacetConfiguration config) {
    myModule = module;
    myConfiguration = config;
    setLayout(new BorderLayout());
    add(myPanel);

    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
    //descriptor.setRoot(myConfiguration.getRoot());
    myScript.addBrowseFolderListener("Choose a buildout script", "Select the target script that will invoke your code", null, descriptor,
                                     TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT);
    myScript.getComboBox().setEditable(true);

    initErrorValidation();
  }

  public void setFacetEnabled(boolean facetEnabled) {
    if (myFacetEnabled != facetEnabled) {
      myFacetEnabled = facetEnabled;
      initErrorValidation();
    }
  }

  private void initErrorValidation() {
    FacetErrorPanel facetErrorPanel = new FacetErrorPanel();
    myErrorPanel.add(facetErrorPanel.getComponent(), BorderLayout.CENTER);

    facetErrorPanel.getValidatorsManager().registerValidator(new FacetEditorValidator() {
      @NotNull
      @Override
      public ValidationResult check() {
        if (!myFacetEnabled) {
          return ValidationResult.OK;
        }
        return validateScriptName(getScriptName());
      }
    }, myScript);

    facetErrorPanel.getValidatorsManager().validate();
  }

  private static ValidationResult validateScriptName(String scriptName) {
    if (StringUtil.isEmpty(scriptName)) {
      return new ValidationResult("Please specify buildout script");
    }
    try {
      getScriptFile(scriptName);
    }
    catch (ConfigurationException e) {
      return new ValidationResult(e.getMessage());
    }
    return ValidationResult.OK;
  }

  public boolean isModified(BuildoutFacetConfiguration configuration) {
    final String their = configuration.getScriptName();
    final String ours = getScriptName();
    return !Comparing.strEqual(their, ours);
  }

  public String getScriptName() {
    return (String)myScript.getComboBox().getEditor().getItem();
  }

  public void reset() {
    final List<File> scriptFiles = BuildoutFacet.getScripts(BuildoutFacet.getInstance(myModule), myModule.getProject().getBaseDir());
    final List<String> scripts = ContainerUtil.map(scriptFiles, file -> file.getPath());
    myScript.getComboBox().setModel(new CollectionComboBoxModel(scripts, myConfiguration.getScriptName()));
    myScript.getComboBox().getEditor().setItem(myConfiguration.getScriptName());
  }

  public void apply() {
    final String scriptName = getScriptName();
    myConfiguration.setScriptName(scriptName);
  }

  BuildoutFacetConfiguration getConfiguration() {
    return myConfiguration;
  }

  void showNoticeText(boolean show) {
    myNoticeTextArea.setVisible(show);
  }

  @NotNull
  public static VirtualFile getScriptFile(String script_name) throws ConfigurationException {
    VirtualFile script_file = LocalFileSystem.getInstance().findFileByPath(script_name);
    if (script_file == null || script_file.isDirectory()) {
      throw new ConfigurationException("Invalid script file '" + script_name + "'");
    }
    return script_file;
  }
}
