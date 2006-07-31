/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.PanelWithText;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 05-Jun-2006
 */
public class JdksConfigurable extends NamedConfigurable<ProjectJdksModel> {
  private ProjectJdksModel myJdkTableConfigurable;
  public static final String JDKS = ProjectBundle.message("jdks.node.display.name");
  public static final Icon ICON = IconLoader.getIcon("/modules/jdks.png");


  public JdksConfigurable(final ProjectJdksModel jdksTreeModel) {
    myJdkTableConfigurable = jdksTreeModel;
  }

  public void setDisplayName(final String name) {
    //do nothing
  }

  public ProjectJdksModel getEditableObject() {
    return myJdkTableConfigurable;
  }

  public String getBannerSlogan() {
    return JDKS;
  }

  public String getDisplayName() {
    return JDKS;
  }

  public Icon getIcon() {
    return ICON;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {     //todo help
    return null;
  }


  public JComponent createOptionsPanel() {
    return new PanelWithText(ProjectBundle.message("project.roots.jdks.node.text"));
  }

  public boolean isModified() {
    return false;
  }

  public void apply() throws ConfigurationException {
  }

  public void reset() {
  }

  public void disposeUIResources() {

  }
}
