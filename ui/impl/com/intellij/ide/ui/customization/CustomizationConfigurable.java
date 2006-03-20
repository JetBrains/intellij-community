package com.intellij.ide.ui.customization;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: Mar 17, 2005
 */
public class CustomizationConfigurable extends BaseConfigurable implements SearchableConfigurable, ApplicationComponent {
  private CustomizableActionsPanel myPanel;

  public CustomizationConfigurable() {

  }

  public JComponent createComponent() {
    myPanel = new CustomizableActionsPanel();
    return myPanel.getPanel();
  }

  public String getDisplayName() {
    return IdeBundle.message("title.customizations");
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableCustomization.png");
  }

  public String getHelpTopic() {
    return "preferences.customizations";
  }

  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  public void reset() {
    myPanel.reset();
  }

  public boolean isModified() {
    return myPanel.isModified();
  }

  public void disposeUIResources() {
  }

  public String getComponentName() {
    return "com.intellij.ide.ui.customization.CustomizationConfigurable";
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
