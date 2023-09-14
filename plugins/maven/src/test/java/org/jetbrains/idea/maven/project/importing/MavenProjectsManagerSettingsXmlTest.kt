// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.idea.maven.utils.MavenUtil
import org.junit.Assume
import org.junit.Test

class MavenProjectsManagerSettingsXmlTest : MavenMultiVersionImportingTestCase() {
  override fun setUp() {
    super.setUp()
    initProjectsManager(true)
    Assume.assumeFalse(MavenUtil.isLinearImportEnabled())
  }

  @Test
  fun testUpdatingProjectsOnSettingsXmlChange() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       <build>
                         <sourceDirectory>${'$'}{prop}</sourceDirectory>
                       </build>
                       """.trimIndent())
    createModulePom("m",
                    """
                      <groupId>test</groupId>
                      <artifactId>m</artifactId>
                      <version>1</version>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                      </parent>
                      <build>
                        <sourceDirectory>${'$'}{prop}</sourceDirectory>
                      </build>
                      """.trimIndent())
    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>one</id>
                            <activation>
                              <activeByDefault>true</activeByDefault>
                            </activation>
                            <properties>
                              <prop>value1</prop>
                            </properties>
                          </profile>
                        </profiles>
                        """.trimIndent())
    importProject()
    val roots = projectsTree.rootProjects
    val parentNode = roots[0]
    val childNode = projectsTree.getModules(roots[0])[0]
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/value1")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/value1")))
    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>one</id>
                            <activation>
                              <activeByDefault>true</activeByDefault>
                            </activation>
                            <properties>
                              <prop>value2</prop>
                            </properties>
                          </profile>
                        </profiles>
                        """.trimIndent())
    waitForReadingCompletion()
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/value2")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/value2")))
    deleteSettingsXml()
    waitForReadingCompletion()
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/\${prop}")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/\${prop}")))
    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>one</id>
                            <activation>
                              <activeByDefault>true</activeByDefault>
                            </activation>
                            <properties>
                              <prop>value2</prop>
                            </properties>
                          </profile>
                        </profiles>
                        """.trimIndent())
    waitForReadingCompletion()
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/value2")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/value2")))
  }

  @Test
  fun testUpdatingProjectsOnSettingsXmlCreationAndDeletion() {
    deleteSettingsXml()
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())
    importProject()
    assertUnorderedElementsAreEqual(projectsTree.getAvailableProfiles())
    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>one</id>
                          </profile>
                        </profiles>
                        """.trimIndent())
    waitForReadingCompletion()
    assertUnorderedElementsAreEqual(projectsTree.getAvailableProfiles(), "one")
    deleteSettingsXml()
    waitForReadingCompletion()
    assertUnorderedElementsAreEqual(projectsTree.getAvailableProfiles())
  }
}