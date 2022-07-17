// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes

import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.RunAll
import com.intellij.maven.testFramework.MavenDomTestCase
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.junit.Test

class LanguageLevelQuickFixTest : MavenDomTestCase() {

  override fun tearDown() {
    RunAll.runAll(
      { stopMavenImportManager() },
      { super.tearDown() }
    )
  }

  @Test
  fun `test property empty quick fix`() {
    importProject("""<groupId>test</groupId>
                  <artifactId>p1</artifactId>
                  <packaging>pom</packaging>
                  <version>1</version>"""
    )
    val mavenProject = MavenProjectsManager.getInstance(myProject).findProject(MavenId("test:p1:1"))
    LanguageLevelQuickFixFactory.getInstance(myProject, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
    val tag = findTag("project.properties")
    assertNotNull(tag)
    assertSize(2, tag.subTags)
    assertEquals("11", tag.subTags[0].value.text)
    assertEquals("11", tag.subTags[1].value.text)
  }

  @Test
  fun `test property old value quick fix`() {
    importProject("""<groupId>test</groupId>
                  <artifactId>p1</artifactId>
                  <packaging>pom</packaging>
                  <version>1</version>
                  <properties>
                          <maven.compiler.source>5</maven.compiler.source>
                          <maven.compiler.target>5</maven.compiler.target>
                  </properties>""")
    val mavenProject = MavenProjectsManager.getInstance(myProject).findProject(MavenId("test:p1:1"))
    LanguageLevelQuickFixFactory.getInstance(myProject, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
    val tag = findTag("project.properties")
    assertNotNull(tag)
    assertSize(2, tag.subTags)
    assertEquals("11", tag.subTags[0].value.text)
    assertEquals("11", tag.subTags[1].value.text)
  }

  @Test
  fun `test plugin configuration empty value quick fix`() {
    importProject("""<groupId>test</groupId>
                  <artifactId>p1</artifactId>
                  <packaging>pom</packaging>
                  <version>1</version>
                  <build>
                          <plugins>
                              <plugin>
                                  <groupId>org.apache.maven.plugins</groupId>
                                  <artifactId>maven-compiler-plugin</artifactId>
                                     <configuration>
                                     </configuration>
                              </plugin>
                          </plugins>
                      </build>""")
    val mavenProject = MavenProjectsManager.getInstance(myProject).findProject(MavenId("test:p1:1"))
    LanguageLevelQuickFixFactory.getInstance(myProject, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
    val tag = findTag("project.properties")
    assertNotNull(tag)
    assertSize(2, tag.subTags)
    assertEquals("11", tag.subTags[0].value.text)
    assertEquals("11", tag.subTags[1].value.text)
  }

  @Test
  fun `test plugin configuration old value quick fix`() {
    importProject("""<groupId>test</groupId>
                  <artifactId>p1</artifactId>
                  <packaging>pom</packaging>
                  <version>1</version>
                  <build>
                    <plugins>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-compiler-plugin</artifactId>
                               <configuration>
                                 <source>5</source>
                                 <target>5</target>
                               </configuration>
                        </plugin>
                    </plugins>
                  </build>""")
    val mavenProject = MavenProjectsManager.getInstance(myProject).findProject(MavenId("test:p1:1"))
    LanguageLevelQuickFixFactory.getInstance(myProject, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
    val tag = findTag("project.build.plugins.plugin.configuration")
    assertNotNull(tag)
    assertSize(2, tag.subTags)
    assertEquals("11", tag.subTags[0].value.text)
    assertEquals("11", tag.subTags[1].value.text)
  }

  @Test
  fun `test multi module project property empty value quick fix`() {
    createProjectPom("""<groupId>test</groupId>
                     <artifactId>p1</artifactId>
                     <packaging>pom</packaging>
                     <version>1</version>
                     <modules>
                       <module>p2</module>
                     </modules>""")
    createModulePom("p2",
                    """<parent>
                      <groupId>test</groupId>
                      <artifactId>p1</artifactId>
                      <version>1</version>
                    </parent>
                    <groupId>test</groupId>
                    <artifactId>p2</artifactId>
                    <version>1</version>""")
    importProject()
    val mavenProject = MavenProjectsManager.getInstance(myProject).findProject(MavenId("test:p2:1"))
    LanguageLevelQuickFixFactory.getInstance(myProject, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
    val tag = findTag("project.properties")
    assertNotNull(tag)
    assertSize(2, tag.subTags)
    assertEquals("11", tag.subTags[0].value.text)
    assertEquals("11", tag.subTags[1].value.text)
  }

  @Test
  fun `test multi module project property old value quick fix`() {
    createProjectPom(("""<groupId>test</groupId>
                      <artifactId>p1</artifactId>
                      <packaging>pom</packaging>
                      <version>1</version>
                      <modules>
                        <module>p2</module>
                      </modules>"""))
    val p2 = createModulePom("p2",
                             ("""
                             <parent>
                               <groupId>test</groupId>
                               <artifactId>p1</artifactId>
                               <version>1</version>
                             </parent>
                             <groupId>test</groupId>
                             <artifactId>p2</artifactId>
                             <version>1</version>
                             <properties>
                               <maven.compiler.source>5</maven.compiler.source>
                               <maven.compiler.target>5</maven.compiler.target>
                             </properties>"""))
    importProject()
    val mavenProject = MavenProjectsManager.getInstance(myProject).findProject(MavenId("test:p2:1"))
    LanguageLevelQuickFixFactory.getInstance(myProject, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
    val tag = findTag(p2, "project.properties")
    assertNotNull(tag)
    assertSize(2, tag.subTags)
    assertEquals("11", tag.subTags[0].value.text)
    assertEquals("11", tag.subTags[1].value.text)
  }

  @Test
  fun `test multi module project plugin old value quick fix`() {
    createProjectPom(("""<groupId>test</groupId>
                      <artifactId>p1</artifactId>
                      <packaging>pom</packaging>
                      <version>1</version>
                      <modules>
                        <module>p2</module>
                      </modules>"""))
    val p2 = createModulePom("p2",
                             ("""
                             <parent>
                               <groupId>test</groupId>
                               <artifactId>p1</artifactId>
                               <version>1</version>
                             </parent>
                             <groupId>test</groupId>
                             <artifactId>p2</artifactId>
                             <version>1</version>
                             <build>
                               <plugins>
                                 <plugin>
                                   <groupId>org.apache.maven.plugins</groupId>
                                   <artifactId>maven-compiler-plugin</artifactId>
                                    <configuration>
                                      <source>5</source>
                                      <target>5</target>
                                    </configuration>
                                 </plugin>
                               </plugins>
                             </build>"""))
    importProject()
    val mavenProject = MavenProjectsManager.getInstance(myProject).findProject(MavenId("test:p2:1"))
    LanguageLevelQuickFixFactory.getInstance(myProject, (mavenProject)!!)!!.perform(LanguageLevel.JDK_11)
    val tag = findTag(p2, "project.build.plugins.plugin.configuration")
    assertNotNull(tag)
    assertSize(2, tag.subTags)
    assertEquals("11", tag.subTags[0].value.text)
    assertEquals("11", tag.subTags[1].value.text)
  }

  @Test
  fun `test target property empty quick fix`() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>p1</artifactId>" +
                  "<packaging>pom</packaging>" +
                  "<version>1</version>" +
                  "<properties>" +
                  "        <maven.compiler.source>11</maven.compiler.source>" +
                  "</properties>")
    val mavenProject = MavenProjectsManager.getInstance(myProject).findProject(MavenId("test:p1:1"))
    LanguageLevelQuickFixFactory.getTargetInstance(myProject, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
    val tag = findTag("project.properties")
    assertNotNull(tag)
    assertSize(2, tag.subTags)
    assertEquals("11", tag.subTags[0].value.text)
    assertEquals("11", tag.subTags[1].value.text)
  }

  @Test
  fun `test target property bad value quick fix`() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>p1</artifactId>" +
                  "<packaging>pom</packaging>" +
                  "<version>1</version>" +
                  "<properties>" +
                  "        <maven.compiler.source>11</maven.compiler.source>" +
                  "        <maven.compiler.target>5</maven.compiler.target>" +
                  "</properties>")
    val mavenProject = MavenProjectsManager.getInstance(myProject).findProject(MavenId("test:p1:1"))
    LanguageLevelQuickFixFactory.getTargetInstance(myProject, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
    val tag = findTag("project.properties")
    assertNotNull(tag)
    assertSize(2, tag.subTags)
    assertEquals("11", tag.subTags[0].value.text)
    assertEquals("11", tag.subTags[1].value.text)
  }

  @Test
  fun `test plugin target property empty quick fix`() {
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
                  "                     <source>11</source>" +
                  "                   </configuration>" +
                  "            </plugin>" +
                  "        </plugins>" +
                  "    </build>")
    val mavenProject = MavenProjectsManager.getInstance(myProject).findProject(MavenId("test:p1:1"))
    LanguageLevelQuickFixFactory.getInstance(myProject, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
    val tag = findTag("project.build.plugins.plugin.configuration")
    assertNotNull(tag)
    assertSize(2, tag.subTags)
    assertEquals("11", tag.subTags[0].value.text)
    assertEquals("11", tag.subTags[1].value.text)
  }

  @Test
  fun `test plugin target property bad value quick fix`() {
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
                  "                     <source>11</source>" +
                  "                     <target>5</target>" +
                  "                   </configuration>" +
                  "            </plugin>" +
                  "        </plugins>" +
                  "    </build>")
    val mavenProject = MavenProjectsManager.getInstance(myProject).findProject(MavenId("test:p1:1"))
    LanguageLevelQuickFixFactory.getInstance(myProject, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
    val tag = findTag("project.build.plugins.plugin.configuration")
    assertNotNull(tag)
    assertSize(2, tag.subTags)
    assertEquals("11", tag.subTags[0].value.text)
    assertEquals("11", tag.subTags[1].value.text)
  }

  @Test
  fun `test target property bad value in child quick fix`() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>p1</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +
                     "<modules>" +
                     "  <module>p2</module>" +
                     "</modules>")
    val p2 = createModulePom("p2",
                             "<parent>" +
                             "  <groupId>test</groupId>" +
                             "  <artifactId>p1</artifactId>" +
                             "  <version>1</version>" +
                             "</parent>" +
                             "<groupId>test</groupId>" +
                             "<artifactId>p2</artifactId>" +
                             "<version>1</version>" +
                             "<properties>" +
                             "        <maven.compiler.source>11</maven.compiler.source>" +
                             "        <maven.compiler.target>5</maven.compiler.target>" +
                             "</properties>")
    importProject()
    val mavenProject = MavenProjectsManager.getInstance(myProject).findProject(MavenId("test:p2:1"))
    LanguageLevelQuickFixFactory.getInstance(myProject, (mavenProject)!!)!!.perform(LanguageLevel.JDK_11)
    val tag = findTag(p2, "project.properties")
    assertNotNull(tag)
    assertSize(2, tag.subTags)
    assertEquals("11", tag.subTags[0].value.text)
    assertEquals("11", tag.subTags[1].value.text)
  }

  @Test
  fun `test target property bad value in parent quick fix`() {
    createProjectPom(("<groupId>test</groupId>" +
                      "<artifactId>p1</artifactId>" +
                      "<packaging>pom</packaging>" +
                      "<version>1</version>" +
                      "<modules>" +
                      "  <module>p2</module>" +
                      "</modules>" +
                      "<properties>" +
                      "        <maven.compiler.source>11</maven.compiler.source>" +
                      "        <maven.compiler.target>5</maven.compiler.target>" +
                      "</properties>"))
    createModulePom("p2",
                    ("<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>p1</artifactId>" +
                     "  <version>1</version>" +
                     "</parent>" +
                     "<groupId>test</groupId>" +
                     "<artifactId>p2</artifactId>" +
                     "<version>1</version>")
    )
    importProject()
    val mavenProject = MavenProjectsManager.getInstance(myProject).findProject(MavenId("test:p2:1"))
    LanguageLevelQuickFixFactory.getInstance(myProject, (mavenProject)!!)!!.perform(LanguageLevel.JDK_11)
    val tag = findTag("project.properties")
    assertNotNull(tag)
    assertSize(2, tag.subTags)
    assertEquals("11", tag.subTags[0].value.text)
    assertEquals("11", tag.subTags[1].value.text)
  }

  @Test
  fun `test plugin target property bad value in child quick fix`() {
    createProjectPom(("<groupId>test</groupId>" +
                      "<artifactId>p1</artifactId>" +
                      "<packaging>pom</packaging>" +
                      "<version>1</version>" +
                      "<modules>" +
                      "  <module>p2</module>" +
                      "</modules>"))
    val p2 = createModulePom("p2",
                             ("<parent>" +
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
                              "                     <source>11</source>" +
                              "                     <target>5</target>" +
                              "                   </configuration>" +
                              "            </plugin>" +
                              "        </plugins>" +
                              "    </build>"))
    importProject()
    val mavenProject = MavenProjectsManager.getInstance(myProject).findProject(MavenId("test:p2:1"))
    LanguageLevelQuickFixFactory.getInstance(myProject, (mavenProject)!!)!!.perform(LanguageLevel.JDK_11)
    val tag = findTag(p2, "project.build.plugins.plugin.configuration")
    assertNotNull(tag)
    assertSize(2, tag.subTags)
    assertEquals("11", tag.subTags[0].value.text)
    assertEquals("11", tag.subTags[1].value.text)
  }

  @Test
  fun `test plugin target property bad value in parent quick fix`() {
    createProjectPom(("<groupId>test</groupId>" +
                      "<artifactId>p1</artifactId>" +
                      "<packaging>pom</packaging>" +
                      "<version>1</version>" +
                      "<modules>" +
                      "  <module>p2</module>" +
                      "</modules>" +
                      "<build>" +
                      "        <plugins>" +
                      "            <plugin>" +
                      "                <groupId>org.apache.maven.plugins</groupId>" +
                      "                <artifactId>maven-compiler-plugin</artifactId>" +
                      "                   <configuration>" +
                      "                     <source>11</source>" +
                      "                     <target>5</target>" +
                      "                   </configuration>" +
                      "            </plugin>" +
                      "        </plugins>" +
                      "    </build>"))
    createModulePom("p2",
                    ("<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>p1</artifactId>" +
                     "  <version>1</version>" +
                     "</parent>" +
                     "<groupId>test</groupId>" +
                     "<artifactId>p2</artifactId>" +
                     "<version>1</version>")
    )
    importProject()
    val mavenProject = MavenProjectsManager.getInstance(myProject).findProject(MavenId("test:p2:1"))
    LanguageLevelQuickFixFactory.getInstance(myProject, (mavenProject)!!)!!.perform(LanguageLevel.JDK_11)
    val tag = findTag("project.build.plugins.plugin.configuration")
    assertNotNull(tag)
    assertSize(2, tag.subTags)
    assertEquals("11", tag.subTags[0].value.text)
    assertEquals("11", tag.subTags[1].value.text)
  }
}