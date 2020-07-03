// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.compatibility;

import org.jetbrains.idea.maven.server.MavenServerManager;
import org.junit.Test;

public class MavenCompatibilityProjectImportingTest extends MavenCompatibilityTest {
  @Test
  public void testSmokeImport() {
    assertCorrectVersion();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");


    assertModules("project");
  }

  private void assertCorrectVersion() {
    assertEquals(myMavenVersion, MavenServerManager.getInstance().getConnector(myProject).getMavenDistribution().getVersion());
  }

  @Test
  public void testInterpolateModel() {
    assertCorrectVersion();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<properties>\n" +
                  "    <junitVersion>4.0</junitVersion>" +
                  "  </properties>" +
                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>${junitVersion}</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModules("project");

    assertModuleLibDep("project", "Maven: junit:junit:4.0");
  }

  @Test
  public void testImportProjectProperties() {
    assumeVersionMoreThan("3.0.3");

    assertCorrectVersion();

    createModulePom("module1", "<parent>" +
                               "<groupId>test</groupId>" +
                               "<artifactId>project</artifactId>" +
                               "<version>1</version>" +
                               "</parent>" +
                               "<artifactId>module1</artifactId>" +
                               "<dependencies>" +
                               "  <dependency>" +
                               "    <groupId>junit</groupId>" +
                               "    <artifactId>junit</artifactId>" +
                               "    <version>${junitVersion}</version>" +
                               "  </dependency>" +
                               "</dependencies>"
    );

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>pom</packaging>" +

                  "<properties>" +
                  "    <junitVersion>4.0</junitVersion>" +
                  "  </properties>" +
                  "<modules>" +
                  "<module>module1</module>" +
                  "</modules>");

    waitForReadingCompletion();
    assertModules("project", "module1");

    assertModuleLibDep("module1", "Maven: junit:junit:4.0");
  }

  @Test
  public void testImportAddedProjectProperties() {
    assumeVersionMoreThan("3.0.3");
    assumeVersionNot("3.6.0");

    assertCorrectVersion();

    createModulePom("module1", "<parent>" +
                               "<groupId>test</groupId>" +
                               "<artifactId>project</artifactId>" +
                               "<version>1</version>" +
                               "</parent>" +
                               "<artifactId>module1</artifactId>" +
                               "<dependencies>" +
                               "  <dependency>" +
                               "    <groupId>junit</groupId>" +
                               "    <artifactId>junit</artifactId>" +
                               "    <version>${junitVersion}</version>" +
                               "  </dependency>" +
                               "</dependencies>"
    );

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>pom</packaging>" +

                  "<properties>" +
                  "    <junitVersion>4.0</junitVersion>" +
                  "  </properties>" +
                  "<modules>" +
                  "<module>module1</module>" +
                  "</modules>");

    waitForReadingCompletion();
    assertModules("project", "module1");

    assertModuleLibDep("module1", "Maven: junit:junit:4.0");

      /*myWrapperTestFixture.tearDown();
      myWrapperTestFixture.setUp();*/

    createModulePom("module1", "<parent>" +
                               "<groupId>test</groupId>" +
                               "<artifactId>project</artifactId>" +
                               "<version>1</version>" +
                               "</parent>" +
                               "<artifactId>module1</artifactId>" +
                               "<dependencies>" +
                               "  <dependency>" +
                               "    <groupId>junit</groupId>" +
                               "    <artifactId>junit</artifactId>" +
                               "    <version>${junitVersion2}</version>" +
                               "  </dependency>" +
                               "</dependencies>"
    );

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>pom</packaging>" +

                  "<properties>" +
                  "    <junitVersion>4.0</junitVersion>" +
                  "    <junitVersion2>4.1</junitVersion2>" +
                  "  </properties>" +
                  "<modules>" +
                  "<module>module1</module>" +
                  "</modules>");
    waitForReadingCompletion();
    assertModuleLibDep("module1", "Maven: junit:junit:4.1");
  }

  @Test
  public void testImportSubProjectWithPropertyInParent() {
    assumeVersionMoreThan("3.0.3");

    assertCorrectVersion();

    createModulePom("module1", "<parent>" +
                               "<groupId>test</groupId>" +
                               "<artifactId>project</artifactId>" +
                               "<version>${revision}</version>" +
                               "</parent>" +
                               "<artifactId>module1</artifactId>");

    importProject("<groupId>test</groupId>" +
                  "    <artifactId>project</artifactId>" +
                  "    <version>${revision}</version>" +
                  "    <packaging>pom</packaging>" +
                  "    <modules>" +
                  "        <module>module1</module>\n" +
                  "    </modules>" +
                  "    <properties>" +
                  "        <revision>1.0-SNAPSHOT</revision>" +
                  "    </properties>");
    waitForReadingCompletion();

    assertModules("project", "module1");
  }
}
