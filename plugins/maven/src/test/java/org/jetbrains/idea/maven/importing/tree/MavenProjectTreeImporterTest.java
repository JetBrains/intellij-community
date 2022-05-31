// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree;

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import org.jetbrains.idea.maven.importing.MavenProjectImporter;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.junit.Test;

import java.io.IOException;

public class MavenProjectTreeImporterTest extends MavenMultiVersionImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    MavenProjectsManager.getInstance(myProject).getImportingSettings().setImportToTreeStructure(true);
  }

  @Test
  public void testImportWithTestSourceAndTestTargetVersion() throws IOException {
    createModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>" +
                    "<parent>" +
                    "  <groupId>test</groupId>" +
                    "  <artifactId>project</artifactId>" +
                    "  <version>1</version>" +
                    "</parent>");
    createProjectSubFile("m1/src/main/java/com/A.java", "package com; class A {}");
    createProjectSubFile("m1/src/test/java/com/ATest.java", "package com; class ATest {}");

    createProjectSubFile("m1/src/main/resources/test.txt", "resource");
    createProjectSubFile("m1/src/test/resources/test.txt", "test resource");

    createModulePom("m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>" +

                    "<parent>" +
                    "  <groupId>test</groupId>" +
                    "  <artifactId>project</artifactId>" +
                    "  <version>1</version>" +
                    "</parent>" +

                    "<dependencies>" +
                    "  <dependency>" +
                    "    <groupId>test</groupId>" +
                    "    <artifactId>m1</artifactId>" +
                    "    <version>1</version>" +
                    "  </dependency>" +
                    "</dependencies>");
    createProjectSubFile("m2/src/main/java/com/B.java", "package com; class B {}");
    createProjectSubFile("m2/src/test/java/com/BTest.java", "package com; class BTest {}");
    createProjectSubFile("m2/src/main/resources/test.txt", "resource");
    createProjectSubFile("m2/src/test/resources/test.txt", "test resource");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>pom</packaging>" +

                  "<properties>" +
                  "  <maven.compiler.source>8</maven.compiler.source>" +
                  "  <maven.compiler.target>8</maven.compiler.target>" +
                  "  <maven.compiler.testSource>11</maven.compiler.testSource>" +
                  "  <maven.compiler.testTarget>11</maven.compiler.testTarget>" +
                  "</properties>" +

                  "<modules>" +
                  "  <module>m1</module>" +
                  "  <module>m2</module>" +
                  "</modules>");

    assertModules("project",
                  mn("project", "m1"),
                  mn("project", "m2"),
                  mn("project", "m1.main"),
                  mn("project", "m1.test"),
                  mn("project", "m2.main"),
                  mn("project", "m2.test"));
    assertModuleModuleDeps(mn("project", "m1.test"), mn("project", "m1.main"));
    assertModuleModuleDeps(mn("project", "m2.test"), mn("project", "m2.main"), mn("project", "m1.main"));
    assertModuleModuleDeps(mn("project", "m2.main"), mn("project", "m1.main"));

    assertSources("project");
    assertSources(mn("project", "m1"));
    assertSources(mn("project", "m2"));
    assertTestSources("project");
    assertResources("project");
    assertTestResources("project");

    assertSources(mn("project", "m1.main"), "java");
    assertSources(mn("project", "m2.main"), "java");
    assertTestSources(mn("project", "m1.test"), "java");
    assertTestSources(mn("project", "m2.test"), "java");

    assertResources(mn("project", "m1.main"), "resources");
    assertTestResources(mn("project", "m1.test"), "resources");
    assertResources(mn("project", "m2.main"), "resources");
    assertTestResources(mn("project", "m2.test"), "resources");

    assertExcludes("project", "target");
    assertExcludes(mn("project", "m1"), "target");
    assertExcludes(mn("project", "m2"), "target");
    assertExcludes(mn("project", "m1.main"));
    assertExcludes(mn("project", "m2.main"));
    assertExcludes(mn("project", "m1.test"));
    assertExcludes(mn("project", "m2.test"));
  }

  @Test
  public void testMultiplyContentRootsWithGeneratedSources() throws IOException {
    createProjectSubFile("target/generated-sources/src1/com/GenA.java", "package com; class GenA {}");
    createProjectSubFile("src/main/java/com/A.java", "package com; class A {}");
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<properties>" +
                  "  <maven.compiler.source>8</maven.compiler.source>" +
                  "  <maven.compiler.target>8</maven.compiler.target>" +
                  "  <maven.compiler.testSource>11</maven.compiler.testSource>" +
                  "  <maven.compiler.testTarget>11</maven.compiler.testTarget>" +
                  "</properties>"
    );

    assertModules("project", "project.main", "project.test");
    assertModuleModuleDeps("project.test", "project.main");

    if (MavenProjectImporter.isImportToWorkspaceModelEnabled()) {
      assertContentRoots("project.main",
                         getProjectPath() + "/src/main",
                         getProjectPath() + "/target/generated-sources");
    }
    else {
      assertContentRoots("project.main",
                         getProjectPath() + "/src/main",
                         getProjectPath() + "/target/generated-sources/src1"); // bug in implementation
    }
  }

  @Test
  public void testContentRootOutsideOfModuleDir() throws Exception {
    createModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>" +
                    "<parent>" +
                    "  <groupId>test</groupId>" +
                    "  <artifactId>project</artifactId>" +
                    "  <version>1</version>" +
                    "</parent>" +

                    "<build>" +
                    "  <sourceDirectory>../custom-sources</sourceDirectory>" +
                    "</build>");

    createProjectSubFile("custom-sources/com/CustomSource.java", "package com; class CustomSource {}");
    createProjectSubFile("m1/src/main/resources/test.txt", "resource");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>pom</packaging>" +

                  "<properties>" +
                  "  <maven.compiler.source>8</maven.compiler.source>" +
                  "  <maven.compiler.target>8</maven.compiler.target>" +
                  "  <maven.compiler.testSource>11</maven.compiler.testSource>" +
                  "  <maven.compiler.testTarget>11</maven.compiler.testTarget>" +
                  "</properties>" +

                  "<modules>" +
                  "  <module>m1</module>" +
                  "</modules>");

    assertModules("project", mn("project", "m1"), mn("project", "m1.main"), mn("project", "m1.test"));
    assertContentRoots(mn("project", "m1.main"), getProjectPath() + "/m1/src/main", getProjectPath() + "/custom-sources");
  }

  @Test
  public void testReleaseCompilerProperty() throws IOException {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<properties>" +
                  "  <maven.compiler.release>8</maven.compiler.release>" +
                  "  <maven.compiler.testRelease>11</maven.compiler.testRelease>" +
                  "</properties>" +
                  "" +
                  " <build>\n" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <artifactId>maven-compiler-plugin</artifactId>" +
                  "      <version>3.10.0</version>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>"
    );

    assertModules("project", "project.main", "project.test");
  }
}
