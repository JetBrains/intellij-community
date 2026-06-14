// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.UsefulTestCase.assertSize
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.fixtures.findTag
import org.jetbrains.idea.maven.fixtures.waitForImportWithinTimeout
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class LanguageLevelQuickFixTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  

  @Test
  fun `test property empty quick fix`() = runBlocking {
    maven.importProjectAsync("""<groupId>test</groupId>
                    <artifactId>p1</artifactId>
                    <packaging>pom</packaging>
                    <version>1</version>"""
    )
    val mavenProject = MavenProjectsManager.getInstance(maven.project).findProject(MavenId("test:p1:1"))
    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(maven.project, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = maven.findTag("project.properties")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test property old value quick fix`() = runBlocking {
    maven.importProjectAsync("""<groupId>test</groupId>
                    <artifactId>p1</artifactId>
                    <packaging>pom</packaging>
                    <version>1</version>
                    <properties>
                            <maven.compiler.source>5</maven.compiler.source>
                            <maven.compiler.target>5</maven.compiler.target>
                    </properties>""")
    val mavenProject = MavenProjectsManager.getInstance(maven.project).findProject(MavenId("test:p1:1"))
    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(maven.project, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = maven.findTag("project.properties")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test plugin configuration empty value quick fix`() = runBlocking {
    maven.importProjectAsync("""<groupId>test</groupId>
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
    val mavenProject = MavenProjectsManager.getInstance(maven.project).findProject(MavenId("test:p1:1"))
    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(maven.project, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = maven.findTag("project.properties")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test plugin configuration old value quick fix`() = runBlocking {
    maven.importProjectAsync("""<groupId>test</groupId>
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
    val mavenProject = MavenProjectsManager.getInstance(maven.project).findProject(MavenId("test:p1:1"))
    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(maven.project, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = maven.findTag("project.build.plugins.plugin.configuration")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test multi module project property empty value quick fix`() = runBlocking {
    maven.createProjectPom("""<groupId>test</groupId>
                       <artifactId>p1</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>p2</module>
                       </modules>""")
    maven.createModulePom("p2",
                    """<parent>
                        <groupId>test</groupId>
                        <artifactId>p1</artifactId>
                        <version>1</version>
                      </parent>
                      <groupId>test</groupId>
                      <artifactId>p2</artifactId>
                      <version>1</version>""")
    maven.importProjectAsync()
    val mavenProject = MavenProjectsManager.getInstance(maven.project).findProject(MavenId("test:p2:1"))
    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(maven.project, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = maven.findTag("project.properties")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test multi module project property old value quick fix`() = runBlocking {
    maven.createProjectPom(("""<groupId>test</groupId>
                        <artifactId>p1</artifactId>
                        <packaging>pom</packaging>
                        <version>1</version>
                        <modules>
                          <module>p2</module>
                        </modules>"""))
    val p2 = maven.createModulePom("p2",
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
    maven.importProjectAsync()
    val mavenProject = MavenProjectsManager.getInstance(maven.project).findProject(MavenId("test:p2:1"))
    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(maven.project, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = maven.findTag(p2, "project.properties")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test multi module project plugin old value quick fix`() = runBlocking {
    maven.createProjectPom(("""<groupId>test</groupId>
                        <artifactId>p1</artifactId>
                        <packaging>pom</packaging>
                        <version>1</version>
                        <modules>
                          <module>p2</module>
                        </modules>"""))
    val p2 = maven.createModulePom("p2",
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
    maven.importProjectAsync()
    val mavenProject = MavenProjectsManager.getInstance(maven.project).findProject(MavenId("test:p2:1"))
    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(maven.project, (mavenProject)!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = maven.findTag(p2, "project.build.plugins.plugin.configuration")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test target property empty quick fix`() = runBlocking {
    maven.importProjectAsync("<groupId>test</groupId>" +
                  "<artifactId>p1</artifactId>" +
                  "<packaging>pom</packaging>" +
                  "<version>1</version>" +
                  "<properties>" +
                  "        <maven.compiler.source>11</maven.compiler.source>" +
                  "</properties>")
    val mavenProject = MavenProjectsManager.getInstance(maven.project).findProject(MavenId("test:p1:1"))
    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getTargetInstance(maven.project, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = maven.findTag("project.properties")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test target property bad value quick fix`() = runBlocking {
    maven.importProjectAsync("<groupId>test</groupId>" +
                  "<artifactId>p1</artifactId>" +
                  "<packaging>pom</packaging>" +
                  "<version>1</version>" +
                  "<properties>" +
                  "        <maven.compiler.source>11</maven.compiler.source>" +
                  "        <maven.compiler.target>5</maven.compiler.target>" +
                  "</properties>")
    val mavenProject = MavenProjectsManager.getInstance(maven.project).findProject(MavenId("test:p1:1"))
    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getTargetInstance(maven.project, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = maven.findTag("project.properties")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test plugin target property empty quick fix`() = runBlocking {
    maven.importProjectAsync("<groupId>test</groupId>" +
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
    val mavenProject = MavenProjectsManager.getInstance(maven.project).findProject(MavenId("test:p1:1"))
    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(maven.project, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = maven.findTag("project.build.plugins.plugin.configuration")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test plugin target property bad value quick fix`() = runBlocking {
    maven.importProjectAsync("<groupId>test</groupId>" +
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
    val mavenProject = MavenProjectsManager.getInstance(maven.project).findProject(MavenId("test:p1:1"))
    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(maven.project, mavenProject!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = maven.findTag("project.build.plugins.plugin.configuration")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test target property bad value in child quick fix`() = runBlocking {
    maven.createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>p1</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +
                     "<modules>" +
                     "  <module>p2</module>" +
                     "</modules>")
    val p2 = maven.createModulePom("p2",
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
    maven.importProjectAsync()
    val mavenProject = MavenProjectsManager.getInstance(maven.project).findProject(MavenId("test:p2:1"))
    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(maven.project, (mavenProject)!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = maven.findTag(p2, "project.properties")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test target property bad value in parent quick fix`() = runBlocking {
    maven.createProjectPom(("<groupId>test</groupId>" +
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
    maven.createModulePom("p2",
                    ("<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>p1</artifactId>" +
                     "  <version>1</version>" +
                     "</parent>" +
                     "<groupId>test</groupId>" +
                     "<artifactId>p2</artifactId>" +
                     "<version>1</version>")
    )
    maven.importProjectAsync()
    val mavenProject = MavenProjectsManager.getInstance(maven.project).findProject(MavenId("test:p2:1"))
    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(maven.project, (mavenProject)!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = maven.findTag("project.properties")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test plugin target property bad value in child quick fix`() = runBlocking {
    maven.createProjectPom(("<groupId>test</groupId>" +
                      "<artifactId>p1</artifactId>" +
                      "<packaging>pom</packaging>" +
                      "<version>1</version>" +
                      "<modules>" +
                      "  <module>p2</module>" +
                      "</modules>"))
    val p2 = maven.createModulePom("p2",
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
    maven.importProjectAsync()
    val mavenProject = MavenProjectsManager.getInstance(maven.project).findProject(MavenId("test:p2:1"))
    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(maven.project, (mavenProject)!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = maven.findTag(p2, "project.build.plugins.plugin.configuration")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }

  @Test
  fun `test plugin target property bad value in parent quick fix`() = runBlocking {
    maven.createProjectPom(("<groupId>test</groupId>" +
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
    maven.createModulePom("p2",
                    ("<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>p1</artifactId>" +
                     "  <version>1</version>" +
                     "</parent>" +
                     "<groupId>test</groupId>" +
                     "<artifactId>p2</artifactId>" +
                     "<version>1</version>")
    )
    maven.importProjectAsync()
    val mavenProject = MavenProjectsManager.getInstance(maven.project).findProject(MavenId("test:p2:1"))
    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          LanguageLevelQuickFixFactory.getInstance(maven.project, (mavenProject)!!)!!.perform(LanguageLevel.JDK_11)
        }
      }
    }
    val tag = maven.findTag("project.build.plugins.plugin.configuration")
    assertNotNull(tag)
    readAction {
      assertSize(2, tag.subTags)
      assertEquals("11", tag.subTags[0].value.text)
      assertEquals("11", tag.subTags[1].value.text)
    }
  }
}