package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class KeymapConfigurable extends BaseConfigurable implements ApplicationComponent {
  private static final Icon icon = IconLoader.getIcon("/general/keymap.png");
  private KeymapPanel myPanel;

  public String getComponentName() {
    return "KeymapConfigurable";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public String getDisplayName() {
    return "Keymap";
  }

  public JComponent createComponent() {
    myPanel = new KeymapPanel();
    return myPanel;
  }

  public boolean isModified() {
    return myPanel.isModified();
  }

  public Icon getIcon() {
    return icon;
  }

  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  public void reset() {
    myPanel.reset();
  }

  public void disposeUIResources() {
    myPanel = null;
  }

  public String getHelpTopic() {
    return "preferences.keymap";
  }

  public void selectAction(String actionId) {
    myPanel.selectAction(actionId);
  }
}