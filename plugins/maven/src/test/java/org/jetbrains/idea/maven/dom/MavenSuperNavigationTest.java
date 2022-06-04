// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom;

import com.intellij.maven.testFramework.MavenDomTestCase;
import com.intellij.maven.testFramework.MavenTestCase;
import org.junit.Test;

public class MavenSuperNavigationTest extends MavenDomTestCase {

  @Test
  public void testNavigationToManagingDependencyWithoutModules() {
    configureProjectPom(
      "<groupId>test</groupId>" +
      "<artifactId>project</artifactId>" +
      "<version>1</version>" +

      "<dependencyManagement>" +
      "  <dependencies>" +
      "    <dependency>" +
      "      <groupId>junit</groupId>" +
      "      <artifactId>junit</artifactId>" +
      "      <version>4.0</version>" +
      "    </dependency>" +
      "  </dependencies>" +
      "</dependencyManagement>" +

      "<dependencies>" +
      "  <dependency>" +
      "    <groupId>junit</groupId>" +
      "    <artifactId>junit<caret></artifactId>" +
      "  </dependency>" +
      "</dependencies>");

    myFixture.performEditorAction("GotoSuperMethod");

    myFixture.checkResultWithInlays(
      MavenTestCase.createPomXml(
        "<groupId>test</groupId>" +
        "<artifactId>project</artifactId>" +
        "<version>1</version>" +

        "<dependencyManagement>" +
        "  <dependencies>" +
        "    <caret><dependency>" +
        "      <groupId>junit</groupId>" +
        "      <artifactId>junit</artifactId>" +
        "      <version>4.0</version>" +
        "    </dependency>" +
        "  </dependencies>" +
        "</dependencyManagement>" +

        "<dependencies>" +
        "  <dependency>" +
        "    <groupId>junit</groupId>" +
        "    <artifactId>junit</artifactId>" +
        "  </dependency>" +
        "</dependencies>"));
  }

  @Test
  public void testNavigationToManagingPluginWithoutModules() {
    configureProjectPom(
      "<groupId>test</groupId>" +
      "<artifactId>project</artifactId>" +
      "<version>1</version>" +
      "<build>" +
      "  <pluginManagement>" +
      "    <plugins>" +
      "      <plugin>" +
      "        <groupId>org.apache.maven.plugins</groupId>" +
      "        <artifactId>maven-compiler-plugin</artifactId>" +
      "      </plugin>" +
      "    </plugins>" +
      "  </pluginManagement>" +
      "  <plugins>" +
      "    <plugin>" +
      "      <gro<caret>upId>org.apache.maven.plugins</groupId>" +
      "      <artifactId>maven-compiler-plugin</artifactId>" +
      "    </plugin>" +
      "  </plugins>" +
      "</build>"
    );

    myFixture.performEditorAction("GotoSuperMethod");

    myFixture.checkResultWithInlays(
      MavenTestCase.createPomXml(
        "<groupId>test</groupId>" +
        "<artifactId>project</artifactId>" +
        "<version>1</version>" +
        "<build>" +
        "  <pluginManagement>" +
        "    <plugins>" +
        "      <caret><plugin>" +
        "        <groupId>org.apache.maven.plugins</groupId>" +
        "        <artifactId>maven-compiler-plugin</artifactId>" +
        "      </plugin>" +
        "    </plugins>" +
        "  </pluginManagement>" +
        "  <plugins>" +
        "    <plugin>" +
        "      <groupId>org.apache.maven.plugins</groupId>" +
        "      <artifactId>maven-compiler-plugin</artifactId>" +
        "    </plugin>" +
        "  </plugins>" +
        "</build>"
      ));
  }

  @Test
  public void testGotoToParentProject() {
    var parent = createProjectPom(
      "<groupId>test</groupId>" +
      "<artifactId>project</artifactId>" +
      "<version>1</version>" +
      "<packaging>pom</packaging>" +

      "<modules>" +
      "  <module>m1</module>" +
      "</modules>");

    var m1 = createModulePom(
      "m1",
      "<parent>" +
      "  <groupId>test</groupId>" +
      "  <artifactId>project</artifactId>" +
      "  <version><caret>1</version>" +
      "</parent>" +
      "<artifactId>m1</artifactId>");

    configTest(m1);
    myFixture.performEditorAction("GotoSuperMethod");

    var offset = getEditorOffset(parent);
    assertEquals(0, offset);
  }

  @Test
  public void testNavigationToManagingDependencyWithModules() {
    var parent = createProjectPom(
      "<groupId>test</groupId>" +
      "<artifactId>project</artifactId>" +
      "<version>1</version>" +
      "<packaging>pom</packaging>" +

      "<dependencyManagement>" +
      "  <dependencies>" +
      "    <dependency>" +
      "      <groupId>junit</groupId>" +
      "      <artifactId>junit</artifactId>" +
      "      <version>4.0</version>" +
      "    </dependency>" +
      "  </dependencies>" +
      "</dependencyManagement>" +

      "<modules>" +
      "  <module>m1</module>" +
      "</modules>");

    var m1 = createModulePom(
      "m1",
      "<parent>" +
      "  <groupId>test</groupId>" +
      "  <artifactId>project</artifactId>" +
      "  <version>1</version>" +
      "</parent>" +
      "<artifactId>m1</artifactId>" +

      "<dependencies>" +
      "  <dependency>" +
      "    <groupId><caret>junit</groupId>" +
      "    <artifactId>junit<caret></artifactId>" +
      "  </dependency>" +
      "</dependencies>"
    );

    configTest(m1);
    myFixture.performEditorAction("GotoSuperMethod");

    configTest(parent);
    myFixture.checkResultWithInlays(
      MavenTestCase.createPomXml(
        "<groupId>test</groupId>" +
        "<artifactId>project</artifactId>" +
        "<version>1</version>" +
        "<packaging>pom</packaging>" +

        "<dependencyManagement>" +
        "  <dependencies>" +
        "    <caret><dependency>" +
        "      <groupId>junit</groupId>" +
        "      <artifactId>junit</artifactId>" +
        "      <version>4.0</version>" +
        "    </dependency>" +
        "  </dependencies>" +
        "</dependencyManagement>" +

        "<modules>" +
        "  <module>m1</module>" +
        "</modules>"));
  }

  @Test
  public void testNavigationToManagingPluginWithModules() {
    var parent = createProjectPom(
      "<groupId>test</groupId>" +
      "<artifactId>project</artifactId>" +
      "<version>1</version>" +
      "<packaging>pom</packaging>" +

      "<modules>" +
      "  <module>m1</module>" +
      "</modules>" +

      "<build>" +
      "  <pluginManagement>" +
      "    <plugins>" +
      "    	 <plugin>" +
      "  		   <groupId>org.apache.maven.plugins</groupId>" +
      "  		   <artifactId>maven-compiler-plugin</artifactId>" +
      "  		   <version>3.8.1</version>" +
      "  	   </plugin>" +
      "    </plugins>" +
      "  </pluginManagement>" +
      "</build>"
    );

    var m1 = createModulePom(
      "m1",
      "<parent>" +
      "  <groupId>test</groupId>" +
      "  <artifactId>project</artifactId>" +
      "  <version>1</version>" +
      "</parent>" +
      "<artifactId>m1</artifactId>" +

      "<build>" +
      "  <plugins>" +
      "  	 <plugin>" +
      "		   <groupId><caret>org.apache.maven.plugins</groupId>" +
      "		   <artifactId>maven-compiler-plugin</artifactId>" +
      "	   </plugin>" +
      "  </plugins>" +
      "</build>"
    );

    configTest(m1);
    myFixture.performEditorAction("GotoSuperMethod");

    configTest(parent);
    myFixture.checkResultWithInlays(
      MavenTestCase.createPomXml(
        "<groupId>test</groupId>" +
        "<artifactId>project</artifactId>" +
        "<version>1</version>" +
        "<packaging>pom</packaging>" +

        "<modules>" +
        "  <module>m1</module>" +
        "</modules>" +

        "<build>" +
        "  <pluginManagement>" +
        "    <plugins>" +
        "    	 <caret><plugin>" +
        "  		   <groupId>org.apache.maven.plugins</groupId>" +
        "  		   <artifactId>maven-compiler-plugin</artifactId>" +
        "  		   <version>3.8.1</version>" +
        "  	   </plugin>" +
        "    </plugins>" +
        "  </pluginManagement>" +
        "</build>"
      ));
  }
}
