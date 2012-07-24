/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 *         Date: 7/20/12
 */
public class XMLCatalogConfigurable extends BaseConfigurable implements SearchableConfigurable {

  private TextFieldWithBrowseButton myPropertyFile;
  private JPanel myPanel;

  public XMLCatalogConfigurable() {
    myPropertyFile.addBrowseFolderListener("XML Catalog Properties File", null, null,
                                           new FileChooserDescriptor(true, false, false, false, false, false));
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "XML Catalog";
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPropertyFile.getTextField();
  }

  @Override
  public void apply() throws ConfigurationException {
    ExternalResourceManagerEx.getInstanceEx().setCatalogPropertiesFile(myPropertyFile.getText());
  }

  @Override
  public void reset() {
    myPropertyFile.setText(ExternalResourceManagerEx.getInstanceEx().getCatalogPropertiesFile());
  }

  @Override
  public void disposeUIResources() {

  }

  @NotNull
  @Override
  public String getId() {
    return "xml.catalog";
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }
}
