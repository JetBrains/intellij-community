package com.intellij.codeInsight.intention.impl.config;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import java.awt.*;

public class IntentionSettingsConfigurable extends BaseConfigurable implements ApplicationComponent {
  private IntentionSettingsPanel myPanel;

  public IntentionSettingsConfigurable() {
  }

  public JComponent createComponent() {
    myPanel = new IntentionSettingsPanel();
    JPanel component = myPanel.getComponent();
    component.setPreferredSize(new Dimension(800, 600));
    return component;
  }

  public void projectOpened() {

  }

  public void projectClosed() {

  }

  public boolean isModified() {
    return myPanel.isModified();
  }

  public String getDisplayName() {
    return "Intention Settings";
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableIntentionSettings.png");
  }

  public void reset() {
    myPanel.reset();
  }

  public void apply() {
    myPanel.apply();
  }

  public void disposeUIResources() {
    if (myPanel != null) {
      myPanel.dispose();
    }
  }

  public String getHelpTopic() {
    return "preferences.errorHighlight";
  }

  public void disposeComponent() {
  }

  public String getComponentName() {
    return "IntentionSettingsOptions";
  }

  public void initComponent() { }
}