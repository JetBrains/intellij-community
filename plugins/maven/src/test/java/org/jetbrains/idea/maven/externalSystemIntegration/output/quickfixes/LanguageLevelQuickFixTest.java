// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.idea.maven.dom.MavenDomTestCase;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.junit.Test;

public class LanguageLevelQuickFixTest extends MavenDomTestCase {

  @Test
  public void testPropertyFix1() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>p1</artifactId>" +
                  "<packaging>pom</packaging>" +
                  "<version>1</version>");
    MavenProject mavenProject = MavenProjectsManager.getInstance(myProject).findProject(new MavenId("test:p1:1"));
    LanguageLevelQuickFixFactory.getInstance(myProject, mavenProject).perform(LanguageLevel.JDK_11);
    XmlTag tag = findTag("project.properties");
    assertNotNull(tag);
    assertSize(2, tag.getSubTags());
    assertEquals("11", tag.getSubTags()[0].getValue().getText());
    assertEquals("11", tag.getSubTags()[1].getValue().getText());
  }

  @Test
  public void testPropertyFix2() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>p1</artifactId>" +
                  "<packaging>pom</packaging>" +
                  "<version>1</version>" +
                  "<properties>" +
                  "        <maven.compiler.source>5</maven.compiler.source>" +
                  "        <maven.compiler.target>5</maven.compiler.target>" +
                  "</properties>");
    MavenProject mavenProject = MavenProjectsManager.getInstance(myProject).findProject(new MavenId("test:p1:1"));
    LanguageLevelQuickFixFactory.getInstance(myProject, mavenProject).perform(LanguageLevel.JDK_11);
    XmlTag tag = findTag("project.properties");
    assertNotNull(tag);
    assertSize(2, tag.getSubTags());
    assertEquals("11", tag.getSubTags()[0].getValue().getText());
    assertEquals("11", tag.getSubTags()[1].getValue().getText());
  }

  @Test
  public void testPropertyFix3() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>p1</artifactId>" +
                  "<packaging>pom</packaging>" +
                  "<version>1</version>" +
                  "<build>" +
                  "        <plugins>" +
                  "            <plugin>" +
                  "                <groupId>org.apache.maven.plugins</groupId>" +
                  "                <artifactId>maven-compiler-plugin</artifactId>" +
                  "                   <configuration>" +
                  "                   </configuration>" +
                  "            </plugin>" +
                  "        </plugins>" +
                  "    </build>");
    MavenProject mavenProject = MavenProjectsManager.getInstance(myProject).findProject(new MavenId("test:p1:1"));
    LanguageLevelQuickFixFactory.getInstance(myProject, mavenProject).perform(LanguageLevel.JDK_11);
    XmlTag tag = findTag("project.properties");
    assertNotNull(tag);
    assertSize(2, tag.getSubTags());
    assertEquals("11", tag.getSubTags()[0].getValue().getText());
    assertEquals("11", tag.getSubTags()[1].getValue().getText());
  }

  @Test
  public void testPluginFix() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>p1</artifactId>" +
                  "<packaging>pom</packaging>" +
                  "<version>1</version>" +
                  "<build>" +
                  "        <plugins>" +
                  "            <plugin>" +
                  "                <groupId>org.apache.maven.plugins</groupId>" +
                  "                <artifactId>maven-compiler-plugin</artifactId>" +
                  "                   <configuration>" +
                  "                     <source>5</source>" +
                  "                     <target>5</target>" +
                  "                   </configuration>" +
                  "            </plugin>" +
                  "        </plugins>" +
                  "    </build>");
    MavenProject mavenProject = MavenProjectsManager.getInstance(myProject).findProject(new MavenId("test:p1:1"));
    LanguageLevelQuickFixFactory.getInstance(myProject, mavenProject).perform(LanguageLevel.JDK_11);
    XmlTag tag = findTag("project.build.plugins.plugin.configuration");
    assertNotNull(tag);
    assertSize(2, tag.getSubTags());
    assertEquals("11", tag.getSubTags()[0].getValue().getText());
    assertEquals("11", tag.getSubTags()[1].getValue().getText());
  }

  @Test
  public void testPropertyFixChild() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>p1</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +
                     "<modules>" +
                     "  <module>p2</module>" +
                     "</modules>");
    createModulePom("p2",
                    "<parent>" +
                    "  <groupId>test</groupId>" +
                    "  <artifactId>p1</artifactId>" +
                    "  <version>1</version>" +
                    "</parent>" +
                    "<groupId>test</groupId>" +
                    "<artifactId>p2</artifactId>" +
                    "<version>1</version>");
    importProject();
    MavenProject mavenProject = MavenProjectsManager.getInstance(myProject).findProject(new MavenId("test:p2:1"));
    LanguageLevelQuickFixFactory.getInstance(myProject, mavenProject).perform(LanguageLevel.JDK_11);
    XmlTag tag = findTag("project.properties");
    assertNotNull(tag);
    assertSize(2, tag.getSubTags());
    assertEquals("11", tag.getSubTags()[0].getValue().getText());
    assertEquals("11", tag.getSubTags()[1].getValue().getText());
  }

  @Test
  public void testPropertyFixChild2() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>p1</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +
                     "<modules>" +
                     "  <module>p2</module>" +
                     "</modules>");
    VirtualFile p2 = createModulePom("p2",
                                     "<parent>" +
                                     "  <groupId>test</groupId>" +
                                     "  <artifactId>p1</artifactId>" +
                                     "  <version>1</version>" +
                                     "</parent>" +
                                     "<groupId>test</groupId>" +
                                     "<artifactId>p2</artifactId>" +
                                     "<version>1</version>" +
                                     "<properties>" +
                                     "        <maven.compiler.source>5</maven.compiler.source>" +
                                     "        <maven.compiler.target>5</maven.compiler.target>" +
                                     "</properties>");
    importProject();
    MavenProject mavenProject = MavenProjectsManager.getInstance(myProject).findProject(new MavenId("test:p2:1"));
    LanguageLevelQuickFixFactory.getInstance(myProject, mavenProject).perform(LanguageLevel.JDK_11);
    XmlTag tag = findTag(p2, "project.properties");
    assertNotNull(tag);
    assertSize(2, tag.getSubTags());
    assertEquals("11", tag.getSubTags()[0].getValue().getText());
    assertEquals("11", tag.getSubTags()[1].getValue().getText());
  }

  @Test
  public void testPluginFixChild() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>p1</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +
                     "<modules>" +
                     "  <module>p2</module>" +
                     "</modules>");
    VirtualFile p2 = createModulePom("p2",
                                     "<parent>" +
                                     "  <groupId>test</groupId>" +
                                     "  <artifactId>p1</artifactId>" +
                                     "  <version>1</version>" +
                                     "</parent>" +
                                     "<groupId>test</groupId>" +
                                     "<artifactId>p2</artifactId>" +
                                     "<version>1</version>" +
                                     "<build>" +
                                     "        <plugins>" +
                                     "            <plugin>" +
                                     "                <groupId>org.apache.maven.plugins</groupId>" +
                                     "                <artifactId>maven-compiler-plugin</artifactId>" +
                                     "                   <configuration>" +
                                     "                     <source>5</source>" +
                                     "                     <target>5</target>" +
                                     "                   </configuration>" +
                                     "            </plugin>" +
                                     "        </plugins>" +
                                     "    </build>");
    importProject();
    MavenProject mavenProject = MavenProjectsManager.getInstance(myProject).findProject(new MavenId("test:p2:1"));
    LanguageLevelQuickFixFactory.getInstance(myProject, mavenProject).perform(LanguageLevel.JDK_11);
    XmlTag tag = findTag(p2, "project.build.plugins.plugin.configuration");
    assertNotNull(tag);
    assertSize(2, tag.getSubTags());
    assertEquals("11", tag.getSubTags()[0].getValue().getText());
    assertEquals("11", tag.getSubTags()[1].getValue().getText());
  }
}