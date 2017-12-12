/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.idea.maven.execution;

import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.Collection;
import java.util.List;

public class MavenConfigurationProducer extends RuntimeConfigurationProducer {
  private PsiElement myPsiElement;

  public MavenConfigurationProducer() {
    super(MavenRunConfigurationType.getInstance());
  }

  @Override
  public PsiElement getSourceElement() {
    return myPsiElement;
  }

  @Override
  protected RunnerAndConfigurationSettings createConfigurationByElement(Location location, ConfigurationContext context) {
    myPsiElement = location.getPsiElement();
    final MavenRunnerParameters params = createBuildParameters(location);
    if (params == null) return null;

    return MavenRunConfigurationType.createRunnerAndConfigurationSettings(null, null, params, location.getProject());
  }

  @Override
  protected RunnerAndConfigurationSettings findExistingByElement(Location location,
                                                                 @NotNull List<RunnerAndConfigurationSettings> existingConfigurations,
                                                                 ConfigurationContext context) {

    final MavenRunnerParameters runnerParameters = createBuildParameters(location);
    for (RunnerAndConfigurationSettings existingConfiguration : existingConfigurations) {
      final RunConfiguration configuration = existingConfiguration.getConfiguration();
      if (configuration instanceof MavenRunConfiguration &&
          ((MavenRunConfiguration)configuration).getRunnerParameters().equals(runnerParameters)) {
        return existingConfiguration;
      }
    }
    return null;
  }

  private static MavenRunnerParameters createBuildParameters(Location l) {
    if (!(l instanceof MavenGoalLocation)) return null;

    VirtualFile f = ((PsiFile)l.getPsiElement()).getVirtualFile();
    List<String> goals = ((MavenGoalLocation)l).getGoals();
    MavenExplicitProfiles profiles = MavenProjectsManager.getInstance(l.getProject()).getExplicitProfiles();

    return new MavenRunnerParameters(true, f.getParent().getPath(), f.getName(), goals, profiles.getEnabledProfiles(), profiles.getDisabledProfiles());
  }

  public int compareTo(Object o) {
    return PREFERED;
  }
}
