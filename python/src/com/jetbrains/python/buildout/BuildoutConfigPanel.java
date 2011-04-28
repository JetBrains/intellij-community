package com.jetbrains.python.buildout;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * Panel to choose target buildout script
 * User: dcheryasov
 * Date: Jul 26, 2010 5:09:23 PM
 */
public class BuildoutConfigPanel extends JPanel {
  private ComboboxWithBrowseButton myScript;
  private JPanel myPanel;
  private JTextArea myNoticeTextArea;
  private final Module myModule;
  private BuildoutFacetConfiguration myConfiguration;

  public BuildoutConfigPanel(Module module, BuildoutFacetConfiguration config) {
    myModule = module;
    myConfiguration = config;
    setLayout(new BorderLayout());
    add(myPanel);
    
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
    //descriptor.setRoot(myConfiguration.getRoot());
    myScript.addBrowseFolderListener(
      "Choose a buildout script", "Select the target script that will invoke your code",
      null, descriptor, TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT, false
    );
    myScript.getComboBox().setEditable(true);
  }

  public boolean isModified(BuildoutFacetConfiguration configuration) {
    final String their = configuration.getScriptName();
    final String ours = getScriptName();
    return !Comparing.strEqual(their, ours);
  }

  public String getScriptName() {
    return (String) myScript.getComboBox().getEditor().getItem();
  }

  public void reset() {
    final List<File> scriptFiles = BuildoutFacet.getScripts(BuildoutFacet.getInstance(myModule), myModule.getProject().getBaseDir());
    final List<String> scripts = ContainerUtil.map(scriptFiles, new Function<File, String>() {
      @Override
      public String fun(File file) {
        return file.getPath();
      }
    });
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

}
