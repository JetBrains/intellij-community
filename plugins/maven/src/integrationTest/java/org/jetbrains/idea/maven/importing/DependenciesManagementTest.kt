/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModuleLibDeps
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.importProjectsWithErrors
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.maven.testFramework.fixtures.updateSettingsXml
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.executeGoal
import org.jetbrains.idea.maven.fixtures.hasMavenInstallation
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class DependenciesManagementTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun testImportingDependencies() = runBlocking {
    if (!maven.hasMavenInstallation()) return@runBlocking

    maven.repositoryPath = maven.dir.resolve("repo")
    maven.updateSettingsXml("""
                      <localRepository>
                      ${maven.repositoryPath}</localRepository>
                      """.trimIndent())

    maven.createModulePom("__temp",
                    """
                      <groupId>test</groupId>
                      <artifactId>bom</artifactId>
                      <packaging>pom</packaging>
                      <version>1</version>
                      <dependencyManagement>
                        <dependencies>
                          <dependency>
                            <groupId>junit</groupId>
                            <artifactId>junit</artifactId>
                            <version>4.0</version>
                          </dependency>
                        </dependencies>
                      </dependencyManagement>
                      """.trimIndent())

    maven.executeGoal("__temp", "install")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencyManagement>
                      <dependencies>
                        <dependency>
                          <groupId>test</groupId>
                          <artifactId>bom</artifactId>
                          <version>1</version>
                          <type>pom</type>
                          <scope>import</scope>
                        </dependency>
                      </dependencies>
                    </dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertModuleLibDeps("project", "Maven: junit:junit:4.0")
  }

  @Test
  fun testImportingNotInstalledDependencies() = runBlocking {
    maven.repositoryPath = maven.dir.resolve("repo")
    maven.updateSettingsXml("""
  <localRepository>
  ${maven.repositoryPath}</localRepository>
  """.trimIndent())

    val bom = maven.createModulePom("bom",
                              """
                                        <groupId>test</groupId>
                                        <artifactId>bom</artifactId>
                                        <packaging>pom</packaging>
                                        <version>1</version>
                                        <dependencyManagement>
                                          <dependencies>
                                            <dependency>
                                              <groupId>junit</groupId>
                                              <artifactId>junit</artifactId>
                                              <version>4.0</version>
                                            </dependency>
                                          </dependencies>
                                        </dependencyManagement>
                                        """.trimIndent())

    val project = maven.createModulePom("project",
                                  """
                                            <groupId>test</groupId>
                                            <artifactId>project</artifactId>
                                            <version>1</version>
                                            <dependencyManagement>
                                              <dependencies>
                                                <dependency>
                                                  <groupId>test</groupId>
                                                  <artifactId>bom</artifactId>
                                                  <version>1</version>
                                                  <type>pom</type>
                                                  <scope>import</scope>
                                                </dependency>
                                              </dependencies>
                                            </dependencyManagement>
                                            <dependencies>
                                              <dependency>
                                                <groupId>junit</groupId>
                                                <artifactId>junit</artifactId>
                                              </dependency>
                                            </dependencies>
                                            """.trimIndent())
    maven.importProjectsWithErrors(bom, project)
    maven.assertModules("bom", "project")

    maven.updateAllProjects()

    maven.assertModuleLibDeps("project", "Maven: junit:junit:4.0")
  }
}