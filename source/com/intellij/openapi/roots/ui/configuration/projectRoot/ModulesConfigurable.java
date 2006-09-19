/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 17-Aug-2006
 * Time: 14:10:54
 */
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.PanelWithText;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ModulesConfigurable extends NamedConfigurable<ModuleManager> {
  private static final Icon PROJECT_ICON = IconLoader.getIcon("/modules/modulesNode.png");

  private ModuleManager myManager;

  public ModulesConfigurable(ModuleManager manager) {
    myManager = manager;
  }

  public void setDisplayName(final String name) {
    //do nothing
  }

  public ModuleManager getEditableObject() {
    return myManager;
  }

  public String getBannerSlogan() {
    return ProjectBundle.message("project.roots.modules.display.name");
  }

  public JComponent createOptionsPanel() {
    return new PanelWithText(ProjectBundle.message("project.roots.modules.description"));
  }

  public String getDisplayName() {
    return ProjectBundle.message("project.roots.modules.display.name");
  }

  public Icon getIcon() {
    return PROJECT_ICON;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  public boolean isModified() {
    return false;
  }

  public void apply() throws ConfigurationException {
    //do nothing
  }

  public void reset() {
    //do nothing
  }

  public void disposeUIResources() {
    //do nothing
  }
}