// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.nio.charset.StandardCharsets

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenEncodingImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun testShouldSetEncodingForNewProject() = runBlocking {
    val subFile = maven.createProjectSubFile("src/main/java/MyClass.java")
    maven.importProjectAsync("""<groupId>test</groupId>
                     <artifactId>project</artifactId>
                     <version>1</version>
                     <properties>
                        <project.build.sourceEncoding>ISO-8859-1</project.build.sourceEncoding>
                     </properties>"""
    )

    assertEquals(StandardCharsets.ISO_8859_1, EncodingProjectManager.getInstance(maven.project).getEncoding(subFile, true))
  }

  @Test
  fun testShouldSetDifferentEncodingForSourceAndResource() = runBlocking {
    val srcFile = maven.createProjectSubFile("src/main/java/MyClass.java")
    val resFile = maven.createProjectSubFile("src/main/resources/data.properties")
    maven.importProjectAsync("""<groupId>test</groupId>
                     <artifactId>project</artifactId>
                     <version>1</version>
                     <properties>
                        <project.build.sourceEncoding>ISO-8859-1</project.build.sourceEncoding>
                     </properties>
                     <build>
                       <plugins>
                         <plugin>
                            <artifactId>maven-resources-plugin</artifactId>
                            <version>2.6</version>
                            <configuration>
                              <encoding>UTF-16LE</encoding>
                            </configuration>
                          </plugin>
                       </plugins>
                     </build>
                     """
    )

    assertEquals(StandardCharsets.ISO_8859_1, EncodingProjectManager.getInstance(maven.project).getEncoding(srcFile, true))
    assertEquals(StandardCharsets.UTF_16LE, EncodingProjectManager.getInstance(maven.project).getEncoding(resFile, true))
  }

  @Test
  fun testShouldUseSrcEncodingForResFiles() = runBlocking {
    val resFile = maven.createProjectSubFile("src/main/resources/data.properties")
    maven.importProjectAsync("""<groupId>test</groupId>
                     <artifactId>project</artifactId>
                     <version>1</version>
                     <properties>
                        <project.build.sourceEncoding>ISO-8859-1</project.build.sourceEncoding>
                     </properties>
                     """
    )

    assertEquals(StandardCharsets.ISO_8859_1, EncodingProjectManager.getInstance(maven.project).getEncoding(resFile, true))
  }

  @Test
  fun testShouldChangeEncoding() = runBlocking {
    val subFile = maven.createProjectSubFile("src/main/java/MyClass.java")
    maven.importProjectAsync("""<groupId>test</groupId>
                     <artifactId>project</artifactId>
                     <version>1</version>
                     <properties>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                     </properties>"""
    )

    assertEquals(StandardCharsets.UTF_8, EncodingProjectManager.getInstance(maven.project).getEncoding(subFile, true))

    maven.updateProjectPom("""<groupId>test</groupId>
                     <artifactId>project</artifactId>
                     <version>1</version>
                     <properties>
                        <project.build.sourceEncoding>ISO-8859-1</project.build.sourceEncoding>
                     </properties>"""
    )
    maven.updateAllProjects()

    assertEquals(StandardCharsets.ISO_8859_1, EncodingProjectManager.getInstance(maven.project).getEncoding(subFile, true))
  }

  @Test
  fun testShouldSetEncodingPerProject() = runBlocking {

    maven.createModulePom("module1", """<parent>
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                          </parent>
                          <artifactId>module1</artifactId>
""")
    maven.createModulePom("module2", """<parent>
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                          </parent>
                          <artifactId>module2</artifactId>
                          <properties>
                            <project.build.sourceEncoding>ISO-8859-1</project.build.sourceEncoding>
                          </properties>
""")

    val subFile1 = maven.createProjectSubFile("module1/src/main/java/MyClass.java")
    val subFile2 = maven.createProjectSubFile("module2/src/main/java/AnotherClass.java")
    maven.importProjectAsync("""<groupId>test</groupId>
                     <artifactId>project</artifactId>
                     <version>1</version>
                     <packaging>pom</packaging>
                     <modules>
                       <module>module1</module>
                       <module>module2</module>
                     </modules>
                     <properties>
                        <project.build.sourceEncoding>UTF-16</project.build.sourceEncoding>
                     </properties>"""
    )



    assertEquals(StandardCharsets.UTF_16, EncodingProjectManager.getInstance(maven.project).getEncoding(subFile1, true))
    assertEquals(StandardCharsets.ISO_8859_1, EncodingProjectManager.getInstance(maven.project).getEncoding(subFile2, true))
  }

  @Test
  fun testShouldSetEncodingPerProjectInSubsequentImport() = runBlocking {
    maven.createModulePom("module1", """
                          <parent>
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                          </parent>
                          <artifactId>module1</artifactId>""")

    maven.createModulePom("module2", """
                          <parent>
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                          </parent>
                          <artifactId>module2</artifactId>
                          <properties>
                            <project.build.sourceEncoding>ISO-8859-1</project.build.sourceEncoding>
                          </properties>""")

    val subFile1 = maven.createProjectSubFile("module1/src/main/java/MyClass.java")
    val subFile2 = maven.createProjectSubFile("module2/src/main/java/AnotherClass.java")
    maven.importProjectAsync("""
                     <groupId>test</groupId>
                     <artifactId>project</artifactId>
                     <version>1</version>
                     <packaging>pom</packaging>
                     <modules>
                       <module>module1</module>
                       <module>module2</module>
                     </modules>
                     <properties>
                        <project.build.sourceEncoding>UTF-16</project.build.sourceEncoding>
                     </properties>""")

    maven.updateProjectPom("""
                     <groupId>test</groupId>
                     <artifactId>project</artifactId>
                     <version>1</version>
                     <packaging>pom</packaging>
                     <modules>
                       <module>module1</module>
                       <module>module2</module>
                     </modules>
                     <properties>
                        <project.build.sourceEncoding>UTF-16</project.build.sourceEncoding>
                     </properties>""")
    maven.updateAllProjects()

    assertEquals(StandardCharsets.UTF_16, EncodingProjectManager.getInstance(maven.project).getEncoding(subFile1, true))
    assertEquals(StandardCharsets.ISO_8859_1, EncodingProjectManager.getInstance(maven.project).getEncoding(subFile2, true))
  }

  @Test
  fun testShouldSetEncodingToNewFiles() = runBlocking {

    maven.createModulePom("module1", """<parent>
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                          </parent>
                          <artifactId>module1</artifactId>
""")
    maven.createModulePom("module2", """<parent>
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                          </parent>
                          <artifactId>module2</artifactId>
                          <properties>
                            <project.build.sourceEncoding>ISO-8859-1</project.build.sourceEncoding>
                          </properties>
""")


    maven.importProjectAsync("""<groupId>test</groupId>
                     <artifactId>project</artifactId>
                     <version>1</version>
                     <packaging>pom</packaging>
                     <modules>
                       <module>module1</module>
                       <module>module2</module>
                     </modules>
                     <properties>
                        <project.build.sourceEncoding>UTF-16</project.build.sourceEncoding>
                     </properties>"""
    )

    val subFile1 = maven.createProjectSubFile("module1/src/main/java/MyClass.java")
    val subFile2 = maven.createProjectSubFile("module2/src/main/java/AnotherClass.java")

    assertEquals(StandardCharsets.UTF_16, EncodingProjectManager.getInstance(maven.project).getEncoding(subFile1, true))
    assertEquals(StandardCharsets.ISO_8859_1, EncodingProjectManager.getInstance(maven.project).getEncoding(subFile2, true))
  }

  @Test
  fun testShouldSetResourceEncodingAsProperties() = runBlocking {
    maven.importProjectAsync("""<groupId>test</groupId>
                     <artifactId>project</artifactId>
                     <version>1</version>
                     <properties>
                        <project.build.sourceEncoding>ISO-8859-1</project.build.sourceEncoding>
                     </properties>
                     <build>
                       <plugins>
                         <plugin>
                            <artifactId>maven-resources-plugin</artifactId>
                            <version>2.6</version>
                            <configuration>
                              <encoding>${'$'}{project.encoding}</encoding>
                            </configuration>
                          </plugin>
                       </plugins>
                     </build>
                     """
    )
    val mavenProject = MavenProjectsManager.getInstance(maven.project).rootProjects.first()

    val encoding = readAction { mavenProject.getResourceEncoding(maven.project) }
    assertEquals("ISO-8859-1", encoding)
  }
}
