/*
 * User: anna
 * Date: 14-Feb-2008
 */
package com.intellij.application.options.editor;

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.ConfigurationException;

import javax.swing.*;

public class XmlCodeFoldingOptionsProvider implements CodeFoldingOptionsProvider{
  private JCheckBox myCbCollapseXMLTags = new JCheckBox(ApplicationBundle.message("checkbox.collapse.xml.tags"));

  public JComponent createComponent() {
    return myCbCollapseXMLTags;
  }

  public boolean isModified() {
    CodeFoldingSettings codeFoldingSettings = CodeFoldingSettings.getInstance();
    return codeFoldingSettings.isCollapseXmlTags() != myCbCollapseXMLTags.isSelected();
  }

  public void apply() throws ConfigurationException {
    CodeFoldingSettings codeFoldingSettings = CodeFoldingSettings.getInstance();
    codeFoldingSettings.setCollapseXmlTags(myCbCollapseXMLTags.isSelected());
  }

  public void reset() {
    CodeFoldingSettings codeFoldingSettings = CodeFoldingSettings.getInstance();
    myCbCollapseXMLTags.setSelected(codeFoldingSettings.isCollapseXmlTags());
  }

  public void disposeUIResources() {
    myCbCollapseXMLTags = null;
  }
}