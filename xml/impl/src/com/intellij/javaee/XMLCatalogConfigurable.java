// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.javaee;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class XMLCatalogConfigurable implements Configurable {
  private TextFieldWithBrowseButton myPropertyFile;
  private JPanel myPanel;

  public XMLCatalogConfigurable() {
    myPropertyFile.addBrowseFolderListener(XmlBundle.message("xml.catalog.properties.file"), null, null,
                                           new FileChooserDescriptor(true, false, false, false, false, false));
  }

  @Nls
  @Override
  public String getDisplayName() {
    return XmlBundle.message("configurable.XMLCatalogConfigurable.display.name");
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
  public @Nullable JComponent getPreferredFocusedComponent() {
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
