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
import com.intellij.testFramework.JavaProjectTestCase;
import org.jdom.Element;
import org.jetbrains.idea.maven.config.MavenConfigSettings;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.junit.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MavenRunConfigurationTest extends JavaProjectTestCase {
  @Override
  protected void tearDown() throws Exception {
    try {
      MavenServerManager.getInstance().closeAllConnectorsAndWait();
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

  public void testDefaultMavenRunConfigurationParameters() throws ExecutionException {
    MavenRunnerParameters mavenRunnerParameters = new MavenRunnerParameters();
    mavenRunnerParameters.setGoals(List.of("clean"));
    mavenRunnerParameters.setWorkingDirPath("workingDirPath");

    RunnerAndConfigurationSettings settings = RunManager.getInstance(myProject)
      .createConfiguration("name", MavenRunConfigurationType.class);

    MavenRunConfiguration runConfiguration = (MavenRunConfiguration)settings.getConfiguration();
    runConfiguration.setRunnerParameters(mavenRunnerParameters);
    JavaParameters parameters = runConfiguration.createJavaParameters(myProject);

    notContainMavenKey(parameters, MavenConfigSettings.NON_RECURSIVE);
    notContainMavenKey(parameters, MavenConfigSettings.UPDATE_SNAPSHOTS);
    notContainMavenKey(parameters, MavenConfigSettings.OFFLINE);
    notContainMavenKey(parameters, MavenConfigSettings.ERRORS);
    Assert.assertTrue(parameters.getProgramParametersList().hasParameter(mavenRunnerParameters.getGoals().get(0)));
    String pathValue = parameters.getVMParametersList().getPropertyValue("maven.multiModuleProjectDirectory");
    Assert.assertNotNull(pathValue);
    Assert.assertTrue(pathValue.endsWith(mavenRunnerParameters.getWorkingDirPath()));
    Assert.assertTrue(parameters.getVMParametersList().hasProperty("maven.home"));
    Assert.assertTrue(parameters.getVMParametersList().hasProperty("jansi.passthrough"));
  }

  public void testInheritGeneralSettings() throws ExecutionException {
    MavenRunnerParameters mavenRunnerParameters = new MavenRunnerParameters();
    mavenRunnerParameters.setGoals(List.of("clean"));
    mavenRunnerParameters.setWorkingDirPath("workingDirPath");

    RunnerAndConfigurationSettings settings = RunManager.getInstance(myProject)
      .createConfiguration("name", MavenRunConfigurationType.class);

    MavenGeneralSettings generalSettings = MavenProjectsManager.getInstance(myProject).getGeneralSettings();
    generalSettings.setWorkOffline(true);
    generalSettings.setAlwaysUpdateSnapshots(true);
    generalSettings.setNonRecursive(true);
    generalSettings.setPrintErrorStackTraces(true);
    generalSettings.setThreads("4");
    generalSettings.setLocalRepository("inheritLocalRepository");
    generalSettings.setUserSettingsFile("inheritUserSettings");
    MavenRunConfiguration runConfiguration = (MavenRunConfiguration)settings.getConfiguration();
    runConfiguration.setRunnerParameters(mavenRunnerParameters);
    JavaParameters parameters = runConfiguration.createJavaParameters(myProject);

    containMavenKey(parameters, MavenConfigSettings.NON_RECURSIVE);
    containMavenKey(parameters, MavenConfigSettings.UPDATE_SNAPSHOTS);
    containMavenKey(parameters, MavenConfigSettings.OFFLINE);
    containMavenKey(parameters, MavenConfigSettings.ERRORS);
    containMavenKey(parameters, MavenConfigSettings.THREADS);
    Assert.assertTrue(parameters.getProgramParametersList().hasParameter(generalSettings.getThreads()));
    containMavenKey(parameters, MavenConfigSettings.ALTERNATE_USER_SETTINGS);
    Assert.assertTrue(parameters.getProgramParametersList().hasParameter(generalSettings.getUserSettingsFile()));
    Assert.assertEquals(generalSettings.getLocalRepository(), parameters.getProgramParametersList().getPropertyValue("maven.repo.local"));
  }

  public void testOverrideGeneralSettings() throws ExecutionException {
    MavenRunnerParameters mavenRunnerParameters = new MavenRunnerParameters();
    mavenRunnerParameters.setGoals(List.of("clean"));
    mavenRunnerParameters.setWorkingDirPath("workingDirPath");

    RunnerAndConfigurationSettings settings = RunManager.getInstance(myProject)
      .createConfiguration("name", MavenRunConfigurationType.class);

    MavenGeneralSettings generalSettings = MavenProjectsManager.getInstance(myProject).getGeneralSettings();
    generalSettings.setThreads("4");
    generalSettings.setLocalRepository("inheritLocalRepository");
    generalSettings.setUserSettingsFile("inheritUserSettings");
    MavenRunConfiguration runConfiguration = (MavenRunConfiguration)settings.getConfiguration();
    runConfiguration.setRunnerParameters(mavenRunnerParameters);
    runConfiguration.setGeneralSettings(generalSettings.clone());
    runConfiguration.getGeneralSettings().setThreads("5");
    runConfiguration.getGeneralSettings().setLocalRepository("overrideLocalRepository");
    runConfiguration.getGeneralSettings().setUserSettingsFile("overrideUserSettings");
    JavaParameters parameters = runConfiguration.createJavaParameters(myProject);

    containMavenKey(parameters, MavenConfigSettings.THREADS);
    Assert.assertTrue(parameters.getProgramParametersList().hasParameter("5"));
    containMavenKey(parameters, MavenConfigSettings.ALTERNATE_USER_SETTINGS);
    Assert.assertTrue(parameters.getProgramParametersList().hasParameter("overrideUserSettings"));
    Assert.assertEquals("overrideLocalRepository", parameters.getProgramParametersList().getPropertyValue("maven.repo.local"));
  }

  public void testInheritRunnerSettings() throws ExecutionException {
    MavenRunnerParameters mavenRunnerParameters = new MavenRunnerParameters();
    mavenRunnerParameters.setGoals(List.of("clean"));
    mavenRunnerParameters.setWorkingDirPath("workingDirPath");

    RunnerAndConfigurationSettings settings = RunManager.getInstance(myProject)
      .createConfiguration("name", MavenRunConfigurationType.class);

    MavenRunnerSettings runnerSettings = MavenRunner.getInstance(myProject).getSettings();
    runnerSettings.setSkipTests(true);
    runnerSettings.setVmOptions("-Xmx100m");
    runnerSettings.setMavenProperties(Map.of("mp1", "mp1"));
    runnerSettings.setEnvironmentProperties(Map.of("ep1", "ep1"));
    MavenRunConfiguration runConfiguration = (MavenRunConfiguration)settings.getConfiguration();
    runConfiguration.setRunnerParameters(mavenRunnerParameters);
    JavaParameters parameters = runConfiguration.createJavaParameters(myProject);

    Assert.assertTrue(parameters.getVMParametersList().hasParameter(runnerSettings.getVmOptions()));
    Assert.assertTrue(parameters.getProgramParametersList().hasParameter("-DskipTests=true"));
    Assert.assertEquals(runnerSettings.getEnvironmentProperties(), parameters.getEnv());
    Assert.assertEquals("mp1", parameters.getProgramParametersList().getPropertyValue("mp1"));
  }

  public void testOverrideRunnerSettings() throws ExecutionException {
    MavenRunnerParameters mavenRunnerParameters = new MavenRunnerParameters();
    mavenRunnerParameters.setGoals(List.of("clean"));
    mavenRunnerParameters.setWorkingDirPath("workingDirPath");

    RunnerAndConfigurationSettings settings = RunManager.getInstance(myProject)
      .createConfiguration("name", MavenRunConfigurationType.class);

    Map<String, String> mavenProperties = Map.of("mp2", "mp2");
    Map<String, String> environment = Map.of("ep2", "ep2");
    String vmOptions = "-Xmx200m";

    MavenRunnerSettings runnerSettings = MavenRunner.getInstance(myProject).getSettings();
    runnerSettings.setVmOptions("-Xmx100m");
    runnerSettings.setMavenProperties(Map.of("mp1", "mp1"));
    runnerSettings.setEnvironmentProperties(Map.of("ep1", "ep1"));
    MavenRunConfiguration runConfiguration = (MavenRunConfiguration)settings.getConfiguration();
    runConfiguration.setRunnerParameters(mavenRunnerParameters);
    runConfiguration.setRunnerSettings(runnerSettings.clone());
    runConfiguration.getRunnerSettings().setVmOptions(vmOptions);
    runConfiguration.getRunnerSettings().setMavenProperties(mavenProperties);
    runConfiguration.getRunnerSettings().setEnvironmentProperties(environment);
    JavaParameters parameters = runConfiguration.createJavaParameters(myProject);

    Assert.assertTrue(parameters.getVMParametersList().hasParameter(vmOptions));
    Assert.assertEquals(environment, parameters.getEnv());
    Assert.assertEquals("mp2", parameters.getProgramParametersList().getPropertyValue("mp2"));
    Assert.assertFalse(parameters.getProgramParametersList().hasProperty("mp1"));
  }

  private static void notContainMavenKey(JavaParameters parameters, MavenConfigSettings mavenConfigSettings) {
    Assert.assertFalse(parameters.getProgramParametersList().hasParameter(mavenConfigSettings.getLongKey()));
    Assert.assertFalse(parameters.getProgramParametersList().hasParameter(mavenConfigSettings.getKey()));
  }

  private static void containMavenKey(JavaParameters parameters, MavenConfigSettings mavenConfigSettings) {
    Assert.assertTrue(parameters.getProgramParametersList().hasParameter(mavenConfigSettings.getLongKey())
                      || parameters.getProgramParametersList().hasParameter(mavenConfigSettings.getKey()));
  }
}
