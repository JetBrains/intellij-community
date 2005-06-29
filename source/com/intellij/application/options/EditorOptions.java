package com.intellij.application.options;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class EditorOptions implements Configurable, ApplicationComponent {
  private EditorOptionsPanel myPanel;

  public void disposeComponent() {
  }

  public void initComponent() { }

  public boolean isModified() {
    return myPanel.isModified();
  }

  public JComponent createComponent() {
    myPanel = new EditorOptionsPanel();
    return myPanel.getTabbedPanel();
  }

  public String getDisplayName() {
    return "Editor";
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableEditor.png");
  }

  public void reset() {
    myPanel.reset();
  }

  public void apply() {
    myPanel.apply();
  }

  public void disposeUIResources() {
    myPanel = null;
  }

  public String getHelpTopic() {
    return "preferences.editor";
  }

  public String getComponentName() {
    return "EditorOptions";
  }
}