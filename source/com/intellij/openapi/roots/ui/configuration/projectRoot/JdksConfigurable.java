/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.NamedConfigurable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * User: anna
 * Date: 05-Jun-2006
 */
public class JdksConfigurable implements NamedConfigurable<ProjectJdksModel> {
  private ProjectJdksModel myJdkTableConfigurable;
  public static final String JDKS = ProjectBundle.message("jdks.node.display.name");


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
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {     //todo help
    return null;
  }

  public JComponent createComponent() {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createEtchedBorder());
    JLabel label = new JLabel();
    @NonNls String opentTag = "<html>";
    @NonNls String closeTag = "</html>";
    label.setText(opentTag + ProjectBundle.message("project.roots.jdks.node.text") + closeTag);
    panel.add(label, new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(8,8,8,8), 0, 0));
    return panel;
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
