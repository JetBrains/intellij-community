/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerConfigurable;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.indices.MavenRepositoriesConfigurable;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.wizards.archetype.MavenCatalogsConfigurable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class MavenSettings implements SearchableConfigurable.Parent {
  private final Configurable myConfigurable;
  private final List<Configurable> myChildren;

  public MavenSettings(@NotNull Project project) {

    myConfigurable = new MavenGeneralConfigurable(project);

    myChildren = new ArrayList<>();
    myChildren.add(new MavenImportingConfigurable(project));
    myChildren.add(new MavenIgnoredFilesConfigurable(project));

    myChildren.add(new MyMavenRunnerConfigurable(project));

    myChildren.add(new MavenTestRunningConfigurable(project));

    if (!project.isDefault()) {
      myChildren.add(new MavenRepositoriesConfigurable(project));
    }

    myChildren.add(new MavenCatalogsConfigurable(project));
  }

  @Override
  public boolean hasOwnContent() {
    return true;
  }

  @Override
  public JComponent createComponent() {
    return myConfigurable.createComponent();
  }

  @Override
  public boolean isModified() {
    return myConfigurable.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myConfigurable.apply();
  }

  @Override
  public void reset() {
    myConfigurable.reset();
  }

  @Override
  public void disposeUIResources() {
    myConfigurable.disposeUIResources();
  }

  @Override
  public Configurable @NotNull [] getConfigurables() {
    return myChildren.toArray(new Configurable[0]);
  }

  @Override
  @NotNull
  public String getId() {
    return MavenSettings.class.getSimpleName();
  }

  @Override
  @Nls
  public String getDisplayName() {
    return MavenProjectBundle.message("configurable.MavenSettings.display.name");
  }

  @Override
  public String getHelpTopic() {
    return myConfigurable.getHelpTopic();
  }

  public static class MyMavenRunnerConfigurable extends MavenRunnerConfigurable {
    public MyMavenRunnerConfigurable(Project project) {
      super(project, false);
    }

    @Override
    protected MavenRunnerSettings getState() {
      return MavenRunner.getInstance(myProject).getState();
    }
  }
}
