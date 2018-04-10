/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class XMLCatalogConfigurable extends BaseConfigurable {

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
    return "XML.Catalog.Dialog";
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
  public boolean isModified() {
    return !StringUtil.notNullize(ExternalResourceManagerEx.getInstanceEx().getCatalogPropertiesFile()).equals(myPropertyFile.getText());
  }
}
