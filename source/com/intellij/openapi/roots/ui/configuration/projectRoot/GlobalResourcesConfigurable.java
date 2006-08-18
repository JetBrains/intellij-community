/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 17-Aug-2006
 * Time: 14:13:02
 */
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.PanelWithText;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class GlobalResourcesConfigurable extends NamedConfigurable {
  public void setDisplayName(final String name) {
    //do nothing
  }

  public Object getEditableObject() {
    return null;
  }

  public String getBannerSlogan() {
    return ProjectBundle.message("project.roots.global.resources.display.name");
  }

  public JComponent createOptionsPanel() {
    return new PanelWithText();
  }

  public String getDisplayName() {
    return ProjectBundle.message("project.roots.global.resources.display.name");
  }

  public Icon getIcon() {
    return null;
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