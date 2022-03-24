// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.maven.testFramework.MavenTestCase;

import java.io.IOException;
import java.nio.file.Path;

public class MavenExternalParametersVmTest extends MavenTestCase {

  public void testGetRunVmOptionsSettingsAndJvm() throws IOException {
    createProjectSubFile(".mvn/jvm.config", "-Xms800m");
    MavenRunnerSettings runnerSettings = new MavenRunnerSettings();
    runnerSettings.setVmOptions("-Xmx400m");
    String vmOptions = MavenExternalParameters.getRunVmOptions(runnerSettings, myProject, getProjectPath());
    assertEquals("-Xmx400m", vmOptions);
  }

  public void testGetRunVmOptionsSettings() {
    MavenRunnerSettings runnerSettings = new MavenRunnerSettings();
    runnerSettings.setVmOptions("-Xmx400m");
    String vmOptions = MavenExternalParameters.getRunVmOptions(runnerSettings, myProject, getProjectPath());
    assertEquals("-Xmx400m", vmOptions);
  }

  public void testGetRunVmOptionsJvm() throws IOException {
    createProjectSubFile(".mvn/jvm.config", "-Xms800m");
    MavenRunnerSettings runnerSettings = new MavenRunnerSettings();
    String vmOptions = MavenExternalParameters.getRunVmOptions(runnerSettings, myProject, getProjectPath());
    assertEquals("-Xms800m", vmOptions);
  }

  public void testGetRunVmOptionsEmpty() throws IOException {
    MavenRunnerSettings runnerSettings = new MavenRunnerSettings();
    String vmOptions = MavenExternalParameters.getRunVmOptions(runnerSettings, myProject, getProjectPath());
    assertEmpty(vmOptions);
  }

  public void testGetRunVmOptionsSubmoduleConfigParent() throws IOException {
    createProjectSubFile(".mvn/jvm.config", "-Xms800m");
    MavenRunnerSettings runnerSettings = new MavenRunnerSettings();
    String workingDirPath = Path.of(getProjectPath()).resolve("module").toString();
    String vmOptions = MavenExternalParameters.getRunVmOptions(runnerSettings, myProject, workingDirPath);
    assertEquals("", vmOptions);
  }

  public void testGetRunVmOptionsSubmoduleConfig() throws IOException {
    createProjectSubFile("/module/.mvn/jvm.config", "-Xms800m");
    MavenRunnerSettings runnerSettings = new MavenRunnerSettings();
    String workingDirPath = Path.of(getProjectPath()).resolve("module").toString();
    String vmOptions = MavenExternalParameters.getRunVmOptions(runnerSettings, myProject, workingDirPath);
    assertEquals("-Xms800m", vmOptions);
  }
}