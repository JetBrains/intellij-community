// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.MavenVersionArguments
import org.jetbrains.idea.maven.fixtures.assertUnorderedElementsAreEqual
import org.jetbrains.idea.maven.fixtures.assertUnorderedPathsAreEqual
import org.jetbrains.idea.maven.fixtures.createModulePom
import org.jetbrains.idea.maven.fixtures.createProjectPom
import org.jetbrains.idea.maven.fixtures.createSettingsXml
import org.jetbrains.idea.maven.fixtures.importProjectAsync
import org.jetbrains.idea.maven.fixtures.initProjectsManager
import org.jetbrains.idea.maven.fixtures.mavenImportingFixture
import org.jetbrains.idea.maven.fixtures.projectPath
import org.jetbrains.idea.maven.fixtures.projectsTree
import org.jetbrains.idea.maven.fixtures.updateSettingsXml
import org.jetbrains.idea.maven.fixtures.waitForImportWithinTimeout
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.nio.file.Paths

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenProjectsManagerSettingsXmlTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )

  // Forwarders to keep the legacy bodies one-to-one (these were inherited members of the base test class).
  private val projectsTree get() = maven.projectsTree
  private val projectPath get() = maven.projectPath

  @BeforeEach
  fun setUp() {
    maven.initProjectsManager(true)
  }

  @Test
  fun testUpdatingProjectsOnSettingsXmlChange() = runBlocking {
    maven.createProjectPom("""
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
    maven.createModulePom("m",
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
    maven.updateSettingsXml("""
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
    maven.importProjectAsync()
    val roots = projectsTree.rootProjects
    val parentNode = roots[0]
    val childNode = projectsTree.getModules(roots[0])[0]
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/value1")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/value1")))
    maven.waitForImportWithinTimeout {
      maven.updateSettingsXml("""
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
    }
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/value2")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/value2")))

    deleteSettingsXmlAndWaitForImport()
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/\${prop}")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/\${prop}")))
    maven.waitForImportWithinTimeout {
      maven.updateSettingsXml("""
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
    }
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/value2")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/value2")))
  }

  @Test
  fun testUpdatingProjectsOnSettingsXmlCreationAndDeletion() = runBlocking {
    deleteSettingsXmlAndWaitForImport()
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())
    maven.importProjectAsync()
    assertUnorderedElementsAreEqual(projectsTree.availableProfiles)
    maven.waitForImportWithinTimeout {
      maven.createSettingsXml("""
                        <profiles>
                          <profile>
                            <id>one</id>
                          </profile>
                        </profiles>
                        """.trimIndent())
    }

    assertUnorderedElementsAreEqual(projectsTree.availableProfiles, "one")
    deleteSettingsXmlAndWaitForImport()
    assertUnorderedElementsAreEqual(projectsTree.availableProfiles)
  }

  private suspend fun deleteSettingsXmlAndWaitForImport() {
    val f = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Paths.get(maven.dir.toString(), "settings.xml"))!!
    maven.waitForImportWithinTimeout {
      edtWriteAction {
        f.delete(this)
      }
    }
  }
}