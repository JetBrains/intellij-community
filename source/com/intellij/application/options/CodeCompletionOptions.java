package com.intellij.application.options;

import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.GlassPanel;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class CodeCompletionOptions extends BaseConfigurable implements SearchableConfigurable, ApplicationComponent {
  private CodeCompletionPanel myPanel;
  private GlassPanel myGlassPanel;

  public boolean isModified() {
    return myPanel.isModified();
  }

  public JComponent createComponent() {
    myPanel = new CodeCompletionPanel();
    myGlassPanel = new GlassPanel(myPanel.myPanel);
    return myPanel.myPanel;
  }

  public String getDisplayName() {
    return ApplicationBundle.message("title.code.completion");
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableCodeCompletion.png");
  }

  public void reset() {
    myPanel.myPanel.getRootPane().setGlassPane(myGlassPanel);
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

  public String getComponentName() {
    return "CodeCompletionOptions";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public Runnable showOption(String option) {
    return SearchUtil.lightOptions(this, myPanel.myPanel, option, myGlassPanel);
  }

  public String getId() {
    return getHelpTopic();
  }

  public void clearSearch() {
    myGlassPanel.clear();
  }
}