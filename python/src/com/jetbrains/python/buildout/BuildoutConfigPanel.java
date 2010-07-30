package com.jetbrains.python.buildout;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;

import javax.swing.*;
import java.awt.*;

/**
 * Panel to choose target buildout script
 * User: dcheryasov
 * Date: Jul 26, 2010 5:09:23 PM
 */
public class BuildoutConfigPanel extends JPanel {
  private TextFieldWithBrowseButton myScript;
  private JPanel myPanel;
  private BuildoutFacetConfiguration myConfiguration;

  public BuildoutConfigPanel(BuildoutFacetConfiguration config) {
    myConfiguration = config;
    setLayout(new BorderLayout());
    add(myPanel);
    
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
    //descriptor.setRoot(myConfiguration.getRoot());
    myScript.addBrowseFolderListener(
      "Choose a buildout script", "Select the target script that will invoke your code",
      null, descriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT, false
    );
  }

  public boolean isModified(BuildoutFacetConfiguration configuration) {
    final String their = configuration.getScriptName();
    final String ours = myScript.getText();
    if (their == null && ours == null) return false;
    else return their == null || ! their.equals(ours); 
  }

  public String getScriptName() {
    return myScript.getText();
  }

  public void reset() {
    myScript.setText(myConfiguration.getScriptName());
  }

  public void apply() {
    myConfiguration.setScriptName(getScriptName());
  }

  BuildoutFacetConfiguration getConfiguration() {
    return myConfiguration;
  }
}
