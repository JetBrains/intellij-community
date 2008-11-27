package com.intellij.application.options;

import com.intellij.application.options.editor.AutoImportOptionsProvider;
import com.intellij.openapi.options.ConfigurationException;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class XmlAutoImportOptionsProvider implements AutoImportOptionsProvider {

  private JPanel myPanel;
  private JCheckBox myShowAutoImportPopups;

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return XmlSettings.getInstance().SHOW_XML_ADD_IMPORT_HINTS != myShowAutoImportPopups.isSelected();
  }

  public void apply() throws ConfigurationException {
    XmlSettings.getInstance().SHOW_XML_ADD_IMPORT_HINTS = myShowAutoImportPopups.isSelected();
  }

  public void reset() {
    myShowAutoImportPopups.setSelected(XmlSettings.getInstance().SHOW_XML_ADD_IMPORT_HINTS);    
  }

  public void disposeUIResources() {

  }
}
