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
package org.jetbrains.idea.maven.execution;

import com.google.common.collect.ImmutableMap;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.testFramework.JavaProjectTestCase;
import org.jdom.Element;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.junit.Assert;

import java.util.Arrays;
import java.util.List;

public class MavenRunConfigurationTest extends JavaProjectTestCase {
  @Override
  protected void tearDown() throws Exception {
    try {
      MavenServerManager.getInstance().shutdown(true);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testSaveLoadRunnerParameters() {
    MavenRunConfiguration.MavenSettings s = new MavenRunConfiguration.MavenSettings(myProject);
    s.myRunnerParameters.setWorkingDirPath("some path");
    s.myRunnerParameters.setGoals(Arrays.asList("clean", "validate"));
    s.myRunnerParameters.setProfilesMap(ImmutableMap.<String, Boolean>builder()
                                          .put("prof1", true)
                                          .put("prof2", true)
                                          .put("prof3", false)
                                          .put("aaa", true)
                                          .put("tomcat (local)", false)
                                          .put("tomcat (local) ", true).build());

    s.myGeneralSettings = new MavenGeneralSettings(myProject);
    s.myGeneralSettings.setChecksumPolicy(MavenExecutionOptions.ChecksumPolicy.WARN);
    s.myGeneralSettings.setFailureBehavior(MavenExecutionOptions.FailureMode.AT_END);
    s.myGeneralSettings.setOutputLevel(MavenExecutionOptions.LoggingLevel.FATAL);
    s.myGeneralSettings.setThreads("1.5C");

    s.myRunnerSettings = new MavenRunnerSettings();
    s.myRunnerSettings.setMavenProperties(ImmutableMap.of("a", "1", "b", "2", "c", "3"));

    Element xml = XmlSerializer.serialize(s);
    MavenRunConfiguration.MavenSettings loaded
      = XmlSerializer.deserialize(xml, MavenRunConfiguration.MavenSettings.class);
    
    assertEquals(s.myRunnerParameters.getWorkingDirPath(), loaded.myRunnerParameters.getWorkingDirPath());
    assertEquals(s.myRunnerParameters.getGoals(), loaded.myRunnerParameters.getGoals());
    assertEquals(s.myRunnerParameters.getProfilesMap(), loaded.myRunnerParameters.getProfilesMap());
    assertOrderedEquals(s.myRunnerParameters.getProfilesMap().keySet(), loaded.myRunnerParameters.getProfilesMap().keySet()); // Compare ordering of profiles.

    assertEquals(s.myGeneralSettings, loaded.myGeneralSettings);
    assertEquals(s.myRunnerSettings, loaded.myRunnerSettings);
  }

  public void testMavenParametersEditing() throws ConfigurationException {
    ImmutableMap<String, Boolean> profilesMap = ImmutableMap.<String, Boolean>builder()
      .put("prof1", true)
      .put("prof2", true)
      .put("prof3", false)
      .put("aaa", true)
      .put("tomcat (local)", false)
      .put("tomcat (local) ", true).build();

    final MavenRunnerParameters params = new MavenRunnerParameters();
    params.setProfilesMap(profilesMap);

    MavenRunnerParametersConfigurable cfg = new MavenRunnerParametersConfigurable(getProject()) {
      @Override
      protected MavenRunnerParameters getParameters() {
        return params;
      }
    };

    cfg.reset();

    cfg.apply();

    assertEquals(profilesMap, cfg.getParameters().getProfilesMap());
  }

  public void testDefaultMavenRunConfigurationParameters() throws ExecutionException {
    MavenRunnerSettings mavenRunnerSettings = new MavenRunnerSettings();
    mavenRunnerSettings.setJreName(MavenRunnerSettings.USE_PROJECT_JDK);

    MavenRunnerParameters mavenRunnerParameters = new MavenRunnerParameters();
    mavenRunnerParameters.setGoals(List.of("clean"));
    mavenRunnerParameters.setWorkingDirPath("workingDirPath");

    RunnerAndConfigurationSettings settings = RunManager.getInstance(myProject)
      .createConfiguration("name", MavenRunConfigurationType.class);

    MavenRunConfiguration runConfiguration = (MavenRunConfiguration)settings.getConfiguration();
    runConfiguration.getSettings().setRunnerSettings(mavenRunnerSettings);
    runConfiguration.getSettings().setRunnerParameters(mavenRunnerParameters);
    JavaParameters parameters = runConfiguration.createJavaParameters(myProject);

    Assert.assertFalse(parameters.getProgramParametersList().hasParameter("--non-recursive"));
    Assert.assertFalse(parameters.getProgramParametersList().hasParameter("-N"));
    Assert.assertTrue(parameters.getProgramParametersList().hasParameter(mavenRunnerParameters.getGoals().get(0)));
    String pathValue = parameters.getVMParametersList().getPropertyValue("maven.multiModuleProjectDirectory");
    Assert.assertNotNull(pathValue);
    Assert.assertTrue(pathValue.endsWith(mavenRunnerParameters.getWorkingDirPath()));
    Assert.assertTrue(parameters.getVMParametersList().hasProperty("maven.home"));
  }
}
