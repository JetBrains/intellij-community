package com.intellij.application.options;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class CodeCompletionOptions extends BaseConfigurable implements ApplicationComponent {
  private CodeCompletionPanel myPanel;

  public boolean isModified() {
    return myPanel.isModified();
  }

  public JComponent createComponent() {
    myPanel = new CodeCompletionPanel();
    return myPanel.myPanel;
  }

  public String getDisplayName() {
    return "Code Completion";
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableCodeCompletion.png");
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
    return "preferences.codeCompletion";
  }

  public String getComponentName(){
    return "CodeCompletionOptions";
  }

  public void initComponent() { }

  public void disposeComponent(){
  }
}