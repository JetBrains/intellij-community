// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.compatibility;

import com.intellij.maven.testFramework.MavenImportingTestCase;
import com.intellij.maven.testFramework.MavenWrapperTestFixture;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;


@RunWith(Parameterized.class)
public class MavenCompatibilityProjectImportingTest extends MavenImportingTestCase {


  @Parameterized.Parameters(name = "with Maven-{0}")
  public static List<String[]> getMavenVersions() {
    return Arrays.asList(
      new String[]{"3.9.0"},
      new String[]{"3.8.6"},
      new String[]{"3.8.6"},
      new String[]{"3.8.5"},
      new String[]{"3.8.4"},
      new String[]{"3.8.3"},
      new String[]{"3.8.2"},
      new String[]{"3.8.1"},
      new String[]{"3.8.1"},
      new String[]{"3.6.3"},
      new String[]{"3.6.2"},
      new String[]{"3.6.1"},
      new String[]{"3.6.0"},
      new String[]{"3.5.4"},
      new String[]{"3.5.3"},
      new String[]{"3.5.2"},
      new String[]{"3.5.0"},
      new String[]{"3.3.9"},
      new String[]{"3.3.3"},
      new String[]{"3.3.1"},
      new String[]{"3.2.5"},
      new String[]{"3.2.3"},
      new String[]{"3.2.2"},
      new String[]{"3.2.1"},
      new String[]{"3.1.1"},
      new String[]{"3.1.0"},
      new String[]{"3.0.5"},
      new String[]{"3.0.4"},
      new String[]{"3.0.3"},
      new String[]{"3.0.2"},
      new String[]{"3.0.1"},
      new String[]{"3.0"}
    );
  }

  @NotNull
  protected MavenWrapperTestFixture myWrapperTestFixture;

  @Parameterized.Parameter
  public String myMavenVersion;

  protected void assumeVersionMoreThan(String version) {
    Assume.assumeTrue("Version should be more than " + version, VersionComparatorUtil.compare(myMavenVersion, version) > 0);
  }

  protected void assumeVersionLessOrEqualsThan(String version) {
    Assume.assumeTrue("Version should be less than " + version, VersionComparatorUtil.compare(myMavenVersion, version) >= 0);
  }

  protected void assumeVersionNot(String version) {
    Assume.assumeTrue("Version " + version + " skipped", VersionComparatorUtil.compare(myMavenVersion, version) != 0);
  }

  @Before
  public void before() throws Exception {
    myWrapperTestFixture = new MavenWrapperTestFixture(myProject, myMavenVersion);
    myWrapperTestFixture.setUp();


    MavenCustomRepositoryHelper helper = new MavenCustomRepositoryHelper(myDir, "local1");
    String repoPath = helper.getTestDataPath("local1");
    setRepositoryPath(repoPath);
  }

  @After
  public void after() throws Exception {
    myWrapperTestFixture.tearDown();
  }


  @Test
  public void testSmokeImport() {
    assertCorrectVersion();

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);


    assertModules("project");
  }

  @Test
  public void testSmokeImportWithUnknownExtension() throws IOException {
    assertCorrectVersion();
    createProjectSubFile(".mvn/extensions.xml", """
      <extensions>
        <extension>
          <groupId>org.example</groupId>
          <artifactId>some-never-existed-extension</artifactId>
          <version>1</version>
        </extension>
      </extensions>
      """);
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """);

    createModulePom("m1", """
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <version>1</version>
                       </parent>
                       <artifactId>m1</artifactId>
                         """);

    createModulePom("m2", """
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <version>1</version>
                       </parent>
                       <artifactId>m2</artifactId>
                         """);

    importProject();

    assertModules("m2", "m1", "project");
  }


  private void assertCorrectVersion() {
    assertEquals(myMavenVersion,
                 MavenServerManager.getInstance().getConnector(myProject, myProjectRoot.getPath()).getMavenDistribution().getVersion());
  }

  @Test
  public void testInterpolateModel() {
    assertCorrectVersion();

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <junitVersion>4.0</junitVersion>
                      </properties>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>${junitVersion}</version>
                      </dependency>
                    </dependencies>
                    """);

    assertModules("project");

    assertModuleLibDep("project", "Maven: junit:junit:4.0");
  }

  @Test
  public void testImportProjectProperties() {
    assumeVersionMoreThan("3.0.3");

    assertCorrectVersion();

    createModulePom("module1", """
      <parent>
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      </parent>
      <artifactId>module1</artifactId>
      <dependencies>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>${junitVersion}</version>
        </dependency>
      </dependencies>"""
    );

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <properties>
                        <junitVersion>4.0</junitVersion>
                      </properties>
                    <modules>
                    <module>module1</module>
                    </modules>
                    """);

    waitForReadingCompletion();
    assertModules("project", mn("project", "module1"));

    assertModuleLibDep(mn("project", "module1"), "Maven: junit:junit:4.0");
  }

  @Test
  public void testImportAddedProjectProperties() {
    assumeVersionMoreThan("3.0.3");
    assumeVersionNot("3.6.0");

    assertCorrectVersion();

    createModulePom("module1", """
      <parent>
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      </parent>
      <artifactId>module1</artifactId>
      <dependencies>
        <dependency>
          <groupId>org.example</groupId>
          <artifactId>intellijmaventest</artifactId>
          <version>${libVersion}</version>
        </dependency>
      </dependencies>"""
    );

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <properties>
                        <libVersion>1.0</libVersion>
                      </properties>
                    <modules>
                    <module>module1</module>
                    </modules>
                    """);

    waitForReadingCompletion();
    assertModules("project", mn("project", "module1"));

    assertModuleLibDep(mn("project", "module1"), "Maven: org.example:intellijmaventest:1.0");

      /*myWrapperTestFixture.tearDown();
      myWrapperTestFixture.setUp();*/

    createModulePom("module1", """
      <parent>
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      </parent>
      <artifactId>module1</artifactId>
      <dependencies>
        <dependency>
          <groupId>org.example</groupId>
          <artifactId>intellijmaventest</artifactId>
          <version>${libVersion2}</version>
        </dependency>
      </dependencies>"""
    );

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <properties>
                        <libVersion>1.0</libVersion>
                        <libVersion2>2.0</libVersion2>
                      </properties>
                    <modules>
                    <module>module1</module>
                    </modules>
                    """);
    waitForReadingCompletion();
    assertModuleLibDep(mn("project", "module1"), "Maven: org.example:intellijmaventest:2.0");
  }

  @Test
  public void testImportSubProjectWithPropertyInParent() {
    assumeVersionMoreThan("3.0.3");

    assertCorrectVersion();

    createModulePom("module1", """
      <parent>
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>${revision}</version>
      </parent>
      <artifactId>module1</artifactId>
      """);

    importProject("""
                    <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>${revision}</version>
                        <packaging>pom</packaging>
                        <modules>
                            <module>module1</module>
                        </modules>
                        <properties>
                            <revision>1.0-SNAPSHOT</revision>
                        </properties>
                    """);
    waitForReadingCompletion();

    assertModules("project", mn("project", "module1"));
  }
}
