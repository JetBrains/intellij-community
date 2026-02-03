// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes

import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.pom.java.LanguageLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.junit.Test

class LanguageLevelQuickFixTest : MavenDomTestCase() {

  @Test
  fun `test property empty quick fix`() = runBlocking {
    importProjectAsync("""<groupId>test</groupId>
                    <artifactId>p1</artifactId>
                    <packaging>pom</packaging>
                    <version>1</version>"""
    )
    val mavenProject = MavenProjectsManager.getInstance(project).findProject(MavenId("test:p1:1"))
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(project, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = findTag("project.properties")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test property old value quick fix`() = runBlocking {
    importProjectAsync("""<groupId>test</groupId>
                    <artifactId>p1</artifactId>
                    <packaging>pom</packaging>
                    <version>1</version>
                    <properties>
                            <maven.compiler.source>5</maven.compiler.source>
                            <maven.compiler.target>5</maven.compiler.target>
                    </properties>""")
    val mavenProject = MavenProjectsManager.getInstance(project).findProject(MavenId("test:p1:1"))
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(project, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = findTag("project.properties")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test plugin configuration empty value quick fix`() = runBlocking {
    importProjectAsync("""<groupId>test</groupId>
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
    val mavenProject = MavenProjectsManager.getInstance(project).findProject(MavenId("test:p1:1"))
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(project, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = findTag("project.properties")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test plugin configuration old value quick fix`() = runBlocking {
    importProjectAsync("""<groupId>test</groupId>
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
    val mavenProject = MavenProjectsManager.getInstance(project).findProject(MavenId("test:p1:1"))
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(project, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = findTag("project.build.plugins.plugin.configuration")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test multi module project property empty value quick fix`() = runBlocking {
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
    importProjectAsync()
    val mavenProject = MavenProjectsManager.getInstance(project).findProject(MavenId("test:p2:1"))
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(project, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = findTag("project.properties")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test multi module project property old value quick fix`() = runBlocking {
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
    importProjectAsync()
    val mavenProject = MavenProjectsManager.getInstance(project).findProject(MavenId("test:p2:1"))
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(project, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = findTag(p2, "project.properties")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test multi module project plugin old value quick fix`() = runBlocking {
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
    importProjectAsync()
    val mavenProject = MavenProjectsManager.getInstance(project).findProject(MavenId("test:p2:1"))
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(project, (mavenProject)!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = findTag(p2, "project.build.plugins.plugin.configuration")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test target property empty quick fix`() = runBlocking {
    importProjectAsync("<groupId>test</groupId>" +
                  "<artifactId>p1</artifactId>" +
                  "<packaging>pom</packaging>" +
                  "<version>1</version>" +
                  "<properties>" +
                  "        <maven.compiler.source>11</maven.compiler.source>" +
                  "</properties>")
    val mavenProject = MavenProjectsManager.getInstance(project).findProject(MavenId("test:p1:1"))
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getTargetInstance(project, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = findTag("project.properties")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test target property bad value quick fix`() = runBlocking {
    importProjectAsync("<groupId>test</groupId>" +
                  "<artifactId>p1</artifactId>" +
                  "<packaging>pom</packaging>" +
                  "<version>1</version>" +
                  "<properties>" +
                  "        <maven.compiler.source>11</maven.compiler.source>" +
                  "        <maven.compiler.target>5</maven.compiler.target>" +
                  "</properties>")
    val mavenProject = MavenProjectsManager.getInstance(project).findProject(MavenId("test:p1:1"))
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getTargetInstance(project, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = findTag("project.properties")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test plugin target property empty quick fix`() = runBlocking {
    importProjectAsync("<groupId>test</groupId>" +
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
    val mavenProject = MavenProjectsManager.getInstance(project).findProject(MavenId("test:p1:1"))
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(project, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = findTag("project.build.plugins.plugin.configuration")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test plugin target property bad value quick fix`() = runBlocking {
    importProjectAsync("<groupId>test</groupId>" +
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
    val mavenProject = MavenProjectsManager.getInstance(project).findProject(MavenId("test:p1:1"))
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(project, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = findTag("project.build.plugins.plugin.configuration")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test target property bad value in child quick fix`() = runBlocking {
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
    importProjectAsync()
    val mavenProject = MavenProjectsManager.getInstance(project).findProject(MavenId("test:p2:1"))
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(project, (mavenProject)!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = findTag(p2, "project.properties")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test target property bad value in parent quick fix`() = runBlocking {
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
    importProjectAsync()
    val mavenProject = MavenProjectsManager.getInstance(project).findProject(MavenId("test:p2:1"))
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(project, (mavenProject)!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = findTag("project.properties")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test plugin target property bad value in child quick fix`() = runBlocking {
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
    importProjectAsync()
    val mavenProject = MavenProjectsManager.getInstance(project).findProject(MavenId("test:p2:1"))
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(project, (mavenProject)!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = findTag(p2, "project.build.plugins.plugin.configuration")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test plugin target property bad value in parent quick fix`() = runBlocking {
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
    importProjectAsync()
    val mavenProject = MavenProjectsManager.getInstance(project).findProject(MavenId("test:p2:1"))
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(project, (mavenProject)!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = findTag("project.build.plugins.plugin.configuration")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }
}