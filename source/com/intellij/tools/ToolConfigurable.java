package com.intellij.tools;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import java.io.IOException;

public class ToolConfigurable implements Configurable, ApplicationComponent {
  private static final Icon ourIcon = IconLoader.getIcon("/general/externalTools.png");

  private ToolsPanel myPanel;

  public String getComponentName() {
    return "ExternalToolsConfigurable";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public String getDisplayName() {
    return ToolsBundle.message("tools.settings.title");
  }

  public JComponent createComponent() {
    myPanel = new ToolsPanel();
    return myPanel;
  }

  public Icon getIcon() {
    return ourIcon;
  }

  public void apply() throws ConfigurationException {
    try {
      myPanel.apply();
    }
    catch (IOException e) {
      throw new ConfigurationException(e.getMessage());
    }
  }

  public boolean isModified() {
    return myPanel.isModified();
  }

  public void reset() {
    myPanel.reset();
  }

  public void disposeUIResources() {
    myPanel = null;
  }

  public String getHelpTopic() {
    return "preferences.externalTools";
  }
}