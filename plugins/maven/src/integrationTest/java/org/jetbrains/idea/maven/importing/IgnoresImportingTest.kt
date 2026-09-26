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
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.maven.testFramework.fixtures.initProjectsManager
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.setIgnoredFilesPathForNextImport
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class IgnoresImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @BeforeEach
  fun setUp() {
    maven.initProjectsManager(false)
  }

  @Test
  fun testDoNotImportIgnoredProjects() = runBlocking {
    val p1 = maven.createModulePom("project1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>project1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())

    val p2 = maven.createModulePom("project2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>project2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())

    maven.setIgnoredFilesPathForNextImport(listOf(p1.getPath()))
    maven.importProjectsAsync(p1, p2)
    maven.assertModules("project2")
  }

  @Test
  fun testAddingAndRemovingModulesWhenIgnoresChange() = runBlocking {
    val p1 = maven.createModulePom("project1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>project1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())

    val p2 = maven.createModulePom("project2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>project2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    maven.importProjectsAsync(p1, p2)
    maven.assertModules("project1", "project2")

    maven.setIgnoredFilesPathForNextImport(listOf(p1.getPath()))
    doReadAndImport()
    maven.assertModules("project2")

    maven.setIgnoredFilesPathForNextImport(listOf(p2.getPath()))
    doReadAndImport()
    maven.assertModules("project1")

    maven.setIgnoredFilesPathForNextImport(emptyList<String>())
    doReadAndImport()
    maven.assertModules("project1", "project2")
  }

  private suspend fun doReadAndImport() {
    maven.updateAllProjects()
  }
}
