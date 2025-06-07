// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.svn.SvnConfiguration;

import javax.swing.*;

public abstract class SvnUpdateConfigurable implements Configurable {
  private static final @NonNls String HELP_ID = "vcs.subversion.updateProject";

  private AbstractSvnUpdatePanel myPanel;

  private final Project myProject;

  public SvnUpdateConfigurable(Project project) {
    myProject = project;
  }

  @Override
  public String getHelpTopic() {
    return HELP_ID;
  }


  @Override
  public void apply() throws ConfigurationException {
    myPanel.apply(SvnConfiguration.getInstance(myProject));
  }

  @Override
  public JComponent createComponent() {
    myPanel = createPanel();
    return myPanel.getPanel();
  }

  protected abstract AbstractSvnUpdatePanel createPanel();

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void reset() {
    myPanel.reset(SvnConfiguration.getInstance(myProject));
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

  protected Project getProject() {
    return myProject;
  }
}
