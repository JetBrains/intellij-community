/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 16-Jul-2006
 * Time: 16:52:27
 */
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ModuleGroupConfigurable implements NamedConfigurable<ModuleGroup> {
  private ModuleGroup myModuleGroup;

  public ModuleGroupConfigurable(final ModuleGroup moduleGroup) {
    myModuleGroup = moduleGroup;
  }

  public void setDisplayName(final String name) {
    //do nothing
  }

  public ModuleGroup getEditableObject() {
    return myModuleGroup;
  }

  public String getBannerSlogan() {
    return ProjectBundle.message("module.group.banner.text", myModuleGroup.toString());
  }

  public String getDisplayName() {
    return myModuleGroup.toString();
  }

  public Icon getIcon() {
    return Icons.OPENED_MODULE_GROUP_ICON;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() { //todo
    return new PanelWithText("");
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