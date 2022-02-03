// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectNotificationAware;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.maven.testFramework.MavenDomTestCase;
import org.junit.Test;

public class CustomPomFileNameTest extends MavenDomTestCase {

  @Test
  public void testCustomPomFileName() throws Exception {
    createProjectSubFile("m1/customName.xml", createPomXml(
      "<artifactId>m1</artifactId>" +
      "<version>1</version>" +

      "<parent>" +
      "  <groupId>test</groupId>" +
      "  <artifactId>project</artifactId>" +
      "  <version>1</version>" +
      "</parent>"));

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>pom</packaging>" +

                  "<modules>" +
                  "  <module>m1/customName.xml</module>" +
                  "</modules>");

    assertModules("project", "m1");
  }

  @Test
  public void testFolderNameWithXmlExtension() throws Exception {
    createProjectSubFile("customName.xml/pom.xml", createPomXml(
      "<artifactId>m1</artifactId>" +
      "<version>1</version>" +

      "<parent>" +
      "  <groupId>test</groupId>" +
      "  <artifactId>project</artifactId>" +
      "  <version>1</version>" +
      "</parent>"));

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>pom</packaging>" +

                  "<modules>" +
                  "  <module>customName.xml</module>" +
                  "</modules>");

    assertModules("project", "m1");
  }

  @Test
  public void testModuleCompletion() throws Exception {
    createProjectSubFile("m1/customPom.xml", createPomXml(
      "<artifactId>m1</artifactId>" +
      "<version>1</version>" +

      "<parent>" +
      "  <groupId>test</groupId>" +
      "  <artifactId>project</artifactId>" +
      "  <version>1</version>" +
      "</parent>"));

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>pom</packaging>" +

                  "<modules>" +
                  "  <module>m1/customPom.xml</module>" +
                  "</modules>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module><caret></module>" +
                     "</modules>");

    assertCompletionVariants(myProjectPom, "m1/customPom.xml");
  }

  @Test
  public void testParentCompletion() throws Exception {
    createProjectSubFile("m1/customPom.xml", createPomXml(
      "<artifactId>m1</artifactId>" +
      "<version>1</version>" +
      "<packaging>pom</packaging>" +

      "<parent>" +
      "  <groupId>test</groupId>" +
      "  <artifactId>project</artifactId>" +
      "  <version>1</version>" +
      "</parent>" +

      "<modules>" +
      "  <module>m2</module>" +
      "</modules>"));

    createProjectSubFile("m1/m2/pom.xml", createPomXml(
      "<artifactId>m2</artifactId>" +
      "<version>1</version>" +

      "<parent>" +
      "  <groupId>test</groupId>" +
      "  <artifactId>m1</artifactId>" +
      "  <version>1</version>" +
      "  <relativePath>../customPom.xml</relativePath>" +
      "</parent>"));

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>pom</packaging>" +

                  "<modules>" +
                  "  <module>m1/customPom.xml</module>" +
                  "</modules>");

    VirtualFile m2 = createProjectSubFile("m1/m2/pom.xml", createPomXml(
      "<artifactId>m2</artifactId>" +
      "<version>1</version>" +

      "<parent>" +
      "  <groupId>test</groupId>" +
      "  <artifactId>project</artifactId>" +
      "  <version>1</version>" +
      "  <relativePath>../<caret></relativePath>" +
      "</parent>"));

    assertCompletionVariants(m2, "m2", "customPom.xml");
  }

  @Test
  public void testReimport() throws Exception {
    createProjectSubFile("m1/customName.xml", createPomXml(
      "<artifactId>m1</artifactId>" +
      "<version>1</version>" +

      "<parent>" +
      "  <groupId>test</groupId>" +
      "  <artifactId>project</artifactId>" +
      "  <version>1</version>" +
      "</parent>" +

      "<dependencies>" +
      "  <dependency>" +
      "    <groupId>junit</groupId>" +
      "    <artifactId>junit</artifactId>" +
      "    <version>4.0</version>" +
      "    <scope>test</scope>" +
      "  </dependency>" +
      "</dependencies>"));

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>pom</packaging>" +

                  "<modules>" +
                  "  <module>m1/customName.xml</module>" +
                  "</modules>");

    myProjectsManager.performScheduledImportInTests();
    assertFalse(myProjectsManager.hasScheduledImportsInTests());

    myProjectsManager.enableAutoImportInTests();
    VirtualFile m1 = createProjectSubFile("m1/customName.xml", createPomXml(
      "<artifactId>m1</artifactId>" +
      "<version>1</version>" +

      "<parent>" +
      "  <groupId>test</groupId>" +
      "  <artifactId>project</artifactId>" +
      "  <version>1</version>" +
      "</parent>" +

      "<dependencies>" +
      "  <dependency>" +
      "    <groupId>junit</groupId>" +
      "    <artifactId>junit</artifactId>" +
      "    <version>4.<caret>0</version>" +
      "    <scope>test</scope>" +
      "  </dependency>" +
      "</dependencies>"));
    type(m1, '1');

    assertTrue(ExternalSystemProjectNotificationAware.getInstance(myProject).isNotificationVisible());

    importProject();
    assertTrue(myProjectsManager.hasScheduledImportsInTests());
  }

  @Test
  public void testCustomPomFileNamePom() throws Exception {
    createProjectSubFile("m1/customName.pom", createPomXml(
      "<artifactId>m1</artifactId>" +
      "<version>1</version>" +

      "<parent>" +
      "  <groupId>test</groupId>" +
      "  <artifactId>project</artifactId>" +
      "  <version>1</version>" +
      "</parent>"));

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>pom</packaging>" +

                  "<modules>" +
                  "  <module>m1/customName.pom</module>" +
                  "</modules>");

    assertModules("project", "m1");
  }
}
