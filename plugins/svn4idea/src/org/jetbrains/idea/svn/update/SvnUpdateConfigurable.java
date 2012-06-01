/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.svn.SvnConfiguration;

import javax.swing.*;

public abstract class SvnUpdateConfigurable implements Configurable {
  @NonNls private static final String HELP_ID = "vcs.subversion.updateProject";

  private AbstractSvnUpdatePanel myPanel;

  private final Project myProject;

  public SvnUpdateConfigurable(Project project) {
    myProject = project;
  }

  public String getHelpTopic() {
    return HELP_ID;
  }


  public void apply() throws ConfigurationException {
    myPanel.apply(SvnConfiguration.getInstance(myProject));
  }

  public JComponent createComponent() {
    myPanel = createPanel();
    return myPanel.getPanel();
  }

  protected abstract AbstractSvnUpdatePanel createPanel();

  public boolean isModified() {
    return false;
  }

  public void reset() {
    myPanel.reset(SvnConfiguration.getInstance(myProject));
  }

  public void disposeUIResources() {
    myPanel = null;
  }

  protected Project getProject() {
    return myProject;
  }
}
