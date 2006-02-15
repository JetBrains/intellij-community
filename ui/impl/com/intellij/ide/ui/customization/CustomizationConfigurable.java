package com.intellij.ide.ui.customization;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.GlassPanel;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * User: anna
 * Date: Mar 17, 2005
 */
public class CustomizationConfigurable extends BaseConfigurable implements SearchableConfigurable, ApplicationComponent {
  private CustomizableActionsPanel myPanel;
  private GlassPanel myGlassPanel;

  public CustomizationConfigurable() {

  }

  public JComponent createComponent() {
    myPanel = new CustomizableActionsPanel();
    myGlassPanel = new GlassPanel(myPanel.getPanel());
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
    myPanel.getPanel().getRootPane().setGlassPane(myGlassPanel);
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

  public Runnable showOption(String option) {
    return SearchUtil.lightOptions(this, myPanel.getPanel(), option, myGlassPanel);
  }

  public String getId() {
    return getHelpTopic();
  }

  public void clearSearch() {
    myGlassPanel.clear();
  }
}
