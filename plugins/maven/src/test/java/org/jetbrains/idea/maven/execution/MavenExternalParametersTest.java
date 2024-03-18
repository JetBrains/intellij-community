// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.maven.testFramework.MavenExecutionTestCase;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenWrapper;
import org.junit.Test;

import java.io.IOException;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

public class MavenExternalParametersTest extends MavenExecutionTestCase {

  @Test
  public void testBundledMavenHome() throws IOException, ExecutionException {
    MavenRunnerParameters runnerParameters = new MavenRunnerParameters(getProjectPath(), null, false, emptyList(), emptyMap());
    MavenGeneralSettings generalSettings = MavenProjectsManager.getInstance(getProject()).getGeneralSettings();
    JavaParameters parameters = MavenExternalParameters.createJavaParameters(getProject(), runnerParameters, generalSettings, null, null);
    assertTrue(parameters.getVMParametersList().hasProperty("maven.home"));
  }

  @Test
  public void testWrappedMavenWithoutWrapperProperties() throws IOException, ExecutionException {
    MavenRunnerParameters runnerParameters = new MavenRunnerParameters(getProjectPath(), null, false, emptyList(), emptyMap());
    MavenGeneralSettings generalSettings = MavenProjectsManager.getInstance(getProject()).getGeneralSettings();
    generalSettings.setMavenHomeType(MavenWrapper.INSTANCE);
    JavaParameters parameters = MavenExternalParameters.createJavaParameters(getProject(), runnerParameters, generalSettings, null, null);
    assertTrue(parameters.getVMParametersList().hasProperty("maven.home"));
  }
}