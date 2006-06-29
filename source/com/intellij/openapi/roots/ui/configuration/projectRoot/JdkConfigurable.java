/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.projectRoots.ui.SdkEditor;
import com.intellij.openapi.ui.NamedConfigurable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 05-Jun-2006
 */
public class JdkConfigurable implements NamedConfigurable<ProjectJdk> {
  private ProjectJdkImpl myProjectJdk;
  private SdkEditor mySdkEditor;

  public JdkConfigurable(final ProjectJdkImpl projectJdk, ProjectJdksModel configurable) {
    myProjectJdk = projectJdk;
    mySdkEditor = new SdkEditor(configurable);
    mySdkEditor.setSdk(myProjectJdk);
  }

  public void setDisplayName(final String name) {
    myProjectJdk.setName(name);
  }

  public ProjectJdk getEditableObject() {
    return myProjectJdk;
  }

  public String getBannerSlogan() {
    return myProjectJdk.getName();
  }

  public String getDisplayName() {
    return myProjectJdk.getName();
  }

  public Icon getIcon() {
    return myProjectJdk.getSdkType().getIcon();
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return mySdkEditor.createComponent();
  }

  public boolean isModified() {
    return mySdkEditor.isModified();
  }

  public void apply() throws ConfigurationException {
    mySdkEditor.apply();
  }

  public void reset() {
    mySdkEditor.reset();
  }

  public void disposeUIResources() {
    mySdkEditor.disposeUIResources();
  }
}
