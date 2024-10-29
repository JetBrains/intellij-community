// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.maven.testFramework.MavenDomTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.inspections.MavenParentMissedVersionInspection
import org.junit.Test

class MavenAutomaticVersioningResolutionTest : MavenDomTestCase() {
  @Test
  fun testAutomaticParentVersionResolutionForMaven4() = runBlocking {
    assumeVersionAtLeast("4.0.0-alpha-2")
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1.1</version>
                       <packaging>pom</packaging>
                       <modules>
                        <module>m</module>
                       </modules>
                       """.trimIndent())

    val m = createModulePom("m",
                            """
                                      <parent>
                                        <groupId>test</groupId>
                                        <artifactId>project</artifactId>
                                      </parent>
                                       <artifactId>m</artifactId>
                                      """.trimIndent())
    importProjectAsync()
    assertEquals("1.1", projectsManager.findProject(m)!!.mavenId.version)

    createModulePom("m",
                    """
                      <parent>
                        <groupId>test</groupId>
                        <artifactId><caret>project</artifactId>
                      </parent>
                       <artifactId>m</artifactId>
                      """.trimIndent())
    assertResolved(m, findPsiFile(projectPom))
    fixture.enableInspections(listOf<Class<out LocalInspectionTool?>>(MavenParentMissedVersionInspection::class.java))
    checkHighlighting(m)
  }

  @Test
  fun testAutomaticParentVersionResolutionIsNotEnabledForMaven3() = runBlocking {
    assumeVersionLessThan("4.0.0-alpha-2")
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1.1</version>
                       <packaging>pom</packaging>
                       <modules>
                        <module>m</module>
                       </modules>
                       """.trimIndent())

    val m = createModulePom("m",
                            """
                                      <parent>
                                        <groupId>test</groupId>
                                        <artifactId>project</artifactId>
                                      </parent>
                                       <artifactId>m</artifactId>
                                      """.trimIndent())
    importProjectAsync()

    fixture.enableInspections(listOf<Class<out LocalInspectionTool?>>(MavenParentMissedVersionInspection::class.java))

    checkHighlighting(m, Highlight(text = "parent", description = "'version' child tag should be defined"))
  }

  @Test
  fun testAutomaticDependencyVersionResolutionForMaven4() = runBlocking {
    assumeVersionAtLeast("4.0.0-alpha-2")
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1.1</version>
                       <packaging>pom</packaging>
                       <modules>
                        <module>m1</module>
                        <module>m2</module>
                       </modules>
                       """.trimIndent())

    val m1 = createModulePom("m1",
                             """
                                       <parent>
                                         <groupId>test</groupId>
                                         <artifactId>project</artifactId>
                                       </parent>
                                        <artifactId>m1</artifactId>
                                       """.trimIndent())
    val m2 = createModulePom("m2",
                             """
                                       <parent>
                                         <groupId>test</groupId>
                                         <artifactId>project</artifactId>
                                       </parent>
                                        <artifactId>m2</artifactId>
                                        <dependencies>
                                          <dependency>
                                            <groupId>test</groupId>
                                            <artifactId>m1</artifactId>
                                          </dependency>
                                        </dependencies>
                                       """.trimIndent())
    importProjectAsync()
    assertEquals("1.1", projectsManager.findProject(m1)!!.mavenId.version)
    assertEquals("1.1", projectsManager.findProject(m2)!!.mavenId.version)
    assertModuleModuleDeps("m2", "m1")

    createModulePom("m2",
                    """
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                      </parent>
                       <artifactId>m2</artifactId>
                       <dependencies>
                         <dependency>
                           <groupId><caret>test</groupId>
                           <artifactId>m1</artifactId>
                         </dependency>
                       </dependencies>
                      """.trimIndent())
    assertResolved(m2, findPsiFile(m1))
    checkHighlighting(m2)
  }

  @Test
  fun testAutomaticDependencyVersionResolutionForMaven4AndRelativePath() = runBlocking {
    assumeVersionAtLeast("4.0.0-alpha-2")
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1.1</version>
                       <packaging>pom</packaging>
                       <modules>
                        <module>m/m1</module>
                       </modules>
                       """.trimIndent())

    val m1 = createModulePom("m/m1",
                             """
                                       <parent>
                                         <groupId>test</groupId>
                                         <artifactId>project</artifactId>
                                         <relativePath>../../pom.xml</relativePath>
                                       </parent>
                                        <artifactId>m1</artifactId>
                                       """.trimIndent())
    importProjectAsync()
    assertEquals("1.1", projectsManager.findProject(m1)!!.mavenId.version)

    createModulePom("m/m1",
                    """
                                       <parent>
                                         <groupId>test</groupId>
                                         <artifactId><caret>project</artifactId>
                                         <relativePath>../../pom.xml</relativePath>
                                       </parent>
                                        <artifactId>m1</artifactId>
                                       """.trimIndent())
    assertResolved(m1, findPsiFile(projectPom))
  }
}
