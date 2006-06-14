package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CodeCompletionOptions extends BaseConfigurable implements SearchableConfigurable, ApplicationComponent {
  private CodeCompletionPanel myPanel;

  public boolean isModified() {
    return myPanel.isModified();
  }

  public JComponent createComponent() {
    myPanel = new CodeCompletionPanel();
    return myPanel.myPanel;
  }

  public String getDisplayName() {
    return ApplicationBundle.message("title.code.completion");
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

  @NotNull
  public String getComponentName() {
    return "CodeCompletionOptions";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
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