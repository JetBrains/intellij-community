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
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.Nls;
import org.jetbrains.idea.maven.indices.MavenServicesConfigurable;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.execution.MavenRunnerConfigurable;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class MavenSettings implements SearchableConfigurable.Parent {
  private final Project myProject;
  private final Configurable myConfigurable;
  private final List<Configurable> myChildren;

  public MavenSettings(Project project) {
    myProject = project;

    myConfigurable = new MavenGeneralConfigurable() {
      protected MavenGeneralSettings getState() {
        return MavenProjectsManager.getInstance(myProject).getGeneralSettings();
      }
    };

    myChildren = new ArrayList<Configurable>();
    myChildren.add(new MavenImportingConfigurable(MavenProjectsManager.getInstance(myProject).getImportingSettings()));
    myChildren.add(new MavenIgnoredFilesConfigurable(MavenProjectsManager.getInstance(myProject)));

    myChildren.add(new MavenRunnerConfigurable(myProject, false) {
      protected MavenRunnerSettings getState() {
        return MavenRunner.getInstance(myProject).getState();
      }
    });

    if (!myProject.isDefault()) {
      myChildren.add(new MavenServicesConfigurable(myProject));
    }
  }

  public boolean hasOwnContent() {
    return true;
  }

  public boolean isVisible() {
    return true;
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  public JComponent createComponent() {
    return myConfigurable.createComponent();
  }

  public boolean isModified() {
    return myConfigurable.isModified();
  }

  public void apply() throws ConfigurationException {
    myConfigurable.apply();
  }

  public void reset() {
    myConfigurable.reset();
  }

  public void disposeUIResources() {
    myConfigurable.disposeUIResources();
  }

  public Configurable[] getConfigurables() {
    return myChildren.toArray(new Configurable[myChildren.size()]);
  }

  public String getId() {
    return MavenSettings.class.getSimpleName();
  }

  @Nls
  public String getDisplayName() {
    return "Maven";
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableEditor.png");
  }

  public String getHelpTopic() {
    return myConfigurable.getHelpTopic();
  }
}
