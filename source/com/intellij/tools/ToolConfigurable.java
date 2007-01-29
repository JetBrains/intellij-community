package com.intellij.tools;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;

public class ToolConfigurable implements SearchableConfigurable {
  private static final Icon ourIcon = IconLoader.getIcon("/general/externalTools.png");
  private ToolsPanel myPanel;

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


  public String getId() {
    return getHelpTopic();
  }

  public boolean clearSearch() {
    return false;
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}