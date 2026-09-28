// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution;

import com.google.common.collect.ImmutableMap;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jdom.Element;
import org.jetbrains.idea.maven.config.MavenConfigSettings;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.setUpJdk;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.disposableFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.moduleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class MavenRunConfigurationTest {
  private final TestFixture<Path> tempDir = tempPathFixture();
  private final TestFixture<Project> project = projectFixture(tempDir, OpenProjectTask.build(), true);
  private final TestFixture<Module> module = moduleFixture(project, tempDir, false);
  private final TestFixture<Disposable> disposable = disposableFixture();

  @BeforeEach
  public void setUp() {
    setUpJdk(LanguageLevel.JDK_17, project.get(), module.get(), disposable.get());
  }

  @AfterEach
  public void tearDown() {
    MavenServerManager.getInstance().closeAllConnectorsAndWait();
  }

  @Test
  public void testSaveLoadRunnerParameters() {
    MavenRunConfiguration.MavenSettings s = new MavenRunConfiguration.MavenSettings(project.get());
    s.myRunnerParameters.setWorkingDirPath("some path");
    s.myRunnerParameters.setGoals(Arrays.asList("clean", "validate"));
    s.myRunnerParameters.setProfilesMap(ImmutableMap.<String, Boolean>builder()
                                          .put("prof1", true)
                                          .put("prof2", true)
                                          .put("prof3", false)
                                          .put("aaa", true)
                                          .put("tomcat (local)", false)
                                          .put("tomcat (local) ", true).build());

    s.myGeneralSettings = new MavenGeneralSettings(project.get());
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
    // Compare ordering of profiles.
    assertIterableEquals(s.myRunnerParameters.getProfilesMap().keySet(), loaded.myRunnerParameters.getProfilesMap().keySet());

    assertEquals(s.myGeneralSettings, loaded.myGeneralSettings);
    assertEquals(s.myRunnerSettings, loaded.myRunnerSettings);
  }

  @Test
  public void testDefaultMavenRunConfigurationParameters() throws ExecutionException {
    MavenRunnerParameters mavenRunnerParameters = new MavenRunnerParameters();
    mavenRunnerParameters.setGoals(List.of("clean"));
    mavenRunnerParameters.setWorkingDirPath("workingDirPath");

    RunnerAndConfigurationSettings settings = RunManager.getInstance(project.get())
      .createConfiguration("name", MavenRunConfigurationType.class);

    MavenRunConfiguration runConfiguration = (MavenRunConfiguration)settings.getConfiguration();
    runConfiguration.setRunnerParameters(mavenRunnerParameters);
    JavaParameters parameters = runConfiguration.createJavaParameters(project.get());

    notContainMavenKey(parameters, MavenConfigSettings.NON_RECURSIVE);
    notContainMavenKey(parameters, MavenConfigSettings.UPDATE_SNAPSHOTS);
    notContainMavenKey(parameters, MavenConfigSettings.OFFLINE);
    notContainMavenKey(parameters, MavenConfigSettings.ERRORS);
    assertTrue(parameters.getProgramParametersList().hasParameter(mavenRunnerParameters.getGoals().get(0)));
    String pathValue = parameters.getVMParametersList().getPropertyValue("maven.multiModuleProjectDirectory");
    assertNotNull(pathValue);
    assertTrue(pathValue.endsWith(mavenRunnerParameters.getWorkingDirPath()));
    assertTrue(parameters.getVMParametersList().hasProperty("maven.home"));
    assertTrue(parameters.getVMParametersList().hasProperty("jansi.passthrough"));
  }

  @Test
  public void testInheritGeneralSettings() throws ExecutionException {
    MavenRunnerParameters mavenRunnerParameters = new MavenRunnerParameters();
    mavenRunnerParameters.setGoals(List.of("clean"));
    mavenRunnerParameters.setWorkingDirPath("workingDirPath");

    RunnerAndConfigurationSettings settings = RunManager.getInstance(project.get())
      .createConfiguration("name", MavenRunConfigurationType.class);

    MavenGeneralSettings generalSettings = MavenProjectsManager.getInstance(project.get()).getGeneralSettings();
    generalSettings.setWorkOffline(true);
    generalSettings.setAlwaysUpdateSnapshots(true);
    generalSettings.setNonRecursive(true);
    generalSettings.setPrintErrorStackTraces(true);
    generalSettings.setThreads("4");
    generalSettings.setLocalRepository("inheritLocalRepository");
    generalSettings.setUserSettingsFile("inheritUserSettings");
    MavenRunConfiguration runConfiguration = (MavenRunConfiguration)settings.getConfiguration();
    runConfiguration.setRunnerParameters(mavenRunnerParameters);
    JavaParameters parameters = runConfiguration.createJavaParameters(project.get());

    containMavenKey(parameters, MavenConfigSettings.NON_RECURSIVE);
    containMavenKey(parameters, MavenConfigSettings.UPDATE_SNAPSHOTS);
    containMavenKey(parameters, MavenConfigSettings.OFFLINE);
    containMavenKey(parameters, MavenConfigSettings.ERRORS);
    containMavenKey(parameters, MavenConfigSettings.THREADS);
    assertTrue(parameters.getProgramParametersList().hasParameter(generalSettings.getThreads()));
    containMavenKey(parameters, MavenConfigSettings.ALTERNATE_USER_SETTINGS);
    assertTrue(parameters.getProgramParametersList().hasParameter(generalSettings.getUserSettingsFile()));
    assertEquals(generalSettings.getLocalRepository(), parameters.getProgramParametersList().getPropertyValue("maven.repo.local"));
  }

  @Test
  public void testOverrideGeneralSettings() throws ExecutionException {
    MavenRunnerParameters mavenRunnerParameters = new MavenRunnerParameters();
    mavenRunnerParameters.setGoals(List.of("clean"));
    mavenRunnerParameters.setWorkingDirPath("workingDirPath");

    RunnerAndConfigurationSettings settings = RunManager.getInstance(project.get())
      .createConfiguration("name", MavenRunConfigurationType.class);

    MavenGeneralSettings generalSettings = MavenProjectsManager.getInstance(project.get()).getGeneralSettings();
    generalSettings.setThreads("4");
    generalSettings.setLocalRepository("inheritLocalRepository");
    generalSettings.setUserSettingsFile("inheritUserSettings");
    MavenRunConfiguration runConfiguration = (MavenRunConfiguration)settings.getConfiguration();
    runConfiguration.setRunnerParameters(mavenRunnerParameters);
    runConfiguration.setGeneralSettings(generalSettings.clone());
    runConfiguration.getGeneralSettings().setThreads("5");
    runConfiguration.getGeneralSettings().setLocalRepository("overrideLocalRepository");
    runConfiguration.getGeneralSettings().setUserSettingsFile("overrideUserSettings");
    JavaParameters parameters = runConfiguration.createJavaParameters(project.get());

    containMavenKey(parameters, MavenConfigSettings.THREADS);
    assertTrue(parameters.getProgramParametersList().hasParameter("5"));
    containMavenKey(parameters, MavenConfigSettings.ALTERNATE_USER_SETTINGS);
    assertTrue(parameters.getProgramParametersList().hasParameter("overrideUserSettings"));
    assertEquals("overrideLocalRepository", parameters.getProgramParametersList().getPropertyValue("maven.repo.local"));
  }

  @Test
  public void testInheritRunnerSettings() throws ExecutionException {
    MavenRunnerParameters mavenRunnerParameters = new MavenRunnerParameters();
    mavenRunnerParameters.setGoals(List.of("clean"));
    mavenRunnerParameters.setWorkingDirPath("workingDirPath");

    RunnerAndConfigurationSettings settings = RunManager.getInstance(project.get())
      .createConfiguration("name", MavenRunConfigurationType.class);

    MavenRunnerSettings runnerSettings = MavenRunner.getInstance(project.get()).getSettings();
    runnerSettings.setSkipTests(true);
    runnerSettings.setVmOptions("-Xmx100m");
    runnerSettings.setMavenProperties(Map.of("mp1", "mp1"));
    runnerSettings.setEnvironmentProperties(Map.of("ep1", "ep1"));
    MavenRunConfiguration runConfiguration = (MavenRunConfiguration)settings.getConfiguration();
    runConfiguration.setRunnerParameters(mavenRunnerParameters);
    JavaParameters parameters = runConfiguration.createJavaParameters(project.get());

    assertTrue(parameters.getVMParametersList().hasParameter(runnerSettings.getVmOptions()));
    assertTrue(parameters.getProgramParametersList().hasParameter("-DskipTests=true"));
    assertEquals(runnerSettings.getEnvironmentProperties(), parameters.getEnv());
    assertEquals("mp1", parameters.getProgramParametersList().getPropertyValue("mp1"));
  }

  @Test
  public void testOverrideRunnerSettings() throws ExecutionException {
    MavenRunnerParameters mavenRunnerParameters = new MavenRunnerParameters();
    mavenRunnerParameters.setGoals(List.of("clean"));
    mavenRunnerParameters.setWorkingDirPath("workingDirPath");

    RunnerAndConfigurationSettings settings = RunManager.getInstance(project.get())
      .createConfiguration("name", MavenRunConfigurationType.class);

    Map<String, String> mavenProperties = Map.of("mp2", "mp2");
    Map<String, String> environment = Map.of("ep2", "ep2");
    String vmOptions = "-Xmx200m";

    MavenRunnerSettings runnerSettings = MavenRunner.getInstance(project.get()).getSettings();
    runnerSettings.setVmOptions("-Xmx100m");
    runnerSettings.setMavenProperties(Map.of("mp1", "mp1"));
    runnerSettings.setEnvironmentProperties(Map.of("ep1", "ep1"));
    MavenRunConfiguration runConfiguration = (MavenRunConfiguration)settings.getConfiguration();
    runConfiguration.setRunnerParameters(mavenRunnerParameters);
    runConfiguration.setRunnerSettings(runnerSettings.clone());
    runConfiguration.getRunnerSettings().setVmOptions(vmOptions);
    runConfiguration.getRunnerSettings().setMavenProperties(mavenProperties);
    runConfiguration.getRunnerSettings().setEnvironmentProperties(environment);
    JavaParameters parameters = runConfiguration.createJavaParameters(project.get());

    assertTrue(parameters.getVMParametersList().hasParameter(vmOptions));
    assertEquals(environment, parameters.getEnv());
    assertEquals("mp2", parameters.getProgramParametersList().getPropertyValue("mp2"));
    assertFalse(parameters.getProgramParametersList().hasProperty("mp1"));
  }

  private static void notContainMavenKey(JavaParameters parameters, MavenConfigSettings mavenConfigSettings) {
    assertFalse(parameters.getProgramParametersList().hasParameter(mavenConfigSettings.getLongKey()));
    assertFalse(parameters.getProgramParametersList().hasParameter(mavenConfigSettings.getKey()));
  }

  private static void containMavenKey(JavaParameters parameters, MavenConfigSettings mavenConfigSettings) {
    assertTrue(parameters.getProgramParametersList().hasParameter(mavenConfigSettings.getLongKey())
               || parameters.getProgramParametersList().hasParameter(mavenConfigSettings.getKey()));
  }
}
