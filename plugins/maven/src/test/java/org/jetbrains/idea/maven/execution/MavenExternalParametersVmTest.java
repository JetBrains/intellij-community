// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import org.jetbrains.idea.maven.MavenTestCase;

import java.io.IOException;

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
}