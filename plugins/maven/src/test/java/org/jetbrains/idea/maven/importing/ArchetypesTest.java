// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.maven.testFramework.MavenTestCase;
import org.jetbrains.idea.maven.execution.*;
import org.junit.Assert;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ArchetypesTest extends MavenTestCase {

  /**
   * very time consumed test (uses the network and -U maven flag)
   */
  public void ignoreTestGenerating() throws Exception {
    File dir = new File(myDir.getPath(), "generated");
    dir.mkdirs();

    MavenRunnerParameters params = new MavenRunnerParameters(
      false, dir.getPath(), (String)null,
      Arrays.asList("org.apache.maven.plugins:maven-archetype-plugin:RELEASE:generate"),
      Collections.emptyList()
    );

    MavenRunnerSettings settings = new MavenRunnerSettings();
    Map<String, String> props = new HashMap<>();
    props.put("archetypeGroupId", "org.apache.maven.archetypes");
    props.put("archetypeArtifactId", "maven-archetype-quickstart");
    props.put("archetypeVersion", "1.0");
    props.put("interactiveMode", "false");
    props.put("groupId", "foo");
    props.put("artifactId", "bar");

    settings.setMavenProperties(props);
    settings.setJreName(MavenRunnerSettings.USE_INTERNAL_JAVA);
    CountDownLatch latch = new CountDownLatch(1);
    MavenRunner.getInstance(myProject).run(params, settings, () -> latch.countDown());

    boolean tryAcquire = latch.await(20, TimeUnit.SECONDS);
    assertTrue("Maven execution failed", tryAcquire);
    assertTrue(new File(dir, "bar/pom.xml").exists());
  }

  public void testVmParametersGenerating() throws Exception {
    String mavenArchetypeGeneratorPlugin = "org.apache.maven.plugins:maven-archetype-plugin:RELEASE:generate";
    MavenRunnerParameters params = new MavenRunnerParameters(
      false, "generated", (String)null,
      Arrays.asList(mavenArchetypeGeneratorPlugin),
      Collections.emptyList()
    );

    String archetypeGroupId = "archetypeGroupId";
    String archetypeArtifactId = "archetypeArtifactId";
    String archetypeVersion = "archetypeVersion";
    String interactiveMode = "interactiveMode";
    String groupId = "groupId";
    String artifactId = "artifactId";

    String archetypeGroupIdValue = "org.apache.maven.archetypes";
    String archetypeArtifactIdValue = "maven-archetype-quickstart";
    String archetypeVersionValue = "1.0";
    String groupIdValue = "foo";
    String artifactIdValue = "bar";

    MavenRunnerSettings settings = new MavenRunnerSettings();
    Map<String, String> props = new HashMap<>();
    props.put(archetypeGroupId, archetypeGroupIdValue);
    props.put(archetypeArtifactId, archetypeArtifactIdValue);
    props.put(archetypeVersion, archetypeVersionValue);
    props.put(interactiveMode, Boolean.FALSE.toString());
    props.put(groupId, groupIdValue);
    props.put(artifactId, artifactIdValue);

    settings.setMavenProperties(props);
    settings.setJreName(MavenRunnerSettings.USE_INTERNAL_JAVA);
    MavenRunConfiguration configuration = (MavenRunConfiguration)MavenRunConfigurationType
      .createRunnerAndConfigurationSettings(null, settings, params, myProject)
      .getConfiguration();

    JavaParameters parameters = configuration.createJavaParameters(myProject);
    ParametersList parametersList = parameters.getProgramParametersList();
    Assert.assertEquals(archetypeGroupIdValue, parametersList.getPropertyValue(archetypeGroupId));
    Assert.assertEquals(archetypeArtifactIdValue, parametersList.getPropertyValue(archetypeArtifactId));
    Assert.assertEquals(archetypeVersionValue, parametersList.getPropertyValue(archetypeVersion));
    Assert.assertEquals(Boolean.FALSE.toString(), parametersList.getPropertyValue(interactiveMode));
    Assert.assertEquals(groupIdValue, parametersList.getPropertyValue(groupId));
    Assert.assertEquals(artifactIdValue, parametersList.getPropertyValue(artifactId));
    Assert.assertTrue(parametersList.hasParameter(mavenArchetypeGeneratorPlugin));
  }
}
