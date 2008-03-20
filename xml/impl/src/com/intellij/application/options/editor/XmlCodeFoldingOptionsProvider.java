/*
 * User: anna
 * Date: 14-Feb-2008
 */
package com.intellij.application.options.editor;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.ConfigurationException;

import javax.swing.*;

public class XmlCodeFoldingOptionsProvider implements CodeFoldingOptionsProvider{
  private JCheckBox myCbCollapseXMLTags = new JCheckBox(ApplicationBundle.message("checkbox.collapse.xml.tags"));

  public JComponent createComponent() {
    return myCbCollapseXMLTags;
  }

  public boolean isModified() {
    XmlFoldingSettings codeFoldingSettings = XmlFoldingSettings.getInstance();
    return codeFoldingSettings.isCollapseXmlTags() != myCbCollapseXMLTags.isSelected();
  }

  public void apply() throws ConfigurationException {
    XmlFoldingSettings codeFoldingSettings = XmlFoldingSettings.getInstance();
    codeFoldingSettings.setCollapseXmlTags(myCbCollapseXMLTags.isSelected());
  }

  public void reset() {
    XmlFoldingSettings codeFoldingSettings = XmlFoldingSettings.getInstance();
    myCbCollapseXMLTags.setSelected(codeFoldingSettings.isCollapseXmlTags());
  }

  public void disposeUIResources() {
    myCbCollapseXMLTags = null;
  }
}