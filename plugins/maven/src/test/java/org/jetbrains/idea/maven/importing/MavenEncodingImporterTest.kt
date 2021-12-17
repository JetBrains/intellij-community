// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import junit.framework.TestCase
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.junit.Test
import java.nio.charset.StandardCharsets

class MavenEncodingImporterTest : MavenMultiVersionImportingTestCase() {

  @Test fun testShouldSetEncodingForNewProject() {
    val subFile = createProjectSubFile("src/main/java/MyClass.java")
    importProject("""<groupId>test</groupId>
                     <artifactId>project</artifactId>
                     <version>1</version>
                     <properties>
                        <project.build.sourceEncoding>ISO-8859-1</project.build.sourceEncoding>
                     </properties>"""
    )

    TestCase.assertEquals(StandardCharsets.ISO_8859_1, EncodingProjectManager.getInstance(myProject).getEncoding(subFile, true))
  }

  @Test fun testShouldSetDifferentEncodingForSourceAndResource() {
    val srcFile = createProjectSubFile("src/main/java/MyClass.java")
    val resFile = createProjectSubFile("src/main/resources/data.properties")
    importProject("""<groupId>test</groupId>
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

    TestCase.assertEquals(StandardCharsets.ISO_8859_1, EncodingProjectManager.getInstance(myProject).getEncoding(srcFile, true))
    TestCase.assertEquals(StandardCharsets.UTF_16LE, EncodingProjectManager.getInstance(myProject).getEncoding(resFile, true))
  }

  @Test fun testShouldUseSrcEncodingForResFiles() {
    val resFile = createProjectSubFile("src/main/resources/data.properties")
    importProject("""<groupId>test</groupId>
                     <artifactId>project</artifactId>
                     <version>1</version>
                     <properties>
                        <project.build.sourceEncoding>ISO-8859-1</project.build.sourceEncoding>
                     </properties>
                     """
    )

    TestCase.assertEquals(StandardCharsets.ISO_8859_1, EncodingProjectManager.getInstance(myProject).getEncoding(resFile, true))
  }

  @Test fun testShouldChangeEncoding() {
    val subFile = createProjectSubFile("src/main/java/MyClass.java")
    importProject("""<groupId>test</groupId>
                     <artifactId>project</artifactId>
                     <version>1</version>
                     <properties>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                     </properties>"""
    )

    TestCase.assertEquals(StandardCharsets.UTF_8, EncodingProjectManager.getInstance(myProject).getEncoding(subFile, true))

    importProject("""<groupId>test</groupId>
                     <artifactId>project</artifactId>
                     <version>1</version>
                     <properties>
                        <project.build.sourceEncoding>ISO-8859-1</project.build.sourceEncoding>
                     </properties>"""
    )

    TestCase.assertEquals(StandardCharsets.ISO_8859_1, EncodingProjectManager.getInstance(myProject).getEncoding(subFile, true))
  }

  @Test fun testShouldSetEncodingPerProject() {

    createModulePom("module1", """<parent>
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                          </parent>
                          <artifactId>module1</artifactId>
""")
    createModulePom("module2", """<parent>
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                          </parent>
                          <artifactId>module2</artifactId>
                          <properties>
                            <project.build.sourceEncoding>ISO-8859-1</project.build.sourceEncoding>
                          </properties>
""")

    val subFile1 = createProjectSubFile("module1/src/main/java/MyClass.java")
    val subFile2 = createProjectSubFile("module2/src/main/java/AnotherClass.java")
    importProject("""<groupId>test</groupId>
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



    TestCase.assertEquals(StandardCharsets.UTF_16, EncodingProjectManager.getInstance(myProject).getEncoding(subFile1, true))
    TestCase.assertEquals(StandardCharsets.ISO_8859_1, EncodingProjectManager.getInstance(myProject).getEncoding(subFile2, true))
  }

  @Test fun testShouldSetEncodingToNewFiles() {

    createModulePom("module1", """<parent>
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                          </parent>
                          <artifactId>module1</artifactId>
""")
    createModulePom("module2", """<parent>
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                          </parent>
                          <artifactId>module2</artifactId>
                          <properties>
                            <project.build.sourceEncoding>ISO-8859-1</project.build.sourceEncoding>
                          </properties>
""")


    importProject("""<groupId>test</groupId>
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

    val subFile1 = createProjectSubFile("module1/src/main/java/MyClass.java")
    val subFile2 = createProjectSubFile("module2/src/main/java/AnotherClass.java")

    TestCase.assertEquals(StandardCharsets.UTF_16, EncodingProjectManager.getInstance(myProject).getEncoding(subFile1, true))
    TestCase.assertEquals(StandardCharsets.ISO_8859_1, EncodingProjectManager.getInstance(myProject).getEncoding(subFile2, true))
  }

  @Test fun testShouldSetResourceEncodingAsProperties() {
    importProject("""<groupId>test</groupId>
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
    val mavenProject = MavenProjectsManager.getInstance(myProject).rootProjects.first()

    TestCase.assertEquals("ISO-8859-1", mavenProject.getResourceEncoding(myProject))
  }


}
