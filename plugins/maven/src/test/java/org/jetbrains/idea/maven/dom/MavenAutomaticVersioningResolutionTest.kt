// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture.Highlight
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModuleModuleDeps
import com.intellij.maven.testFramework.fixtures.assumeMaven3
import com.intellij.maven.testFramework.fixtures.assumeMaven4
import com.intellij.maven.testFramework.fixtures.assumeVersionAtLeast
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.findPsiFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.moveCaretTo
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.inspections.MavenParentMissedVersionInspection
import org.jetbrains.idea.maven.fixtures.assertResolved
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import org.jetbrains.idea.maven.fixtures.getIntentionAtCaret
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenAutomaticVersioningResolutionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun testAutomaticParentVersionResolutionForMaven4() = runBlocking {
    maven.assumeMaven4()
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1.1</version>
                       <packaging>pom</packaging>
                       <modules>
                        <module>m</module>
                       </modules>
                       """.trimIndent())

    val m = maven.createModulePom("m",
                            """
                                      <parent>
                                        <groupId>test</groupId>
                                        <artifactId>project</artifactId>
                                      </parent>
                                       <artifactId>m</artifactId>
                                      """.trimIndent())
    maven.importProjectAsync()
    assertEquals("1.1", maven.projectsManager.findProject(m)!!.mavenId.version)

    maven.createModulePom("m",
                    """
                      <parent>
                        <groupId>test</groupId>
                        <artifactId><caret>project</artifactId>
                      </parent>
                       <artifactId>m</artifactId>
                      """.trimIndent())
    maven.assertResolved(m, maven.findPsiFile(maven.projectPom))
    maven.fixture.enableInspections(listOf<Class<out LocalInspectionTool?>>(MavenParentMissedVersionInspection::class.java))
    maven.checkHighlighting(m)
  }

  @Test
  fun testAutomaticParentVersionResolutionIsNotEnabledForMaven3() = runBlocking {
    maven.assumeMaven3()
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1.1</version>
                       <packaging>pom</packaging>
                       <modules>
                        <module>m</module>
                       </modules>
                       """.trimIndent())

    val m = maven.createModulePom("m",
                            """
                                      <parent>
                                        <groupId>test</groupId>
                                        <artifactId>project</artifactId>
                                      </parent>
                                       <artifactId>m</artifactId>
                                      """.trimIndent())
    maven.importProjectAsync()

    maven.fixture.enableInspections(listOf<Class<out LocalInspectionTool?>>(MavenParentMissedVersionInspection::class.java))

    maven.moveCaretTo(m, "<parent<caret>>")
    maven.checkHighlighting(m, Highlight(text = "parent", description = "'version' child tag should be defined"))
    val action = maven.getIntentionAtCaret(m, "Insert required child tag version")
    assertNotNull(action, "Quick Fix for adding <version> child tag must be available")
    maven.fixture.launchAction(action!!)
    maven.checkHighlighting(m)
  }

  @Test
  fun testAutomaticDependencyVersionResolutionForMaven4() = runBlocking {
    maven.assumeVersionAtLeast("4.0.0-alpha-2")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1.1</version>
                       <packaging>pom</packaging>
                       <modules>
                        <module>m1</module>
                        <module>m2</module>
                       </modules>
                       """.trimIndent())

    val m1 = maven.createModulePom("m1",
                             """
                                       <parent>
                                         <groupId>test</groupId>
                                         <artifactId>project</artifactId>
                                       </parent>
                                        <artifactId>m1</artifactId>
                                       """.trimIndent())
    val m2 = maven.createModulePom("m2",
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
                                            <version>1.1</version>
                                          </dependency>
                                        </dependencies>
                                       """.trimIndent())
    maven.importProjectAsync()
    assertEquals("1.1", maven.projectsManager.findProject(m1)!!.mavenId.version)
    assertEquals("1.1", maven.projectsManager.findProject(m2)!!.mavenId.version)
    maven.assertModuleModuleDeps("m2", "m1")

    maven.createModulePom("m2",
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
    maven.assertResolved(m2, maven.findPsiFile(m1))
    maven.checkHighlighting(m2)
  }

  @Test
  fun testAutomaticDependencyVersionResolutionForMaven4AndRelativePath() = runBlocking {
    maven.assumeVersionAtLeast("4.0.0-alpha-2")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1.1</version>
                       <packaging>pom</packaging>
                       <modules>
                        <module>m/m1</module>
                       </modules>
                       """.trimIndent())

    val m1 = maven.createModulePom("m/m1",
                             """
                                       <parent>
                                         <groupId>test</groupId>
                                         <artifactId>project</artifactId>
                                         <relativePath>../../pom.xml</relativePath>
                                       </parent>
                                        <artifactId>m1</artifactId>
                                       """.trimIndent())
    maven.importProjectAsync()
    assertEquals("1.1", maven.projectsManager.findProject(m1)!!.mavenId.version)

    maven.createModulePom("m/m1",
                    """
                                       <parent>
                                         <groupId>test</groupId>
                                         <artifactId><caret>project</artifactId>
                                         <relativePath>../../pom.xml</relativePath>
                                       </parent>
                                        <artifactId>m1</artifactId>
                                       """.trimIndent())
    maven.assertResolved(m1, maven.findPsiFile(maven.projectPom))
  }
}
