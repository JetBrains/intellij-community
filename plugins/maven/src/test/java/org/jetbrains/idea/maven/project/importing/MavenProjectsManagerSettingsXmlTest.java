// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing;

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.junit.Assume;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class MavenProjectsManagerSettingsXmlTest extends MavenMultiVersionImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    MavenUtil.setNoBackgroundMode();
    super.setUp();
    initProjectsManager(true);
    Assume.assumeFalse(MavenUtil.isLinearImportEnabled());
  }

  @Override
  protected void tearDown() throws Exception {
    MavenUtil.resetNoBackgroundMode();
    super.tearDown();
  }

  @Test
  public void testUpdatingProjectsOnSettingsXmlChange() throws Exception {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       <build>
                         <sourceDirectory>${prop}</sourceDirectory>
                       </build>
                       """);

    createModulePom("m",
                    """
                      <groupId>test</groupId>
                      <artifactId>m</artifactId>
                      <version>1</version>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                      </parent>
                      <build>
                        <sourceDirectory>${prop}</sourceDirectory>
                      </build>
                      """);

    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>one</id>
                            <activation>
                              <activeByDefault>true</activeByDefault>
                            </activation>
                            <properties>
                              <prop>value1</prop>
                            </properties>
                          </profile>
                        </profiles>
                        """);

    importProject();

    List<MavenProject> roots = getProjectsTree().getRootProjects();

    MavenProject parentNode = roots.get(0);
    MavenProject childNode = getProjectsTree().getModules(roots.get(0)).get(0);

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/value1")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/value1")));

    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>one</id>
                            <activation>
                              <activeByDefault>true</activeByDefault>
                            </activation>
                            <properties>
                              <prop>value2</prop>
                            </properties>
                          </profile>
                        </profiles>
                        """);
    waitForReadingCompletion();

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/value2")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/value2")));

    deleteSettingsXml();
    waitForReadingCompletion();

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/${prop}")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/${prop}")));

    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>one</id>
                            <activation>
                              <activeByDefault>true</activeByDefault>
                            </activation>
                            <properties>
                              <prop>value2</prop>
                            </properties>
                          </profile>
                        </profiles>
                        """);
    waitForReadingCompletion();

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/value2")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/value2")));
  }

  @Test
  public void testUpdatingProjectsOnSettingsXmlCreationAndDeletion() throws Exception {
    deleteSettingsXml();
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """);

    importProject();
    assertUnorderedElementsAreEqual(getProjectsTree().getAvailableProfiles());

    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>one</id>
                          </profile>
                        </profiles>
                        """);
    waitForReadingCompletion();
    assertUnorderedElementsAreEqual(getProjectsTree().getAvailableProfiles(), "one");

    deleteSettingsXml();
    waitForReadingCompletion();
    assertUnorderedElementsAreEqual(getProjectsTree().getAvailableProfiles());
  }

}