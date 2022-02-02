// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree;

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class MavenProjectTreeImporterTest extends MavenMultiVersionImportingTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Registry.get("maven.import.tree.structure").setValue(true);
  }

  @Test
  public void testSimpleImport() {
    createModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>");
    createModulePom("m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>" +
                    "<dependencies>" +
                    "  <dependency>" +
                    "    <groupId>test</groupId>" +
                    "    <artifactId>m1</artifactId>" +
                    "    <version>1</version>" +
                    "  </dependency>\n" +
                    "</dependencies>");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>pom</packaging>" +
                  "<modules>" +
                  "  <module>m1</module>" +
                  "  <module>m2</module>" +
                  "</modules>");
    assertModules("project", "m1", "m2");
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

    assertModules("project", "project.m1", "project.m2", "project.m1.main", "project.m1.test", "project.m2.main", "project.m2.test");
    assertModuleModuleDeps("project.m1.test", "project.m1.main");
    assertModuleModuleDeps("project.m2.test", "project.m2.main", "project.m1.main");
    assertModuleModuleDeps("project.m2.main", "project.m1.main");

    assertSources("project");
    assertSources("project.m1");
    assertSources("project.m2");
    assertTestSources("project");
    assertResources("project");
    assertTestResources("project");

    assertSources("project.m1.main", "java");
    assertSources("project.m2.main", "java");
    assertTestSources("project.m1.test", "java");
    assertTestSources("project.m2.test", "java");

    assertResources("project.m1.main", "resources");
    assertTestResources("project.m1.test", "resources");
    assertResources("project.m2.main");
    assertTestResources("project.m2.test");

    assertExcludes("project", "target");
    assertExcludes("project.m1", "target");
    assertExcludes("project.m2", "target");
    assertExcludes("project.m1.main");
    assertExcludes("project.m2.main");
    assertExcludes("project.m1.test");
    assertExcludes("project.m2.test");
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

    List<String> contentRoots = ContainerUtil.map(getContentRoots("project.main"), c -> c.getUrl());
    Assert.assertTrue(contentRoots.stream().anyMatch(r -> r.contains("src/main")));
    Assert.assertTrue(contentRoots.stream().anyMatch(r -> r.contains("target/generated-sources/src1")));
  }
}
